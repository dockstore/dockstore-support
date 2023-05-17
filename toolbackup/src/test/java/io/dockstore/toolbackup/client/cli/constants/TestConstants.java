package io.dockstore.toolbackup.client.cli.constants;

import static io.dockstore.toolbackup.client.cli.Client.API_ERROR;

import io.dockstore.toolbackup.client.cli.ErrorExit;
import io.dockstore.toolbackup.client.cli.FormattedTimeGenerator;
import java.io.File;
import java.time.LocalDateTime;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

/**
 * Created by kcao on 08/02/17.
 */
public final class TestConstants {

    public static final String USER_HOME = System.getProperty("user.home");

    public static String bucket;
    public static String nonexistingBucket;
    public static String prefix;
    // local
    public static String basedir;
    public static String dir;
    public static String dirCheckSize;
    public static String dirSameName;
    public static String nonexistingDir;
    // docker
    public static String img;
    public static String nonexistingImg;

    public static final String TOOL_NAME = "saver_test_tool";
    public static final String ID = "test/" + TOOL_NAME;
    public static final String TAG = "master";
    public static final String TIME = FormattedTimeGenerator.getFormattedTimeNow(LocalDateTime.now());

    private static HierarchicalINIConfiguration config;

    private TestConstants() {
        // hidden constructor
    }

    static {
        try {
            File configFile = new File(USER_HOME + File.separator + ".toolbackup" + File.separator + "config.ini");
            config = new HierarchicalINIConfiguration(configFile);
            bucket = config.getString("bucket", "testbucket");
            prefix = config.getString("prefix", "testprefix");
            img = config.getString("img", "docker/whalesay");
            basedir = config.getString("baseDir", USER_HOME + File.separator + "dockstore-saver");

            dir = config.getString("dir",  basedir + File.separator + "dir");
            dirCheckSize = config.getString("checkSizeDir", basedir + File.separator + "checkSize");

            nonexistingBucket = config.getString("nonexistent.bucket", "dockstore-saver-gibberish");
            nonexistingDir = config.getString("nonexistent.dir", "dockstore-saver-gibberish");
            nonexistingImg = config.getString("nonexistent.img", "dockstore-saver-gibberish");
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }
    }
}
