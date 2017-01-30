package io.dockstore.toolbackup.client.cli;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static io.dockstore.toolbackup.client.cli.Client.API_ERROR;

/**
 * Created by kcao on 25/01/17.
 */
public class Base {
    private static HierarchicalINIConfiguration config;
    protected static String BUCKET;
    protected static String PREFIX;

    protected static String DIR;
    protected static String DIR_SAME;

    protected static String IMG;

    protected static String NONEXISTING_BUCKET;
    protected static String NONEXISTING_DIR;
    protected static String NONEXISTING_IMG;

    protected static String REPORT;

    protected static String userHome = System.getProperty("user.home");

    static {
        try {
            File configFile = new File(userHome + File.separator + ".toolbackup" + File.separator + "config.ini");
            config = new HierarchicalINIConfiguration(configFile);
            BUCKET = config.getString("bucket", "testbucket");
            PREFIX = config.getString("prefix", "testprefix");

            DIR = config.getString("dir", userHome + File.separator + "dockstore-saver" + File.separator  + "dir");
            DirectoryGenerator.validatePath(DIR);

            DIR_SAME = config.getString("checksize.dir", userHome + File.separator + "dockstore-saver" + File.separator  + "size");
            DirectoryGenerator.validatePath(DIR_SAME);
            File newFile = new File(DIR_SAME + File.separator + "helloworld.txt");
            try {
                FileUtils.writeStringToFile(newFile, "Hello world!", "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException("Could not create " + newFile.getAbsolutePath());
            }

            IMG = config.getString("img", "docker/whalesay");

            NONEXISTING_BUCKET = config.getString("nonexisting.bucket", "dockstore-saver-gibberish");
            NONEXISTING_DIR = config.getString("nonexisting.dir", "dockstore-saver-gibberish");
            NONEXISTING_IMG = config.getString("nonexisting.img", "dockstore-saver-gibberish");

            REPORT = DIR + File.separator + "report";
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }
    }
}
