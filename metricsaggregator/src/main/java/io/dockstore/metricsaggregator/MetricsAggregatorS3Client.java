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

import io.dockstore.common.S3ClientHelper;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.AthenaTablePartition;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

public class MetricsAggregatorS3Client {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsAggregatorS3Client.class);

    private final String bucketName;

    private final S3Client s3Client;

    public MetricsAggregatorS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.getS3Client();
    }

    public MetricsAggregatorS3Client(String bucketName, String s3EndpointOverride) throws URISyntaxException {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.createS3Client(s3EndpointOverride);
    }

    public List<VersionS3DirectoryInfo> getVersionDirectoriesForTrsId(String trsId) {
        final String s3KeyPrefix = S3ClientHelper.convertToolIdToPartialKey(trsId) + "/";
        LOG.info("Getting directories for TRS ID {} with S3 key prefix {}", trsId, s3KeyPrefix);
        return getVersionDirectories(s3KeyPrefix);
    }

    public List<VersionS3DirectoryInfo> getVersionDirectoriesForTrsIdVersion(String trsId, String versionName) {
        final String s3KeyPrefix = S3ClientHelper.convertToolIdToPartialKey(trsId) + "/" + versionName + "/";
        LOG.info("Getting directories for TRS ID {} and version {} with S3 key prefix {}", trsId, versionName, s3KeyPrefix);
        return getVersionDirectories(s3KeyPrefix);
    }

    public List<VersionS3DirectoryInfo> getVersionDirectories() {
        LOG.info("Getting all directories");
        return getVersionDirectories("");
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
    public List<VersionS3DirectoryInfo> getVersionDirectories(String rootPrefix) {
        Queue<String> prefixesToProcess = new ArrayDeque<>(List.of(rootPrefix));
        List<VersionS3DirectoryInfo> s3DirectoryInfos = new ArrayList<>();
        while (!prefixesToProcess.isEmpty()) {
            String prefix = prefixesToProcess.remove();
            List<String> subdirectories = retrieveSubdirectories(prefix);
            boolean isVersionDirectory = !S3ClientHelper.getVersionName(prefix).isEmpty();
            if (isVersionDirectory) {
                VersionS3DirectoryInfo info = createVersionS3DirectoryInfo(prefix, subdirectories);
                s3DirectoryInfos.add(info);
            } else {
                prefixesToProcess.addAll(subdirectories);
            }
        }

        return s3DirectoryInfos;
    }

    private List<String> retrieveSubdirectories(String prefix) {
        List<String> subdirectories = new ArrayList<>();
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).delimiter("/").build();
        for (ListObjectsV2Response listObjectsV2Response: s3Client.listObjectsV2Paginator(request)) {
            subdirectories.addAll(listObjectsV2Response.commonPrefixes().stream().map(CommonPrefix::prefix).toList());
        }
        return subdirectories;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private VersionS3DirectoryInfo createVersionS3DirectoryInfo(String prefix, List<String> subdirectories) {
        String toolId = S3ClientHelper.getToolId(prefix);
        String versionId = S3ClientHelper.getVersionName(prefix);
        List<String> platforms = subdirectories.stream().map(subdirectory -> S3ClientHelper.getMetricsPlatform(subdirectory)).toList();
        // Athena partition values. We don't want to decode these otherwise they won't match the partition values in S3
        String entityPartition = S3ClientHelper.getElementFromKey(prefix, 0);
        String registryPartition = S3ClientHelper.getElementFromKey(prefix, 1);
        String orgPartition = S3ClientHelper.getElementFromKey(prefix, 2);
        String namePartition = S3ClientHelper.getElementFromKey(prefix, 3);
        String versionPartition = S3ClientHelper.getElementFromKey(prefix, 4);
        AthenaTablePartition athenaTablePartition = new AthenaTablePartition(Optional.of(entityPartition), Optional.of(registryPartition), Optional.of(orgPartition), Optional.of(namePartition), Optional.of(versionPartition));
        return new VersionS3DirectoryInfo(toolId, versionId, platforms, prefix, athenaTablePartition);
    }

    public record VersionS3DirectoryInfo(String toolId, String versionId, List<String> platforms, String versionS3KeyPrefix, AthenaTablePartition athenaTablePartition) {
        EntryS3DirectoryInfo toEntryS3DirectoryInfo() {
            return new EntryS3DirectoryInfo(toolId, platforms, Paths.get(versionS3KeyPrefix).getParent().toString(), athenaTablePartition.toEntryPartition());
        }
    }

    public record EntryS3DirectoryInfo(String toolId, List<String> platforms, String entryS3KeyPrefix, AthenaTablePartition athenaTablePartition) {
        EntryS3DirectoryInfo combine(EntryS3DirectoryInfo with) {
            // TODO add asserts to check that otherwise equal
            Set<String> combinedPlatforms = new HashSet<>();
            combinedPlatforms.addAll(platforms());
            combinedPlatforms.addAll(with.platforms());
            return new EntryS3DirectoryInfo(toolId, new ArrayList<>(combinedPlatforms), entryS3KeyPrefix, athenaTablePartition);
        }
    }
}
