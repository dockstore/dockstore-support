package io.dockstore.toolbackup.client.cli;

import io.dockstore.toolbackup.client.cli.common.AWSConfig;
import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.BUCKET;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.PREFIX;

/**
 * Created by kcao on 25/01/17.
*/
public class DownloaderTest {
    @BeforeClass
    public static void setUp() {
        AWSConfig.generateCredentials();
    }

    @Test
    public void main() throws Exception {
        S3Communicator s3Communicator = new S3Communicator();
        s3Communicator.createBucket(BUCKET);
        DirectoryGenerator.createDir(DIR);
        List<File> files = new ArrayList<>();
        files.add(new File(DIR + File.separator + "helloworld.txt"));
        s3Communicator.uploadDirectory(BUCKET, PREFIX, DIR, files);
        new Downloader(null).download(BUCKET, PREFIX, DIR, s3Communicator);
        DirCleaner.deleteDir(DIR);
    }
}