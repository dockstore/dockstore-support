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

    private String dockstoreServerUrl;
    private String dockstoreToken;
    private String s3Bucket;
    private String s3EndpointOverride;

    public MetricsAggregatorConfig() {
    }

    public MetricsAggregatorConfig(INIConfiguration config) {
        SubnodeConfiguration dockstoreSection = ConfigFileUtils.getDockstoreSection(config);
        SubnodeConfiguration s3Section = config.getSection("s3");
        this.dockstoreServerUrl = dockstoreSection.getString("server-url", "http://localhost:8080");
        this.dockstoreToken = dockstoreSection.getString("token");
        this.s3Bucket = s3Section.getString("bucketName", "local-dockstore-metrics-data");
        this.s3EndpointOverride = s3Section.getString("endpointOverride");
    }

    public String getDockstoreServerUrl() {
        return dockstoreServerUrl;
    }

    public void setDockstoreServerUrl(String dockstoreServerUrl) {
        this.dockstoreServerUrl = dockstoreServerUrl;
    }

    public String getDockstoreToken() {
        return dockstoreToken;
    }

    public void setDockstoreToken(String dockstoreToken) {
        this.dockstoreToken = dockstoreToken;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    public String getS3EndpointOverride() {
        return s3EndpointOverride;
    }

    public void setS3EndpointOverride(String s3Endpoint) {
        this.s3EndpointOverride = s3Endpoint;
    }
}
