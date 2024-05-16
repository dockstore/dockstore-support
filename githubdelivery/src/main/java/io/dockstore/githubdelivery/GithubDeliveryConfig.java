package io.dockstore.githubdelivery;

import io.dockstore.utils.ConfigFileUtils;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;

public class GithubDeliveryConfig {
    private DockstoreConfig dockstoreConfig;
    private S3Config s3Config;
    public GithubDeliveryConfig(INIConfiguration config) {
        SubnodeConfiguration dockstoreSection = ConfigFileUtils.getDockstoreSection(config);
        SubnodeConfiguration s3Section = config.getSection("s3");

        this.dockstoreConfig = new DockstoreConfig(dockstoreSection.getString("server-url", "http://localhost:8080"), dockstoreSection.getString("token"));
        this.s3Config = new S3Config(s3Section.getString("bucketName", "local-github-delivery-bucket"), s3Section.getString("endpointOverride"));
    }

    public DockstoreConfig getDockstoreConfig() {
        return dockstoreConfig;
    }

    public S3Config getS3Config() {
        return s3Config;
    }


    public record DockstoreConfig(String serverUrl, String token) {
    }

    public record S3Config(String bucket, String endpointOverride) {
    }

}


