package io.dockstore.toolbackup.client.cli;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class ClientTest extends Base {
    private static final DockerCommunicator DOCKER_COMMUNICATOR = new DockerCommunicator();

    @Test
    public void main() throws Exception {
        assumeTrue(new File(DIR).isDirectory());
        Client.main(new String[]{"--bucket-name", BUCKET, "--local-dir", DIR,
                "--test-mode-activate", "true", "--key-prefix", PREFIX});
    }

    @Test
    public void getAddedSizeInB() throws Exception {
        assumeTrue(DIR_SAME != null);
        Client client = new Client(null);
        File dir = new File(DIR_SAME);
        assumeTrue(dir.isDirectory());
        long directorySize = client.getAddedSizeInB((List<File>) FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE));
        assertEquals(FileUtils.sizeOfDirectory(dir), directorySize);
    }

    @Test
    public void saveDockerImage() throws Exception {
        Client client = new Client(null);
        String imgPath = DIR + File.separator + "/SavedImg.tar";
        File imgFile = new File(imgPath);
        assumeTrue(DOCKER_COMMUNICATOR.pullDockerImage(IMG));
        client.saveDockerImage(IMG, imgFile);
        assertTrue(imgFile.isFile());
    }

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