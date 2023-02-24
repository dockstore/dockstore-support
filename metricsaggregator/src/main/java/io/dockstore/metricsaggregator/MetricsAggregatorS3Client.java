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
        List<String> metricsDirectories = getDirectories();

        if (metricsDirectories.isEmpty()) {
            System.out.println("No directories found to aggregate metrics");
            return;
        }

        // Each directory contains metrics for a specific tool version and platform
        for (String directory : metricsDirectories) {
            String toolId = S3ClientHelper.getToolId(directory); // Check if we should just give the full key
            String versionName = S3ClientHelper.getVersionName(directory);
            String platform = S3ClientHelper.getPlatform(directory);
            List<MetricsData> metricsDataList = metricsDataS3Client.getMetricsData(toolId, versionName, Partner.valueOf(platform));

            List<Execution> executions;
            try {
                executions = getExecutions(metricsDataList);
            } catch (IOException e) {
                LOG.error("Error aggregating metrics: Unable to get all executions from directory {}", directory, e);
                continue; // Continue aggregating metrics for other directories
            }

            getAggregatedMetrics(executions).ifPresent(metrics -> {
                extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, toolId, versionName);
                System.out.println(String.format("Aggregated metrics for tool ID %s, version %s, platform %s from S3 directory %s", toolId, versionName, platform, directory));
            });
        }
    }

    private List<Execution> getExecutions(List<MetricsData> metricsDataList) throws IOException {
        List<Execution> executionsFromAllSubmissions = new ArrayList<>();
        for (MetricsData metricsData : metricsDataList) {
            String fileContent = metricsDataS3Client.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(),
                    metricsData.platform(), metricsData.fileName());
            List<Execution> executionsFromOneSubmission = List.of(GSON.fromJson(fileContent, Execution[].class));
            executionsFromAllSubmissions.addAll(executionsFromOneSubmission);
        }
        return executionsFromAllSubmissions;
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
