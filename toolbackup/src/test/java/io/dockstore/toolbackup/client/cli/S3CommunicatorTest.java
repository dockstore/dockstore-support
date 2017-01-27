package io.dockstore.toolbackup.client.cli;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assume.assumeFalse;

/**
 * Created by kcao on 24/01/17.
 */
public class S3CommunicatorTest extends Base {
    private static final S3Communicator S_3_COMMUNICATOR = new S3Communicator();
    File file = new File(NONEXISTING_DIR);

    @Test(expected = IllegalArgumentException.class)
    public void uploadDirectory() throws Exception {
        assumeFalse(file.isDirectory());
        S_3_COMMUNICATOR.uploadDirectory(BUCKET, PREFIX, NONEXISTING_DIR, null);
    }

    @Test (expected = RuntimeException.class)
    public void downloadDirectory_notDir() throws Exception {
        assumeFalse(file.isDirectory());
        S_3_COMMUNICATOR.downloadDirectory(BUCKET, PREFIX, NONEXISTING_DIR);
    }

    @Test (expected = AmazonS3Exception.class)
    public void downloadDirectory_noBucket() throws Exception {
        assumeFalse(S_3_COMMUNICATOR.doesBucketExist(NONEXISTING_BUCKET));
        S_3_COMMUNICATOR.downloadDirectory(NONEXISTING_BUCKET, "", DIR);
    }

    @AfterClass
    public static void shutDownS3() {
        S_3_COMMUNICATOR.shutDown();
    }
}