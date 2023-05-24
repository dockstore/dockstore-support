package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.IMG;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.NON_EXISTING_IMG;
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
public class DockerCommunicatorIT {
    private static final DockerCommunicator DOCKER_COMMUNICATOR = new DockerCommunicator();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    @Test
    public void pullDockerImage() {
        DOCKER_COMMUNICATOR.pullDockerImage(IMG);
    }

    /**
     * Test that the script displays to the user it is unable to pull a non-existent image
     */
    @Test
    public void pullDockerImageNonexistent() {
        assumeFalse(DOCKER_COMMUNICATOR.pullDockerImage(NON_EXISTING_IMG));
        System.setErr(new PrintStream(errContent));
        DOCKER_COMMUNICATOR.pullDockerImage(NON_EXISTING_IMG);
        assertTrue(errContent.toString().contains("Unable to pull"));
    }

    /**
     * Sanity check for saveDockerImage()
     */
    @Test
    public void saveDockerImage() {
        assumeTrue(DOCKER_COMMUNICATOR.pullDockerImage(IMG));
        DOCKER_COMMUNICATOR.saveDockerImage(IMG);
    }

    /**
     * Test for RuntimeException when trying to save a non-existent image
     */
    @Test (expected = RuntimeException.class)
    public void saveDockerImageNonexistent() {
        assumeFalse(DOCKER_COMMUNICATOR.pullDockerImage(NON_EXISTING_IMG));
        DOCKER_COMMUNICATOR.saveDockerImage(NON_EXISTING_IMG);
    }

    @AfterClass
    public static void closeDocker() {
        DOCKER_COMMUNICATOR.closeDocker();
    }
}
