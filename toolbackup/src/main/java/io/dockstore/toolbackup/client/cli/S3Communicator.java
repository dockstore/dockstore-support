package io.dockstore.toolbackup.client.cli;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import static io.dockstore.toolbackup.client.cli.Client.COMMAND_ERROR;
import static java.lang.System.out;

/**
 * Created by kcao on 12/01/17.
 */
class S3Communicator {
    private TransferManager transferManager;
    private AmazonS3 s3Client;

    S3Communicator() {
        s3Client = AmazonS3ClientBuilder.standard().withCredentials(new ProfileCredentialsProvider())
            .withPathStyleAccessEnabled(true).withChunkedEncodingDisabled(true).withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:8080","")).build();
        transferManager = TransferManagerBuilder.standard().withS3Client(s3Client).build();
    }

    S3Communicator(String section, String endpoint) {
        s3Client = AmazonS3ClientBuilder.standard().withCredentials(new ProfileCredentialsProvider(section))
            .withPathStyleAccessEnabled(true).withChunkedEncodingDisabled(true).withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint,"")).build();
        transferManager = TransferManagerBuilder.standard().withS3Client(s3Client).build();
    }

    //-----------------------Report-----------------------
    long getCloudTotalInB(String bucketName, String prefix) {
        long total = 0;

        List<S3ObjectSummary> objectSummaries = s3Client.listObjects(bucketName, prefix).getObjectSummaries();
        List<Long> sizes = objectSummaries.stream().map(S3ObjectSummary::getSize).collect(Collectors.toList());

        for(long size : sizes) {
            total += size;
        }

        return total;
    }

    //-----------------------Upload-----------------------
    boolean doesBucketExist(String bucketName) {
        return s3Client.doesBucketExistV2(bucketName);
    }

    void createBucket(String bucketName) {
        // test server throws exceptions here
        boolean doesBucketExist = false;
        try {
            doesBucketExist = doesBucketExist(bucketName);
        } catch (Exception e){
            System.out.println("blah");
        }
        if(!doesBucketExist) {
            s3Client.createBucket(new CreateBucketRequest(bucketName));
        }
    }

    Map<String, Long> getKeysToSizes(String bucketName, String prefix) {
        createBucket(bucketName);
        List<S3ObjectSummary> objectSummaries = s3Client.listObjects(bucketName, prefix).getObjectSummaries();
        return objectSummaries.stream().collect(Collectors.toMap(S3ObjectSummary::getKey, S3ObjectSummary::getSize));
    }

     private static ObjectMetadataProvider encrypt() {
         return (file, objectMetadata) -> objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
     }

    void uploadDirectory(String bucketName, String keyPrefix, String dirPath, List<File> files, boolean encrypt) {
        createBucket(bucketName);

        try {
            if(encrypt) {
                transferManager.uploadFileList(bucketName, keyPrefix, new File(dirPath), files, encrypt()).waitForCompletion();
            } else {
                transferManager.uploadFileList(bucketName, keyPrefix, new File(dirPath), files).waitForCompletion();
            }
            out.println("Uploaded necessary files in: " + dirPath);
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not upload the directory: " + dirPath + " in its entirety");
        } catch (AmazonS3Exception e) {
            ErrorExit.exceptionMessage(e, "MultiplePartUpload cannot finish. Check your keys and sign methods.", COMMAND_ERROR);
        }
    }

    //-----------------------Download-----------------------
    void downloadDirectory(String bucketName, String keyPrefix, String dirPath) {
        File dir = new File(dirPath);

        if(!dir.isDirectory()) {
            throw new RuntimeException("Not a local directory thus nothing will be saved");
        } else {
            try {
                transferManager.downloadDirectory(bucketName, keyPrefix, new File(dirPath), true).waitForCompletion();
                out.println("Downloaded the bucket(" + bucketName + ") with the prefix(" + keyPrefix + ") to the local directory: " + dirPath);
            } catch (InterruptedException e) {
                throw new RuntimeException("Could not download the bucket: " + bucketName + " in its entirety");
            }
        }
    }

    //-----------------------Shutdown-----------------------
    void shutDown() {
        transferManager.shutdownNow();
    }
}
