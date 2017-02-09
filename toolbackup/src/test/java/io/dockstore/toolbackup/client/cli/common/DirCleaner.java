package io.dockstore.toolbackup.client.cli.common;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by kcao on 08/02/17.
 */
public class DirCleaner {
    public static void deleteDir(String dirPath) {
        try {
            FileUtils.deleteDirectory(new File(dirPath));
        } catch (IOException e) {
            throw new RuntimeException("Could not delete " + dirPath);
        }
    }
}
