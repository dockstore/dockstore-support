package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.bucket;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.dir;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.prefix;
import static org.junit.Assume.assumeTrue;

import io.dockstore.toolbackup.client.cli.common.AWSConfig;
import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Created by kcao on 25/01/17.
*/
public class DownloaderTest {
    @BeforeClass
    public static void setUp() {
        AWSConfig.generateCredentials();
    }

    @Test
    public void download() throws Exception {
        S3Communicator s3Communicator = new S3Communicator();
        s3Communicator.createBucket(bucket);
        DirectoryGenerator.createDir(dir);

        List<File> files = new ArrayList<>();
        File file = new File(dir + File.separator + "helloworld.txt");
        assumeTrue(file.isFile() || !file.exists());
        file.createNewFile();
        files.add(file);

        s3Communicator.uploadDirectory(bucket, prefix, dir, files, false);

        new Downloader(null).download(bucket, prefix, dir, s3Communicator);
        DirCleaner.deleteDir(dir);
    }
}
