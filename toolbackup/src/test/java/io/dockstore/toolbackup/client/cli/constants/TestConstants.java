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

    public static final String BUCKET;
    public static final String NON_EXISTING_BUCKET;
    public static final String PREFIX;
    // local
    public static final String BASEDIR;
    public static final String DIR;
    public static final  String DIR_CHECK_SIZE;
    public static final String NON_EXISTING_DIR;
    // docker
    public static final String IMG;
    public static final String NON_EXISTING_IMG;

    public static final String TOOL_NAME = "saver_test_tool";
    public static final String ID = "test/" + TOOL_NAME;
    public static final String TAG = "master";
    public static final String TIME = FormattedTimeGenerator.getFormattedTimeNow(LocalDateTime.now());

    private TestConstants() {
        // hidden constructor
    }

    static {
        File configFile = new File(USER_HOME + File.separator + ".toolbackup" + File.separator + "config.ini");
        HierarchicalINIConfiguration config = null;
        try {
            config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }
        assert config != null;
        BUCKET = config.getString("bucket", "testbucket");
        PREFIX = config.getString("prefix", "testprefix");
        IMG = config.getString("img", "docker/whalesay");
        BASEDIR = config.getString("baseDir", USER_HOME + File.separator + "dockstore-saver");

        DIR = config.getString("dir", BASEDIR + File.separator + "dir");
        DIR_CHECK_SIZE = config.getString("checkSizeDir", BASEDIR + File.separator + "checkSize");

        NON_EXISTING_BUCKET = config.getString("nonexistent.bucket", "dockstore-saver-gibberish");
        NON_EXISTING_DIR = config.getString("nonexistent.dir", "dockstore-saver-gibberish");
        NON_EXISTING_IMG = config.getString("nonexistent.img", "dockstore-saver-gibberish");
    }
}
