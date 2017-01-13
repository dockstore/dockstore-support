package io.dockstore.tooltester.client.cli;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;

/**
 * Created by kcao on 12/01/17.
 */
class S3Communicator {
    private static final Config S3_CONFIG = ConfigFactory.load();
    private static final String ACCESS_KEY = S3_CONFIG.getString("AWS_ACCESS_KEY");
    private static final String SECRET_KEY = S3_CONFIG.getString("AWS_SECRET_KEY");
    private static final String BUCKET = "testBucket";

    static void uploadFile() {
        AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());
        try {
            System.out.println("Uploading a new object to S3 from a file\n");
            File file = new File("");
            s3client.putObject(new PutObjectRequest(
                    BUCKET, ACCESS_KEY, file));
        } catch (AmazonServiceException e) {
            ErrorExit.errorMessage(e.getMessage(), 1);
        } catch (AmazonClientException e) {
            ErrorExit.errorMessage(e.getMessage(), 1);
        }
    }

    static void createBucket(String bucket_name) {
        final AmazonS3 s3 = new AmazonS3Client();
        try {
            Bucket b = s3.createBucket(bucket_name);
        } catch (AmazonServiceException e) {
            ErrorExit.errorMessage(e.getErrorMessage(), 1);
        }
    }
    static void uploadObj(String bucket_name, String key_name, String file_path) {
        final AmazonS3 s3 = new AmazonS3Client();
        try {
            s3.putObject(bucket_name, key_name, file_path);
        } catch (AmazonServiceException e) {
            ErrorExit.errorMessage(e.getMessage(), 1);
        }
    }

}
