package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.BUCKET;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.NON_EXISTING_BUCKET;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.NON_EXISTING_DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.PREFIX;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import io.dockstore.toolbackup.client.cli.common.AWSConfig;
import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Created by kcao on 24/01/17.
 */
public class S3CommunicatorIT {

    private static S3Communicator s3Communicator;

    @BeforeClass
    public static void setUp() {
        AWSConfig.generateCredentials();

        DirectoryGenerator.createDir(DIR);

        s3Communicator = new S3Communicator();

        s3Communicator.createBucket(BUCKET);
    }

    @Test
    public void uploadDirectory() throws Exception {
        List<File> files = new ArrayList<>();
        File file = new File(DIR + File.separator + "helloworld.txt");
        assumeTrue(file.isFile() || !file.exists());
        file.createNewFile();
        files.add(file);

        s3Communicator.uploadDirectory(BUCKET, PREFIX, DIR, files, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void uploadDirectoryNonexistentDirectory() {
        s3Communicator.uploadDirectory(BUCKET, PREFIX, NON_EXISTING_DIR, null, false);
    }

    @Test(expected = RuntimeException.class)
    public void downloadDirectoryNotDir() {
        s3Communicator.downloadDirectory(BUCKET, PREFIX, NON_EXISTING_DIR);
    }

    @Test(expected = AmazonS3Exception.class)
    public void downloadDirectoryNoBucket() {
        assumeFalse(s3Communicator.doesBucketExist(NON_EXISTING_BUCKET));
        s3Communicator.downloadDirectory(NON_EXISTING_BUCKET, "", DIR);
    }

    @AfterClass
    public static void shutDownS3() {
        s3Communicator.shutDown();
        DirCleaner.deleteDir(DIR);
    }
}
