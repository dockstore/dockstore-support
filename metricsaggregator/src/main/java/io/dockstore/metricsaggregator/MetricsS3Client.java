package io.dockstore.metricsaggregator;

import static java.util.stream.Collectors.groupingBy;

import com.google.gson.Gson;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import io.dockstore.webservice.core.metrics.MetricsData;
import io.dockstore.webservice.core.metrics.MetricsDataS3Client;
import io.dockstore.webservice.helpers.S3ClientHelper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

public class MetricsS3Client {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsS3Client.class);
    private static final Gson GSON = new Gson();

    private String bucketName;

    private S3Client s3Client;
    private MetricsDataS3Client metricsDataS3Client;

    public MetricsS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder().build();
        this.metricsDataS3Client = new MetricsDataS3Client(bucketName);
    }

    public MetricsS3Client(String bucketName, String s3EndpointOverride) throws URISyntaxException {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.createS3Client(s3EndpointOverride);
        this.metricsDataS3Client = new MetricsDataS3Client(bucketName, s3EndpointOverride);
    }

    public void aggregateMetrics(ExtendedGa4GhApi extendedGa4GhApi) {
        List<String> metricsDirectories = getDirectories();
        // Each directory contains metrics for a specific tool version and platform
        for (String directory : metricsDirectories) {
            String toolId = S3ClientHelper.getToolId(directory); // Check if we should just give the full key
            String versionName = S3ClientHelper.getVersionName(directory);
            String platform = S3ClientHelper.getPlatform(directory);
            List<MetricsData> metricsDataList = metricsDataS3Client.getMetricsData(toolId, versionName, Partner.valueOf(platform));
            List<Execution> executions = metricsDataList.stream()
                .map(metricsData -> {
                    try {
                        String fileContent = metricsDataS3Client.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), platform, metricsData.fileName());
                        return List.of(GSON.fromJson(fileContent, Execution[].class));
                    } catch (IOException e) {
                        throw new RuntimeException("Error aggregating metrics: Unable to get all executions");
                    }
                })
                .flatMap(List::stream)
                .toList();

            getAggregatedMetrics(executions).ifPresent(
                    metrics -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, toolId, versionName));
        }
    }

    private Optional<Metrics> getAggregatedMetrics(List<Execution> executions) {
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = getAggregatedExecutionStatus(executions);
        if (getAggregatedExecutionStatus(executions).isPresent()) {
            Metrics aggregatedMetrics = new Metrics();
            aggregatedMetrics.setExecutionStatusCount(aggregatedExecutionStatus.get());
            getAggregatedExecutionTime(executions).ifPresent(aggregatedMetrics::setExecutionTime);
            getAggregatedCpu(executions).ifPresent(aggregatedMetrics::setCpu);
            getAggregatedMemory(executions).ifPresent(aggregatedMetrics::setMemory);
            return Optional.of(aggregatedMetrics);
        }
        return Optional.empty();
    }

    /**
     * Returns a list of directories containing metrics files.
     * Directory example: "workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/master/TERRA/"
     * @return
     */
    public List<String> getDirectories() {
        List<String> commonPrefixes = new ArrayList<>();
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).delimiter("/").build();
        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(request);
        Queue<CommonPrefix> commonPrefixesToProcess = new ArrayDeque<>(listObjectsV2Response.commonPrefixes());

        while (!commonPrefixesToProcess.isEmpty()) {
            String prefix = commonPrefixesToProcess.remove().prefix();
            request = ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).delimiter("/").build();
            listObjectsV2Response = s3Client.listObjectsV2(request);

            if (listObjectsV2Response.commonPrefixes().isEmpty()) {
                // Reached the end of the key, add the previous prefix
                commonPrefixes.add(prefix);
            } else {
                commonPrefixesToProcess.addAll(listObjectsV2Response.commonPrefixes());
            }
        }

        return commonPrefixes;
    }

    Optional<ExecutionStatusMetric> getAggregatedExecutionStatus(List<Execution> executions) {
        Map<String, Integer> statusCount = executions.stream()
                .map(execution -> execution.getExecutionStatus().toString())
                .collect(groupingBy(Function.identity(), Collectors.reducing(0, e -> 1, Integer::sum)));
        // This shouldn't happen because all executions should have an execution status, but check anyway
        if (statusCount.values().stream().allMatch(count -> count == 0)) {
            return Optional.empty();
        }
        return Optional.of(new ExecutionStatusMetric().count(statusCount));
    }

    Optional<ExecutionTimeMetric> getAggregatedExecutionTime(List<Execution> executions) {
        List<String> executionTimes = executions.stream()
                .map(Execution::getExecutionTime)
                .filter(Objects::nonNull)
                .toList();
        List<Double> executionTimesInSeconds = executionTimes.stream()
                .map(executionTime -> {
                    // Convert executionTime in ISO 8601 duration format to seconds
                    try {
                        return Long.valueOf(Duration.parse(executionTime).toSeconds()).doubleValue();
                    } catch (DateTimeParseException e) {
                        LOG.error("Could not parse Duration from {}", executionTime);
                        return null;
                    }
                })
                .toList();

        if (!executionTimesInSeconds.isEmpty()) {
            Statistics statistics = new Statistics(executionTimesInSeconds);
            return Optional.of(new ExecutionTimeMetric()
                    .minimum(statistics.min())
                    .maximum(statistics.max())
                    .average(statistics.average())
                    .numberOfDataPointsForAverage(statistics.numberOfDataPoints())
            );
        }
        return Optional.empty();
    }

    Optional<CpuMetric> getAggregatedCpu(List<Execution> executions) {
        List<Double> cpuRequirements = executions.stream().map(Execution::getCpuRequirements).filter(Objects::nonNull).map(cpu -> cpu.doubleValue()).toList();
        if (!cpuRequirements.isEmpty()) {
            Statistics statistics = new Statistics(cpuRequirements);
            return Optional.of(new CpuMetric()
                    .minimum(statistics.min())
                    .maximum(statistics.max())
                    .average(statistics.average())
                    .numberOfDataPointsForAverage(statistics.numberOfDataPoints()));
        }
        return Optional.empty();
    }

    Optional<MemoryMetric> getAggregatedMemory(List<Execution> executions) {
        List<String> memoryRequirements = executions.stream().map(Execution::getMemoryRequirements).filter(Objects::nonNull).toList();
        if (!memoryRequirements.isEmpty()) {
            List<Double> memoryDoubles = memoryRequirements.stream()
                    .map(memoryString -> memoryString.split(" "))
                    // Only aggregate memory specified in the following format: Numerical value, space, "GB". Ex: "2 GB"
                    .filter(splitMemoryString -> splitMemoryString.length == 2 && MemoryStatisticMetric.UNIT.equals(splitMemoryString[1]))
                    .map(splitMemoryString -> {
                        try {
                            return Double.parseDouble(splitMemoryString[0]);
                        } catch (NumberFormatException e) {
                            LOG.error("Could not parse integer from {}", splitMemoryString[0]);
                            return null;
                        }
                    })
                    .toList();

            if (!memoryDoubles.isEmpty()) {
                Statistics statistics = new Statistics(memoryDoubles);
                return Optional.of(new MemoryMetric()
                        .minimum(statistics.min())
                        .maximum(statistics.max())
                        .average(statistics.average())
                        .numberOfDataPointsForAverage(statistics.numberOfDataPoints()));
            }
        }
        return Optional.empty();
    }
}
