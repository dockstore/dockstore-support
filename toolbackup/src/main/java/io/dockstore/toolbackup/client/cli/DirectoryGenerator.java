package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.toolbackup.client.cli.Client.IO_ERROR;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by kcao on 11/01/17.
 */
public final class DirectoryGenerator {

    private DirectoryGenerator() {
        // hidden constructor
    }

    public static void createDir(String dirPath) {
        try {
            Files.createDirectories(Paths.get(dirPath));
        } catch (FileAlreadyExistsException e) {
            ErrorExit.exceptionMessage(e, "Please delete your file: " + dirPath + " if you want to use this as a directory", CLIENT_ERROR);
        } catch (IOException e) {
            ErrorExit.exceptionMessage(e, "Could not create directory: " + dirPath, IO_ERROR);
        }
    }
}
