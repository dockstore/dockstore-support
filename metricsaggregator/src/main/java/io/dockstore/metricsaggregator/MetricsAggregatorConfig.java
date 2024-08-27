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

import io.dockstore.utils.ConfigFileUtils;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;

public class MetricsAggregatorConfig {
    private DockstoreConfig dockstoreConfig;
    private S3Config s3Config;
    private AthenaConfig athenaConfig;

    public MetricsAggregatorConfig(INIConfiguration config) {
        SubnodeConfiguration dockstoreSection = ConfigFileUtils.getDockstoreSection(config);
        SubnodeConfiguration s3Section = config.getSection("s3");
        SubnodeConfiguration athenaSection = config.getSection("athena");

        this.dockstoreConfig = new DockstoreConfig(dockstoreSection.getString("server-url", "http://localhost:8080"), dockstoreSection.getString("token"));
        this.s3Config = new S3Config(s3Section.getString("bucketName", "local-dockstore-metrics-data"), s3Section.getString("endpointOverride"));
        this.athenaConfig = new AthenaConfig(athenaSection.getString("workgroup"));
    }

    public DockstoreConfig getDockstoreConfig() {
        return dockstoreConfig;
    }

    public S3Config getS3Config() {
        return s3Config;
    }

    public AthenaConfig getAthenaConfig() {
        return athenaConfig;
    }

    public record DockstoreConfig(String serverUrl, String token) {
    }

    public record S3Config(String bucket, String endpointOverride) {
    }

    public record AthenaConfig(String workgroup) {
    }
}
