package io.dockstore.metricsaggregator;

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
        SubnodeConfiguration dockstoreSection = config.getSection("dockstore");
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
