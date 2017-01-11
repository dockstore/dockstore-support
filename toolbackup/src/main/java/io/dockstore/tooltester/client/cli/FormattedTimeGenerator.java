package io.dockstore.tooltester.client.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;

/**
 * Created by kcao on 11/01/17.
 */
class FormattedTimeGenerator {
    static String getFormattedCreationTime(File file) {
        FileTime fileTime = null;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            fileTime = attributes.creationTime();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(fileTime.toMillis());
    }
}
