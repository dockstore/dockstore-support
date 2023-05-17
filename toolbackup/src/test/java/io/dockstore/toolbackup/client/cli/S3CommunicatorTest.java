package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.bucket;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.dir;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.nonexistingBucket;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.nonexistingDir;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.prefix;
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
public class S3CommunicatorTest {

    private static S3Communicator s3Communicator;

    @BeforeClass
    public static void setUp() {
        AWSConfig.generateCredentials();

        DirectoryGenerator.createDir(dir);

        s3Communicator = new S3Communicator();

        s3Communicator.createBucket(bucket);
    }

    @Test
    public void uploadDirectory() throws Exception {
        List<File> files = new ArrayList<>();
        File file = new File(dir + File.separator + "helloworld.txt");
        assumeTrue(file.isFile() || !file.exists());
        file.createNewFile();
        files.add(file);

        s3Communicator.uploadDirectory(bucket, prefix, dir, files, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void uploadDirectoryNonexistentDirectory() {
        s3Communicator.uploadDirectory(bucket, prefix, nonexistingDir, null, false);
    }

    @Test(expected = RuntimeException.class)
    public void downloadDirectoryNotDir() {
        s3Communicator.downloadDirectory(bucket, prefix, nonexistingDir);
    }

    @Test(expected = AmazonS3Exception.class)
    public void downloadDirectoryNoBucket() {
        assumeFalse(s3Communicator.doesBucketExist(nonexistingBucket));
        s3Communicator.downloadDirectory(nonexistingBucket, "", dir);
    }

    @AfterClass
    public static void shutDownS3() {
        s3Communicator.shutDown();
        DirCleaner.deleteDir(dir);
    }
}
