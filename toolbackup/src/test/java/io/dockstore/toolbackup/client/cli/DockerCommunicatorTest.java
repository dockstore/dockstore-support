package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.img;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.nonexistingImg;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Created by kcao on 25/01/17.
 */
public class DockerCommunicatorTest {
    private static final DockerCommunicator DOCKER_COMMUNICATOR = new DockerCommunicator();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    @Test
    public void pullDockerImage() {
        DOCKER_COMMUNICATOR.pullDockerImage(img);
    }

    /**
     * Test that the script displays to the user it is unable to pull a non-existent image
     */
    @Test
    public void pullDockerImageNonexistent() {
        assumeFalse(DOCKER_COMMUNICATOR.pullDockerImage(nonexistingImg));
        System.setErr(new PrintStream(errContent));
        DOCKER_COMMUNICATOR.pullDockerImage(nonexistingImg);
        assertTrue(errContent.toString().contains("Unable to pull"));
    }

    /**
     * Sanity check for saveDockerImage()
     */
    @Test
    public void saveDockerImage() {
        assumeTrue(DOCKER_COMMUNICATOR.pullDockerImage(img));
        DOCKER_COMMUNICATOR.saveDockerImage(img);
    }

    /**
     * Test for RuntimeException when trying to save a non-existent image
     */
    @Test (expected = RuntimeException.class)
    public void saveDockerImageNonexistent() {
        assumeFalse(DOCKER_COMMUNICATOR.pullDockerImage(nonexistingImg));
        DOCKER_COMMUNICATOR.saveDockerImage(nonexistingImg);
    }

    @AfterClass
    public static void closeDocker() {
        DOCKER_COMMUNICATOR.closeDocker();
    }
}
