package io.dockstore.tooltester.client.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by kcao on 11/01/17.
 */
class DirectoryGenerator {
    static void createDirectory(String dirPath) {
        Path newDirPath = Paths.get(dirPath);
        if(!Files.exists(newDirPath)) {
            try {
                Files.createDirectories(newDirPath);
            } catch (IOException e) {
                throw new RuntimeException("IOException occurred while trying to create new directories");
            }
        }
    }
}
