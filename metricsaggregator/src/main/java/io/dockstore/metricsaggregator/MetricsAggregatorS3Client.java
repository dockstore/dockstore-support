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
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.MetricsData;
import io.dockstore.webservice.core.metrics.MetricsDataS3Client;
import io.dockstore.webservice.helpers.S3ClientHelper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

public class MetricsAggregatorS3Client {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsAggregatorS3Client.class);
    private static final Gson GSON = new Gson();

    private String bucketName;

    private S3Client s3Client;
    private MetricsDataS3Client metricsDataS3Client;

    public MetricsAggregatorS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder().build();
        this.metricsDataS3Client = new MetricsDataS3Client(bucketName);
    }

    public MetricsAggregatorS3Client(String bucketName, String s3EndpointOverride) throws URISyntaxException {
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
            List<Execution> executions = metricsDataList.stream().map(metricsData -> {
                try {
                    String fileContent = metricsDataS3Client.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(),
                            platform, metricsData.fileName());
                    return List.of(GSON.fromJson(fileContent, Execution[].class));
                } catch (IOException e) {
                    LOG.error("Error aggregating metrics: Unable to get all executions", e);
                    throw new RuntimeException("Error aggregating metrics: Unable to get all executions");
                }
            }).flatMap(List::stream).toList();

            getAggregatedMetrics(executions).ifPresent(
                    metrics -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, toolId, versionName));
        }
    }

    /**
     * Returns a unique list of directories containing metrics files.
     * Directory example: "workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/master/TERRA/"
     *
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
}
