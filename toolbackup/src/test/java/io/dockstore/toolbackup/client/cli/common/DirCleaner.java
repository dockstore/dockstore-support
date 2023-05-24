package io.dockstore.toolbackup.client.cli.common;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

/**
 * Created by kcao on 08/02/17.
 */
public final class DirCleaner {

    private DirCleaner() {
        // hidden constructor
    }
    public static void deleteDir(String dirPath) {
        try {
            FileUtils.deleteDirectory(new File(dirPath));
        } catch (IOException e) {
            throw new RuntimeException("Could not delete " + dirPath);
        }
    }
}
