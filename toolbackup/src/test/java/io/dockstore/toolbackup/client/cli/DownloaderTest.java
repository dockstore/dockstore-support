package io.dockstore.toolbackup.client.cli;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kcao on 25/01/17.
 */
public class DownloaderTest extends Base {
    private static S3Communicator S_3_COMMUNICATOR;

    @BeforeClass
    public static void setUp() {
        // generate ~/.aws/credentials
        generateAWSConfig();

        S_3_COMMUNICATOR = new S3Communicator();

        if(!S_3_COMMUNICATOR.doesBucketExist(BUCKET)) {
            S_3_COMMUNICATOR.createBucket(BUCKET);

            List<File> files = new ArrayList<>();
            files.add(new File(DIR_SAME + File.separator + "helloworld.txt"));

            S_3_COMMUNICATOR.uploadDirectory(BUCKET,  PREFIX, userHome, files);
        }
    }

    @Test
    public void main() throws Exception {
        Downloader.main(new String[]{"--bucket-name", BUCKET, "--key-prefix", PREFIX, "--destination-dir", DIR});
    }
}