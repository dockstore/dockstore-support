package io.dockstore.toolbackup.client.cli;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class ClientTest extends Base {
    private static final DockerCommunicator DOCKER_COMMUNICATOR = new DockerCommunicator();
    private static final String[] ARGV = new String[]{"--bucket-name", BUCKET, "--local-dir", DIR, "--key-prefix", PREFIX, "--test-mode-activate", "true"};

    @BeforeClass
    public static void setUp() {
        generateAWSConfig();
    }

    @Test
    public void setupEnvironment() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        client.setupClientEnvironment();
        assertTrue("client API could not start", client.getContainersApi() != null);
    }

    @Test
    public void main() throws Exception {
        Client.main(ARGV);
    }

    @Test
    public void getAddedSizeInB() throws Exception {
        File dir = new File(DIR_SAME);

        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);


        long directorySize = client.getAddedSizeInB((List<File>) FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE));
        assertEquals(FileUtils.sizeOfDirectory(dir), directorySize);
    }

    @Test
    public void saveDockerImage() throws Exception {
        String imgPath = DIR + File.separator + "dockstore-saver-img.tar";
        File imgFile = new File(imgPath);
        assumeTrue(DOCKER_COMMUNICATOR.pullDockerImage(IMG));

        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);

        client.saveDockerImage(IMG, imgFile);
        assertTrue(imgFile.isFile());
    }

    @AfterClass
    public static void closeDocker() {
        DOCKER_COMMUNICATOR.closeDocker();
    }
}