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
import io.dockstore.common.metrics.ExecutionsRequestBody;
import io.dockstore.common.metrics.MetricsData;
import io.dockstore.common.metrics.MetricsDataS3Client;
import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.common.metrics.ValidationExecution;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.AthenaTablePartition;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.model.Metrics;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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
    private final AtomicInteger numberOfVersionsSubmitted = new AtomicInteger(0);
    private final AtomicInteger numberOfVersionsSkipped = new AtomicInteger(0);

    private final String bucketName;

    private final S3Client s3Client;
    private final MetricsDataS3Client metricsDataS3Client;

    public MetricsAggregatorS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.getS3Client();
        this.metricsDataS3Client = new MetricsDataS3Client(bucketName, this.s3Client);
    }

    public MetricsAggregatorS3Client(String bucketName, String s3EndpointOverride) throws URISyntaxException {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.createS3Client(s3EndpointOverride);
        this.metricsDataS3Client = new MetricsDataS3Client(bucketName, s3EndpointOverride);
    }

    public void aggregateMetrics(List<S3DirectoryInfo> s3DirectoriesToAggregate, ExtendedGa4GhApi extendedGa4GhApi, boolean skipDockstore) {
        s3DirectoriesToAggregate.stream().parallel().forEach(directoryInfo -> aggregateMetricsForDirectory(directoryInfo, extendedGa4GhApi, skipDockstore));
        LOG.info("Completed aggregating metrics. Processed {} directories, submitted {} platform metrics, and skipped {} platform metrics", numberOfDirectoriesProcessed,
                numberOfVersionsSubmitted,
                numberOfVersionsSkipped);
    }

    private void aggregateMetricsForDirectory(S3DirectoryInfo directoryInfo, ExtendedGa4GhApi extendedGa4GhApi, boolean skipDockstore) {
        LOG.info("Processing directory {}", directoryInfo);
        String toolId = directoryInfo.toolId();
        String versionName = directoryInfo.versionId();
        List<String> platforms = directoryInfo.platforms();
        String versionS3KeyPrefix = directoryInfo.versionS3KeyPrefix();

        // Collect metrics for each platform, so we can calculate metrics across all platforms
        Map<String, Metrics> platformToMetrics = new HashMap<>();
        for (String platform : platforms) {
            ExecutionsRequestBody allSubmissions;
            try {
                allSubmissions = getExecutions(toolId, versionName, platform);
            } catch (Exception e) {
                LOG.error("Error aggregating metrics: Could not get all executions from directory {}", versionS3KeyPrefix, e);
                numberOfVersionsSkipped.incrementAndGet();
                continue; // Continue aggregating metrics for other directories
            }

            try {
                Optional<Metrics> aggregatedPlatformMetric = getAggregatedMetrics(allSubmissions);
                if (aggregatedPlatformMetric.isPresent()) {
                    LOG.info("Aggregated metrics for tool ID {}, version {}, platform {} from directory {}", toolId, versionName, platform,
                            versionS3KeyPrefix);
                    platformToMetrics.put(platform, aggregatedPlatformMetric.get());
                } else {
                    LOG.error("Error aggregating metrics for tool ID {}, version {}, platform {} from directory {}", toolId, versionName, platform, versionS3KeyPrefix);
                }
            } catch (Exception e) {
                LOG.error("Error aggregating metrics: Could not put all executions from directory {}", versionS3KeyPrefix, e);
                numberOfVersionsSkipped.incrementAndGet();
                // Continue aggregating metrics for other platforms
            }
        }

        if (!platformToMetrics.isEmpty()) {
            // Calculate metrics across all platforms by aggregating the aggregated metrics from each platform
            try {
                getAggregatedMetrics(platformToMetrics.values().stream().toList()).ifPresent(metrics -> {
                    platformToMetrics.put(Partner.ALL.name(), metrics);
                    if (!skipDockstore) {
                        extendedGa4GhApi.aggregatedMetricsPut(platformToMetrics, toolId, versionName);
                    }
                    LOG.info("Aggregated metrics across all platforms ({}) for tool ID {}, version {} from directory {}",
                            platformToMetrics.keySet(), toolId, versionName, versionS3KeyPrefix);

                    numberOfVersionsSubmitted.incrementAndGet();
                });
            } catch (Exception e) {
                LOG.error("Error aggregating metrics across all platforms ({}) for tool ID {}, version {} from directory {}", platformToMetrics.keySet(), toolId, versionName, versionS3KeyPrefix, e);
                numberOfVersionsSkipped.incrementAndGet();
                // Continue aggregating metrics for other directories
            }
        } else {
            LOG.error("Error aggregating metrics for directory {}: no platform metrics aggregated", versionS3KeyPrefix);
            numberOfVersionsSkipped.incrementAndGet();
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

        for (MetricsData metricsData : metricsDataList) {
            String fileContent = metricsDataS3Client.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(),
                    metricsData.platform(), metricsData.fileName());

            ExecutionsRequestBody executionsFromOneSubmission;
            try {
                executionsFromOneSubmission = GSON.fromJson(fileContent, ExecutionsRequestBody.class);
            } catch (JsonSyntaxException e) {
                LOG.error("Could not read execution(s) from S3 key {}, ignoring file", metricsData.s3Key(), e);
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
            });
            executionsFromOneSubmission.getTaskExecutions().forEach(taskExecutions -> {
                final String executionId = taskExecutions.getExecutionId();
                executionIdToTaskExecutionsMap.put(executionId, taskExecutions);
                executionIdToWorkflowExecutionMap.remove(executionId);
                executionIdToValidationExecutionMap.remove(executionId);
            });
            executionsFromOneSubmission.getValidationExecutions().forEach(validationExecution -> {
                final String executionId = validationExecution.getExecutionId();
                executionIdToValidationExecutionMap.put(executionId, validationExecution);
                executionIdToWorkflowExecutionMap.remove(executionId);
                executionIdToTaskExecutionsMap.remove(executionId);
            });
        }

        ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody();
        executionsRequestBody.setRunExecutions(executionIdToWorkflowExecutionMap.values().stream().toList());
        executionsRequestBody.setTaskExecutions(executionIdToTaskExecutionsMap.values().stream().toList());
        executionsRequestBody.setValidationExecutions(executionIdToValidationExecutionMap.values().stream().toList());
        return executionsRequestBody;
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
    @SuppressWarnings("checkstyle:magicnumber")
    public List<S3DirectoryInfo> getDirectories(String s3KeyPrefix) {
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder().bucket(bucketName).delimiter("/");
        if (s3KeyPrefix != null) {
            requestBuilder.prefix(s3KeyPrefix);
        }
        ListObjectsV2Request request = requestBuilder.build();
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
                // Athena partition values. We don't want to decode these otherwise they won't match the partition values in S3
                String entityPartition = S3ClientHelper.getElementFromKey(prefix, 0);
                String registryPartition = S3ClientHelper.getElementFromKey(prefix, 1);
                String orgPartition = S3ClientHelper.getElementFromKey(prefix, 2);
                String namePartition = S3ClientHelper.getElementFromKey(prefix, 3);
                String versionPartition = S3ClientHelper.getElementFromKey(prefix, 4);
                AthenaTablePartition athenaTablePartition = new AthenaTablePartition(entityPartition, registryPartition, orgPartition, namePartition, versionPartition);
                s3DirectoryInfos.add(new S3DirectoryInfo(toolId, versionId, platforms, prefix, athenaTablePartition));
            } else {
                commonPrefixesToProcess.addAll(listObjectsV2Response.commonPrefixes());
            }
        }

        return s3DirectoryInfos;
    }

    public List<S3DirectoryInfo> getDirectories() {
        LOG.info("Getting all directories");
        return getDirectories(null);
    }

    public List<S3DirectoryInfo> getDirectoriesForTrsId(String trsId) {
        final String s3KeyPrefix = S3ClientHelper.convertToolIdToPartialKey(trsId);
        LOG.info("Getting directories for TRS ID {} with S3 key prefix {}", trsId, s3KeyPrefix);
        return getDirectories(s3KeyPrefix);
    }

    public List<S3DirectoryInfo> getDirectoriesForTrsIdVersion(String trsId, String versionName) {
        final String s3KeyPrefix = S3ClientHelper.convertToolIdToPartialKey(trsId) + "/" + versionName;
        LOG.info("Getting directories for TRS ID {} and version {} with S3 key prefix {}", trsId, versionName, s3KeyPrefix);
        return getDirectories(s3KeyPrefix);
    }

    public record S3DirectoryInfo(String toolId, String versionId, List<String> platforms, String versionS3KeyPrefix, AthenaTablePartition athenaTablePartition) {
    }
}
