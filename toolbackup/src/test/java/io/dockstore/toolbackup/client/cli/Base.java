package io.dockstore.toolbackup.client.cli;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import java.io.File;

import static io.dockstore.toolbackup.client.cli.Client.API_ERROR;

/**
 * Created by kcao on 25/01/17.
 */
public class Base {
    private static HierarchicalINIConfiguration config;
    protected static String BUCKET;
    protected static String PREFIX;
    protected static String DIR;
    protected static String IMG;

    protected static String NONEXISTING_BUCKET;
    protected static String NONEXISTING_DIR;
    protected static String NONEXISTING_IMG;

    protected static String REPORT;

    static {
        String userHome = System.getProperty("user.home");
        try {
            File configFile = new File(userHome + File.separator + ".toolbackup" + File.separator + "config.ini");
            config = new HierarchicalINIConfiguration(configFile);
            BUCKET = config.getString("bucket", "testbucket");
            PREFIX = config.getString("prefix", "testprefix");
            DIR = config.getString("dir", userHome);
            IMG = config.getString("img", "docker/whalesay");

            NONEXISTING_BUCKET = config.getString("nonexisting.bucket", "nonexisting");
            NONEXISTING_DIR = config.getString("nonexisting.dir", "nonexisting");
            NONEXISTING_IMG = config.getString("nonexisting.img", "gibberish0981");

            REPORT = config.getString("report.path", null);
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }
    }
}
