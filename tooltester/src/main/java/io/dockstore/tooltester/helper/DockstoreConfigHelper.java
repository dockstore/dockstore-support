package io.dockstore.tooltester.helper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author gluu
 * @since 05/12/17
 */
public class DockstoreConfigHelper {
    private static String baseConfig(String url) {
        return "token: test\\nserver-url: " + url;
    }

    /**
     * Gets the config file of the runner in the form of a string to be sent to Jenkins
     * The config file should be moved out of src/main/resources eventually and into the user's home directory
     * @param url       Server url (probably staging)
     * @param runner    The runner (cwltool, cwlrunner, cromwell, or toil)
     * @return          The config file for the specific runner and server url
     */
    public static String getConfig(String url, String runner) {
        try {
            switch (runner) {
            case "toil":
                return baseConfig(url) + "\\n" + readFile(new File("src/main/resources/toil.config").getAbsolutePath(),
                        StandardCharsets.UTF_8);
            case "cwl-runner":
                return baseConfig(url) + "\\n" + readFile(new File("src/main/resources/cwlrunner.config").getAbsolutePath(),
                        StandardCharsets.UTF_8);
            case "cromwell":
                return baseConfig(url) + "\\n" + readFile(new File("src/main/resources/cromwell.config").getAbsolutePath(),
                        StandardCharsets.UTF_8);
            case "cwltool":
                return baseConfig(url) + "\\n" + readFile(new File("src/main/resources/cwltool.config").getAbsolutePath(),
                        StandardCharsets.UTF_8);
            default:
                ExceptionHandler.errorMessage("Unknown runner.", ExceptionHandler.CLIENT_ERROR);
            }
        } catch (IOException e) {
            System.out.println("Could not get " + runner + " config file.  Using the default one.");
            return baseConfig(url);
        }
        return baseConfig(url);
    }

    /**
     * This converts a file to a string which can be appended to the base config file which will later be sent to Jenkins
     *
     * @param path      Path of the config file
     * @param encoding  Encoding of the file
     * @return          The file contents as a string
     * @throws IOException  When file can't be read
     */
    private static String readFile(String path, Charset encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }
}
