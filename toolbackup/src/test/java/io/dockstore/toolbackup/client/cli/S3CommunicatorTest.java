package io.dockstore.toolbackup.client.cli;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import io.dockstore.toolbackup.client.cli.common.AWSConfig;
import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.BUCKET;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.NONEXISTING_BUCKET;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.NONEXISTING_DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.PREFIX;
import static org.junit.Assume.assumeFalse;

/**
 * Created by kcao on 24/01/17.
*/
public class S3CommunicatorTest {
    private static S3Communicator S_3_COMMUNICATOR;

    @BeforeClass
    public static void setUp() {
        AWSConfig.generateCredentials();

        DirectoryGenerator.createDir(DIR);

        S_3_COMMUNICATOR = new S3Communicator();

        S_3_COMMUNICATOR.createBucket(BUCKET);
    }

    @Test(expected = IllegalArgumentException.class)
    public void uploadDirectory() throws Exception {
        S_3_COMMUNICATOR.uploadDirectory(BUCKET, PREFIX, NONEXISTING_DIR, null);
    }

    @Test (expected = RuntimeException.class)
    public void downloadDirectory_notDir() throws Exception {
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
        DirCleaner.deleteDir(DIR);
    }
}