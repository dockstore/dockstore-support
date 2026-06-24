package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.Client.COMMAND_ERROR;
import static java.lang.System.out;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.config.DownloadFilter;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;

/**
 * Created by kcao on 12/01/17.
 */
class S3Communicator {

    private S3TransferManager transferManager;
    private S3AsyncClient s3Client;

    S3Communicator() {
        s3Client = S3AsyncClient.builder().endpointOverride(URI.create("http://localhost:8080")).credentialsProvider(ProfileCredentialsProvider.builder().build())
            .forcePathStyle(true).build();

        transferManager = S3TransferManager.builder().s3Client(s3Client).build();
    }

    S3Communicator(String section, String endpoint) {
        s3Client = S3AsyncClient.builder().credentialsProvider(ProfileCredentialsProvider.builder().build()).endpointOverride(URI.create(endpoint)).credentialsProvider(ProfileCredentialsProvider.builder().build())
           .forcePathStyle(true).build();

        transferManager = S3TransferManager.builder().s3Client(s3Client).build();
    }

    //-----------------------Report-----------------------
    long getCloudTotalInB(String bucketName, String prefix) throws ExecutionException, InterruptedException {
        long total = 0;

        List<S3Object> objectSummaries = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix)
                .build()).get().contents();
        List<Long> sizes = objectSummaries.stream().map(S3Object::size).toList();

        for (long size : sizes) {
            total += size;
        }

        return total;
    }

    //-----------------------Upload-----------------------
    boolean doesBucketExist(String bucketName) throws ExecutionException, InterruptedException {
        return s3Client.listBuckets(ListBucketsRequest.builder().prefix(bucketName).build()).get().hasBuckets();
    }

    void createBucket(String bucketName) throws ExecutionException, InterruptedException {
        if (!doesBucketExist(bucketName)) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName)
                    .build());
        }
    }

    Map<String, Long> getKeysToSizes(String bucketName, String prefix) throws ExecutionException, InterruptedException {
        createBucket(bucketName);

        List<S3Object> objectSummaries = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix)
                .build()).get().contents();
        Map<String, Long> keysToSizes = objectSummaries.stream().collect(Collectors.toMap(S3Object::key, S3Object::size));

        return keysToSizes;
    }

    void uploadDirectory(String bucketName, String keyPrefix, String dirPath, List<File> files, boolean encrypt) throws ExecutionException, InterruptedException {
        createBucket(bucketName);

        try {
            transferManager.uploadDirectory(UploadDirectoryRequest.builder().source(Paths.get(dirPath)).bucket(bucketName).s3Prefix(keyPrefix).build());
            out.println("Uploaded necessary files in: " + dirPath);
        } catch (S3Exception e) {
            ErrorExit.exceptionMessage(e, "MultiplePartUpload cannot finish. Check your keys and sign methods.", COMMAND_ERROR);
        }
    }

    //-----------------------Download-----------------------
    void downloadDirectory(String bucketName, String keyPrefix, String dirPath) {
        File dir = new File(dirPath);

        if (!dir.isDirectory()) {
            throw new RuntimeException("Not a local directory thus nothing will be saved");
        } else {
            if (keyPrefix == null) {
                throw new IllegalArgumentException();
            }
            DownloadFilter filter = s3Object -> s3Object.key().startsWith(keyPrefix);
            transferManager.downloadDirectory(DownloadDirectoryRequest.builder().bucket(bucketName).filter(filter).build());
            out.println("Downloaded the bucket(" + bucketName + ") with the prefix(" + keyPrefix + ") to the local directory: " + dirPath);
        }
    }

    //-----------------------Shutdown-----------------------
    void shutDown() {
        transferManager.close();
    }
}
