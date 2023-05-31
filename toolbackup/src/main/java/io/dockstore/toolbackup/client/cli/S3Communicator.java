package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.Client.COMMAND_ERROR;
import static java.lang.System.out;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
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
    private S3Client s3Client;

    S3Communicator() throws URISyntaxException {
        S3Configuration config = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false)
            .build();
        s3Client = S3Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).endpointOverride(new URI("http://localhost:8080"))
            .serviceConfiguration(config)
            .build();

        S3AsyncClient  s3AsyncClient = S3AsyncClient.builder().credentialsProvider(ProfileCredentialsProvider.create()).endpointOverride(new URI("http://localhost:8080"))
                .serviceConfiguration(config)
                .build();
        transferManager = S3TransferManager.builder()
            .s3Client(s3AsyncClient)
            .build();
    }

    S3Communicator(String section, String endpoint) throws URISyntaxException {
        S3Configuration config = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false)
            .build();
        URI uri = new URI(endpoint);
        s3Client = S3Client.builder().credentialsProvider(ProfileCredentialsProvider.builder().profileName(section).build()).endpointOverride(uri)
            .serviceConfiguration(config)
            .build();

        S3AsyncClient  s3AsyncClient = S3AsyncClient.builder().credentialsProvider(ProfileCredentialsProvider.create()).endpointOverride(uri)
            .serviceConfiguration(config)
            .build();
        transferManager = S3TransferManager.builder()
            .s3Client(s3AsyncClient)
            .build();
    }

    //-----------------------Report-----------------------
    long getCloudTotalInB(String bucketName, String prefix) {
        long total = 0;

        final List<S3Object> contents = s3Client.listObjects(ListObjectsRequest.builder().bucket(bucketName).prefix(prefix).build()).contents();
        List<Long> sizes = contents.stream().map(S3Object::size).collect(Collectors.toList());

        for (long size : sizes) {
            total += size;
        }

        return total;
    }

    //-----------------------Upload-----------------------
    boolean doesBucketExist(String bucketName) {
        try {
            final HeadBucketResponse headBucketResponse = s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            return false;
        }
        return true;
    }

    void createBucket(String bucketName) {
        if (!doesBucketExist(bucketName)) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    Map<String, Long> getKeysToSizes(String bucketName, String prefix) {
        createBucket(bucketName);

        List<S3Object> contents = s3Client.listObjects(ListObjectsRequest.builder().bucket(bucketName).prefix(prefix).build()).contents();
        Map<String, Long> keysToSizes = contents.stream().collect(Collectors.toMap(S3Object::key, S3Object::size));

        return keysToSizes;
    }

    // TODO: implement encryption, but may not be too important
    //    private static ObjectMetadataProvider encrypt() {
    //        ObjectMetadataProvider objectMetadataProvider = new ObjectMetadataProvider() {
    //            @Override
    //            public void provideObjectMetadata(File file, ObjectMetadata objectMetadata) {
    //                objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
    //            }
    //        };
    //        return objectMetadataProvider;
    //    }

    void uploadDirectory(String bucketName, String keyPrefix, String dirPath, List<File> files, boolean encrypt) {
        createBucket(bucketName);

        try {
            if (encrypt) {
                // transferManager.uploadFileList(bucketName, keyPrefix, new File(dirPath), files, encrypt()).waitForCompletion();
                transferManager.uploadDirectory(UploadDirectoryRequest.builder().bucket(bucketName).source(new File(dirPath).toPath()).build()).completionFuture().get();
            } else {
                // transferManager.uploadFileList(bucketName, keyPrefix, new File(dirPath), files).waitForCompletion();
                transferManager.uploadDirectory(UploadDirectoryRequest.builder().bucket(bucketName).source(new File(dirPath).toPath()).build()).completionFuture().get();
            }
            out.println("Uploaded necessary files in: " + dirPath);
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not upload the directory: " + dirPath + " in its entirety");
        } catch (ExecutionException e) {
            ErrorExit.exceptionMessage(e, "MultiplePartUpload cannot finish. Check your keys and sign methods.", COMMAND_ERROR);
        }
    }

    //-----------------------Download-----------------------
    void downloadDirectory(String bucketName, String keyPrefix, String dirPath) {
        File dir = new File(dirPath);

        if (!dir.isDirectory()) {
            throw new RuntimeException("Not a local directory thus nothing will be saved");
        } else {
            try {
                // transferManager.downloadDirectory(bucketName, keyPrefix, new File(dirPath), true).waitForCompletion();
                transferManager.downloadDirectory(DownloadDirectoryRequest.builder().bucket(bucketName).destination(new File(dirPath).toPath()).filter(s3Object -> s3Object.key().startsWith(keyPrefix)).build()).completionFuture().get();

                out.println("Downloaded the bucket(" + bucketName + ") with the prefix(" + keyPrefix + ") to the local directory: " + dirPath);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Could not download the bucket: " + bucketName + " in its entirety");
            }
        }
    }

    //-----------------------Shutdown-----------------------
    void shutDown() {
        transferManager.close();
    }
}
