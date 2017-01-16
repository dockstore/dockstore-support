package io.dockstore.tooltester.client.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by kcao on 11/01/17.
 */
class FormattedTimeGenerator {
    static String getFormattedTimeNow() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        return now.format(formatter);
    }
    static String getFormattedCreationTime(File file) {
        FileTime fileTime = null;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            fileTime = attributes.creationTime();
        } catch (IOException e) {
            throw new RuntimeException("Could not get formatted creation time");
        }
        return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(fileTime.toMillis());
    }
}
