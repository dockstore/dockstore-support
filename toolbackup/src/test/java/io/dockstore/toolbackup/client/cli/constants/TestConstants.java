package io.dockstore.toolbackup.client.cli.constants;

import io.dockstore.toolbackup.client.cli.ErrorExit;
import io.dockstore.toolbackup.client.cli.FormattedTimeGenerator;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import java.io.File;

import static io.dockstore.toolbackup.client.cli.Client.API_ERROR;

/**
 * Created by kcao on 08/02/17.
 */
public class TestConstants {
    private static HierarchicalINIConfiguration config;

    public static final String USER_HOME = System.getProperty("user.home");
    // aws
    public static String BUCKET;
    public static String NONEXISTING_BUCKET;
    public static String PREFIX;
    // local
    public static String BASEDIR;
    public static String DIR;
    public static String DIR_CHECK_SIZE;
    public static String DIR_SAME_NAME;
    public static String NONEXISTING_DIR;
    // docker
    public static String IMG;
    public static String NONEXISTING_IMG;

    public static final String TOOL_NAME = "saver_test_tool";
    public static final String ID = "test/" + TOOL_NAME;
    public static final String TAG = "master";
    public static final String TIME = FormattedTimeGenerator.getFormattedTimeNow();

    static {
        try {
            File configFile = new File(USER_HOME + File.separator + ".toolbackup" + File.separator + "config.ini");
            config = new HierarchicalINIConfiguration(configFile);
            BUCKET = config.getString("bucket", "testbucket");
            PREFIX = config.getString("prefix", "testprefix");
            IMG = config.getString("img", "docker/whalesay");
            BASEDIR = config.getString("baseDir", USER_HOME + File.separator + "dockstore-saver");

            DIR = config.getString("dir",  BASEDIR + File.separator + "dir");
            DIR_CHECK_SIZE = config.getString("checkSizeDir", BASEDIR + File.separator + "checkSize");

            NONEXISTING_BUCKET = config.getString("nonexistent.bucket", "dockstore-saver-gibberish");
            NONEXISTING_DIR = config.getString("nonexistent.dir", "dockstore-saver-gibberish");
            NONEXISTING_IMG = config.getString("nonexistent.img", "dockstore-saver-gibberish");
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }
    }
}
