package io.dockstore.toolbackup.client.cli;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.junit.Test;

/**
 * Created by kcao on 24/01/17.
 */
public class S3CommunicatorTest extends Base {
    private final S3Communicator s3Communicator = new S3Communicator();

    @Test(expected = IllegalArgumentException.class)
    public void uploadDirectory() throws Exception {
        s3Communicator.uploadDirectory(BUCKET, PREFIX, NONEXISTING_DIR, null);
    }

    @Test (expected = RuntimeException.class)
    public void downloadDirectory_notDir() throws Exception {
        s3Communicator.downloadDirectory(BUCKET, PREFIX, NONEXISTING_DIR);
    }

    @Test (expected = AmazonS3Exception.class)
    public void downloadDirectory_noBucket() throws Exception {
        s3Communicator.downloadDirectory(NONEXISTING_BUCKET, "", DIR);
    }
}