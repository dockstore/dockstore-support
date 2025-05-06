package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR_CHECK_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.dockstore.toolbackup.client.cli.common.AWSConfig;
import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import java.io.File;
import java.io.IOException;
import java.util.List;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Created by kcao on 25/01/17.
 */
public class ClientIT {

    private static Client client;

    @BeforeClass
    public static void setUp() {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        client = new Client(parsed);
        AWSConfig.generateCredentials();
    }

    /**
     * Test for GA4GH API connection
     *
     */
    @Test
    public void setupClientEnvironment() {
        client.setupClientEnvironment();
        assertNotNull("client API could not start", client.getContainersApi());
    }

    /**
     * Test that the calculation for files' sizes is correct
     *
     */
    @Test
    public void getFilesTotalSizeB() {
        File newFile = new File(DIR_CHECK_SIZE + File.separator + "helloworld.txt");
        try {
            FileUtils.writeStringToFile(newFile, "Hello world!", "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Could not create " + newFile.getAbsolutePath());
        }
        File dir = new File(DIR_CHECK_SIZE);
        long directorySize = client.getFilesTotalSizeB((List<File>) FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE));
        assertEquals(FileUtils.sizeOfDirectory(dir), directorySize);
        DirCleaner.deleteDir(DIR_CHECK_SIZE);
    }

    /**
     * Test for saving pulled docker image
     *
     */


    @AfterClass
    public static void closeDocker() {
        DirCleaner.deleteDir(DIR);
    }
}
