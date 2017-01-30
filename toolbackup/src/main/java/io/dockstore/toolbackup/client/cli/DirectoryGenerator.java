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
    static boolean createDirectory(String directory) {
        if(!(new File(directory).exists())) {
            try {
                Files.createDirectories(Paths.get(directory));
            } catch (IOException e) {
                ErrorExit.exceptionMessage(e, "Could not create directory: " + directory, IO_ERROR);
            }
            return true;
        }
        return false;
    }

    static void validatePath(String dirPath) {
        File file = new File(dirPath);
        if(!createDirectory(dirPath)) {
            if(file.isFile()) {
                ErrorExit.errorMessage("The parameter, local-dir, MUST be a local directory. Please rename or delete the existing: " + dirPath + " and create a directory with the same name.", CLIENT_ERROR);
            } else {
                out.println("The directory: " + dirPath + " is valid");
            }
        }
    }
}
