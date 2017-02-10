package io.dockstore.toolbackup.client.cli;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.TransferManager;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.dockstore.toolbackup.client.cli.Client.COMMAND_ERROR;
import static java.lang.System.out;

/**
 * Created by kcao on 12/01/17.
 */
class S3Communicator {
    private TransferManager transferManager;
    private AmazonS3Client s3Client;

    S3Communicator() {
        s3Client = new AmazonS3Client(new ProfileCredentialsProvider().getCredentials());
        s3Client.setEndpoint("http://localhost:8080");
        s3Client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).disableChunkedEncoding().build());

        transferManager = new TransferManager(s3Client);
    }

    S3Communicator(String section, String endpoint) {
        ClientConfiguration opts = new ClientConfiguration();
        opts.setSignerOverride("S3SignerType");
        s3Client = new AmazonS3Client(new ProfileCredentialsProvider(section).getCredentials(),  opts);

        s3Client.setEndpoint(endpoint);
        s3Client.setS3ClientOptions(S3ClientOptions.builder().build());

        transferManager = new TransferManager(s3Client);
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
        return s3Client.doesBucketExist(bucketName);
    }

    void createBucket(String bucketName) {
        if(!doesBucketExist(bucketName)) {
            s3Client.createBucket(new CreateBucketRequest(bucketName));
        }
    }

    Map<String, Long> getKeysToSizes(String bucketName, String prefix) {
        createBucket(bucketName);

        List<S3ObjectSummary> objectSummaries = s3Client.listObjects(bucketName, prefix).getObjectSummaries();
        Map<String, Long> keysToSizes = objectSummaries.stream().collect(Collectors.toMap(S3ObjectSummary::getKey, S3ObjectSummary::getSize));

        return keysToSizes;
    }

     private static ObjectMetadataProvider encrypt() {
        ObjectMetadataProvider objectMetadataProvider = new ObjectMetadataProvider() {
            @Override
            public void provideObjectMetadata(File file, ObjectMetadata objectMetadata) {
                objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            }
        };
        return objectMetadataProvider;
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
