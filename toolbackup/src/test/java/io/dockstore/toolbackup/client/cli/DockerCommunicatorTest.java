package io.dockstore.toolbackup.client.cli;

import org.junit.AfterClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.IMG;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.NONEXISTING_IMG;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class DockerCommunicatorTest {
    private static final DockerCommunicator DOCKER_COMMUNICATOR = new DockerCommunicator();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    @Test
    public void pullDockerImage() throws Exception {
        DOCKER_COMMUNICATOR.pullDockerImage(IMG);
    }

    /**
     * Test that the script displays to the user it is unable to pull a non-existent image
     * @throws Exception
     */
    @Test
    public void pullDockerImage_nonexistent() throws Exception {
        assumeFalse(DOCKER_COMMUNICATOR.pullDockerImage(NONEXISTING_IMG));
        System.setErr(new PrintStream(errContent));
        DOCKER_COMMUNICATOR.pullDockerImage(NONEXISTING_IMG);
        assertTrue(errContent.toString().contains("Unable to pull"));
    }

    /**
     * Sanity check for saveDockerImage()
     * @throws Exception
     */
    @Test
    public void saveDockerImage() throws Exception {
        assumeTrue(DOCKER_COMMUNICATOR.pullDockerImage(IMG));
        DOCKER_COMMUNICATOR.saveDockerImage(IMG);
    }

    /**
     * Test for RuntimeException when trying to save a non-existent image
     * @throws Exception
     */
    @Test (expected = RuntimeException.class)
    public void saveDockerImage_nonexistent() throws Exception {
        assumeFalse(DOCKER_COMMUNICATOR.pullDockerImage(NONEXISTING_IMG));
        DOCKER_COMMUNICATOR.saveDockerImage(NONEXISTING_IMG);
    }

    @AfterClass
    public static void closeDocker() {
        DOCKER_COMMUNICATOR.closeDocker();
    }
}