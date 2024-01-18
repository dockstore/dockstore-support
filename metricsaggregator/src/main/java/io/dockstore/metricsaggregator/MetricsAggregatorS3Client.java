/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.metricsaggregator;

import static io.dockstore.metricsaggregator.helper.AggregationHelper.getAggregatedMetrics;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.dockstore.common.Partner;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.common.metrics.MetricsData;
import io.dockstore.common.metrics.MetricsDataS3Client;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.model.AggregatedExecution;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import io.dockstore.openapi.client.model.ValidationExecution;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

public class MetricsAggregatorS3Client {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsAggregatorS3Client.class);
    private static final Gson GSON = new Gson();
    private final AtomicInteger numberOfDirectoriesProcessed = new AtomicInteger(0);
    private final AtomicInteger numberOfMetricsSubmitted = new AtomicInteger(0);
    private final AtomicInteger numberOfMetricsSkipped = new AtomicInteger(0);

    private final String bucketName;

    private final S3Client s3Client;
    private final MetricsDataS3Client metricsDataS3Client;

    public MetricsAggregatorS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.createS3Client();
        this.metricsDataS3Client = new MetricsDataS3Client(bucketName, this.s3Client);
    }

    public MetricsAggregatorS3Client(String bucketName, String s3EndpointOverride) throws URISyntaxException {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.createS3Client(s3EndpointOverride);
        this.metricsDataS3Client = new MetricsDataS3Client(bucketName, s3EndpointOverride);
    }

    public void aggregateMetrics(ExtendedGa4GhApi extendedGa4GhApi) {
        LOG.info("Getting directories to process...");
        List<S3DirectoryInfo> metricsDirectories = getDirectories();

        if (metricsDirectories.isEmpty()) {
            LOG.info("No directories found to aggregate metrics");
            return;
        }

        LOG.info("Aggregating metrics for {} directories", metricsDirectories.size());
        metricsDirectories.stream()
                .parallel()
                .forEach(directoryInfo -> aggregateMetricsForDirectory(directoryInfo, extendedGa4GhApi));
        LOG.info("Completed aggregating metrics. Processed {} directories, submitted {} platform metrics, and skipped {} platform metrics", numberOfDirectoriesProcessed, numberOfMetricsSubmitted, numberOfMetricsSkipped);
    }

    private void aggregateMetricsForDirectory(S3DirectoryInfo directoryInfo, ExtendedGa4GhApi extendedGa4GhApi) {
        LOG.info("Processing directory {}", directoryInfo);
        String toolId = directoryInfo.toolId();
        String versionName = directoryInfo.versionId();
        List<String> platforms = directoryInfo.platforms();
        String platformsString = String.join(", ", platforms);
        String versionS3KeyPrefix = directoryInfo.versionS3KeyPrefix();

        // Collect metrics for each platform, so we can calculate metrics across all platforms
        List<Metrics> allMetrics = new ArrayList<>();
        for (String platform : platforms) {
            ExecutionsRequestBody allSubmissions;
            try {
                allSubmissions = getExecutions(toolId, versionName, platform);
            } catch (Exception e) {
                LOG.error("Error aggregating metrics: Could not get all executions from directory {}", versionS3KeyPrefix, e);
                numberOfMetricsSkipped.incrementAndGet();
                continue; // Continue aggregating metrics for other directories
            }

            try {
                getAggregatedMetrics(allSubmissions).ifPresent(metrics -> {
                    extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, toolId, versionName);
                    LOG.info("Aggregated metrics for tool ID {}, version {}, platform {} from directory {}", toolId, versionName, platform, versionS3KeyPrefix);
                    allMetrics.add(metrics);
                    numberOfMetricsSubmitted.incrementAndGet();
                });
            } catch (Exception e) {
                LOG.error("Error aggregating metrics: Could not put all executions from directory {}", versionS3KeyPrefix, e);
                numberOfMetricsSkipped.incrementAndGet();
                // Continue aggregating metrics for other platforms
            }
        }

        if (!allMetrics.isEmpty()) {
            // Calculate metrics across all platforms by aggregating the aggregated metrics from each platform
            try {
                getAggregatedMetrics(allMetrics).ifPresent(metrics -> {
                    extendedGa4GhApi.aggregatedMetricsPut(metrics, Partner.ALL.name(), toolId, versionName);
                    LOG.info("Aggregated metrics across all platforms ({}) for tool ID {}, version {} from directory {}",
                            platformsString, toolId, versionName, versionS3KeyPrefix);
                    allMetrics.add(metrics);
                    numberOfMetricsSubmitted.incrementAndGet();
                });
            } catch (Exception e) {
                LOG.error("Error aggregating metrics across all platforms ({}) for tool ID {}, version {} from directory {}", platformsString, toolId, versionName, versionS3KeyPrefix, e);
                numberOfMetricsSkipped.incrementAndGet();
                // Continue aggregating metrics for other directories
            }
        } else {
            numberOfMetricsSkipped.incrementAndGet();
        }
        numberOfDirectoriesProcessed.incrementAndGet();
        LOG.info("Processed {} directories", numberOfDirectoriesProcessed);
    }

    /**
     * Get all executions from all submissions for the specific tool, version, and platform.
     * If there are executions with the same execution ID, the function takes the newest execution.
     * @param toolId
     * @param versionName
     * @param platform
     * @return
     */
    private ExecutionsRequestBody getExecutions(String toolId, String versionName, String platform) throws IOException, JsonSyntaxException {
        // getMetricsData uses the S3 ListObjectsV2Request which returns objects in alphabetical order.
        // Since the file names are the time of submission in milliseconds, metricsDataList is sorted from oldest file name to newest file name
        List<MetricsData> metricsDataList = metricsDataS3Client.getMetricsData(toolId, versionName, Partner.valueOf(platform));
        Map<String, RunExecution> executionIdToWorkflowExecutionMap = new HashMap<>();
        Map<String, TaskExecutions> executionIdToTaskExecutionsMap = new HashMap<>();
        Map<String, ValidationExecution> executionIdToValidationExecutionMap = new HashMap<>();
        Map<String, AggregatedExecution> executionIdToAggregatedExecutionMap = new HashMap<>();

        for (MetricsData metricsData : metricsDataList) {
            String fileContent = metricsDataS3Client.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(),
                    metricsData.platform(), metricsData.fileName());

            ExecutionsRequestBody executionsFromOneSubmission;
            try {
                executionsFromOneSubmission = GSON.fromJson(fileContent, ExecutionsRequestBody.class);
            } catch (JsonSyntaxException e) {
                LOG.error("Could not read execution(s) from S3 key {}, ignoring file", metricsData.s3Key());
                continue;
            }

            // For each execution, put it in a map so that there are no executions with duplicate execution IDs.
            // The latest execution put in the map is the newest one based on the principal that S3 lists objects in alphabetical order,
            // which is returned in an ordered list via getMetricsData.
            executionsFromOneSubmission.getRunExecutions().forEach(workflowExecution -> {
                final String executionId = workflowExecution.getExecutionId();
                executionIdToWorkflowExecutionMap.put(executionId, workflowExecution);
                executionIdToValidationExecutionMap.remove(executionId);
                executionIdToTaskExecutionsMap.remove(executionId);
                executionIdToAggregatedExecutionMap.remove(executionId);
            });
            executionsFromOneSubmission.getTaskExecutions().forEach(taskExecutions -> {
                final String executionId = taskExecutions.getExecutionId();
                executionIdToTaskExecutionsMap.put(executionId, taskExecutions);
                executionIdToWorkflowExecutionMap.remove(executionId);
                executionIdToValidationExecutionMap.remove(executionId);
                executionIdToAggregatedExecutionMap.remove(executionId);
            });
            executionsFromOneSubmission.getValidationExecutions().forEach(validationExecution -> {
                final String executionId = validationExecution.getExecutionId();
                executionIdToValidationExecutionMap.put(executionId, validationExecution);
                executionIdToWorkflowExecutionMap.remove(executionId);
                executionIdToTaskExecutionsMap.remove(executionId);
                executionIdToAggregatedExecutionMap.remove(executionId);
            });
            executionsFromOneSubmission.getAggregatedExecutions().forEach(aggregatedExecution -> {
                final String executionId = aggregatedExecution.getExecutionId();
                executionIdToAggregatedExecutionMap.put(executionId, aggregatedExecution);
                executionIdToWorkflowExecutionMap.remove(executionId);
                executionIdToTaskExecutionsMap.remove(executionId);
                executionIdToValidationExecutionMap.remove(executionId);
            });
        }

        return new ExecutionsRequestBody()
                .runExecutions(executionIdToWorkflowExecutionMap.values().stream().toList())
                .taskExecutions(executionIdToTaskExecutionsMap.values().stream().toList())
                .validationExecutions(executionIdToValidationExecutionMap.values().stream().toList())
                .aggregatedExecutions(executionIdToAggregatedExecutionMap.values().stream().toList());
    }

    /**
     * If the execution ID is null, generate a random one for the purposes of aggregation.
     * Executions that were submitted to S3 prior to the existence of execution IDs don't have an execution ID,
     * thus for the purposes of aggregation, generate one.
     * @param executionId
     * @return
     */
    private String generateExecutionIdIfNull(String executionId) {
        if (executionId == null) {
            return UUID.randomUUID().toString();
        }
        return executionId;
    }

    /**
     * Returns a unique list of directories containing metrics files.
     * For example, suppose the local-dockstore-metrics-data bucket looks like the following.
     * <p>
     * local-dockstore-metrics-data
     * ├── tool
     * │   └── quay.io
     * │       └── dockstoretestuser2
     * │           └── dockstore-cgpmap
     * │               └── symbolic.v1
     * │                   └── TERRA
     * │                       └── 1673972062578.json
     * │                           └── OBJECT METADATA
     * │                               └── owner: 1
     * │                               └── description:
     * └── workflow
     *     └── github.com
     *         └── DockstoreTestUser2
     *             └── dockstore_workflow_cnv%2Fmy-workflow
     *                 └── master
     *                     ├── TERRA
     *                     │   └── 1673972062578.json
     *                     │       └── OBJECT METADATA
     *                     │           └── owner: 1
     *                     │           └── description: A single execution
     *                     └── DNA_STACK
     *                         └── 1673972062578.json
     *                             └── OBJECT METADATA
     *                                 └── owner: 1
     *                                 └── description: A single execution
     * <p>
     * This function returns the following directories:
     * <ul>
     *     <li>tool/quay.io/dockstoretestuser2/dockstore-cgpmap/symbolic.v1/TERRA/</li>
     *     <li>workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv%2Fmy-workflow/master/TERRA/</li>
     *     <li>workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv%2Fmy-workflow/master/DNA_STACK/</li>
     * </ul>
     *
     * @return
     */
    public List<S3DirectoryInfo> getDirectories() {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).delimiter("/").build();
        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(request);
        Queue<CommonPrefix> commonPrefixesToProcess = new ArrayDeque<>(listObjectsV2Response.commonPrefixes());
        List<S3DirectoryInfo> s3DirectoryInfos = new ArrayList<>();

        while (!commonPrefixesToProcess.isEmpty()) {
            String prefix = commonPrefixesToProcess.remove().prefix();
            request = ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).delimiter("/").build();
            listObjectsV2Response = s3Client.listObjectsV2(request);

            boolean isVersionDirectory = !S3ClientHelper.getVersionName(prefix).isEmpty();
            if (isVersionDirectory) {
                String toolId = S3ClientHelper.getToolId(prefix);
                String versionId = S3ClientHelper.getVersionName(prefix);
                List<String> platforms = listObjectsV2Response.commonPrefixes().stream()
                        .map(commonPrefix -> S3ClientHelper.getMetricsPlatform(commonPrefix.prefix()))
                        .toList();
                s3DirectoryInfos.add(new S3DirectoryInfo(toolId, versionId, platforms, prefix));
            } else {
                commonPrefixesToProcess.addAll(listObjectsV2Response.commonPrefixes());
            }
        }

        return s3DirectoryInfos;
    }

    public record S3DirectoryInfo(String toolId, String versionId, List<String> platforms, String versionS3KeyPrefix) {
    }
}
