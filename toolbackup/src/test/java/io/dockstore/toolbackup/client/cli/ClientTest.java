package io.dockstore.toolbackup.client.cli;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class ClientTest extends Base {
    private static final DockerCommunicator DOCKER_COMMUNICATOR = new DockerCommunicator();
    private static final String[] ARGV = new String[]{"--bucket-name", BUCKET, "--local-dir", DIR, "--key-prefix", PREFIX, "--test-mode-activate", "true"};

    /*
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
    */

    @Test
    public void setupEnvironment() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        client.setupClientEnvironment();
        assertTrue("client API could not start", client.getContainersApi() != null);
    }

    @AfterClass
    public static void closeDocker() {
        DOCKER_COMMUNICATOR.closeDocker();
    }
}