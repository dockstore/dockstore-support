package io.dockstore.toolbackup.client.cli;

import org.junit.Test;

import java.io.File;

import static org.junit.Assume.assumeTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class DownloaderTest extends Base {
    @Test
    public void main() throws Exception {
        assumeTrue(new File(DIR).isDirectory());
        Downloader.main(new String[]{"--bucket-name", BUCKET, "--key-prefix", PREFIX, "--destination-dir", DIR});
    }
}