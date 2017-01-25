package io.dockstore.toolbackup.client.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.dockstore.toolbackup.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.toolbackup.client.cli.Client.IO_ERROR;
import static java.lang.System.out;

/**
 * Created by kcao on 11/01/17.
 */
class DirectoryGenerator {
    static void validatePath(String dirPath) {
        File file = new File(dirPath);
        try {
            if(!(file.exists())) {
                out.println("The directory: " + dirPath + " does not exist. Creating this directory now.");
                Files.createDirectories(Paths.get(dirPath));
            } else {
                if(file.isFile()) {
                    ErrorExit.errorMessage("The parameter, local-dir, MUST be a local directory. Please rename or delete the existing: " + dirPath + " and create a directory with the same name.", CLIENT_ERROR);
                } else {
                    out.println("The directory: " + dirPath + " is valid");
                }
            }
        } catch(IOException e) {
            ErrorExit.exceptionMessage(e, "Could not create directory: " + dirPath, IO_ERROR);
        }
    }
}
