package io.dockstore.githubdelivery;

import io.dockstore.common.S3ClientHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class GithubDeliveryS3Client {
    private static final Logger LOG = LoggerFactory.getLogger(GithubDeliveryS3Client.class);
    private final S3Client s3Client;
    private final String bucketName;

    public GithubDeliveryS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.getS3Client();
    }

    private GetObjectResponse getGitHubDeliveryEventByKey(String key) {
        GetObjectRequest objectRequest = GetObjectRequest
                .builder()
                .key(key)
                .bucket(bucketName)
                .build();

        return this.s3Client.getObject(objectRequest).response();
    }


}
