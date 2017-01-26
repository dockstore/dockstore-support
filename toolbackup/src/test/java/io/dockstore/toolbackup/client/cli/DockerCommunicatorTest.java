package io.dockstore.toolbackup.client.cli;

import org.junit.AfterClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class DockerCommunicatorTest extends Base {

    private static final DockerCommunicator DOCKER_COMMUNICATOR = new DockerCommunicator();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    @Test
    public void pullDockerImage() throws Exception {
        DOCKER_COMMUNICATOR.pullDockerImage(IMG);
    }

    @Test
    public void pullDockerImage_nonexistent() throws Exception {
        System.setErr(new PrintStream(errContent));
        DOCKER_COMMUNICATOR.pullDockerImage(NONEXISTING_IMG);
        assertTrue(errContent.toString().contains("Unable to pull"));
    }

    @Test
    public void saveDockerImage() throws Exception {
        DOCKER_COMMUNICATOR.saveDockerImage(IMG);
    }

    @Test (expected = RuntimeException.class)
    public void saveDockerImage_nonexistent() throws Exception {
        DOCKER_COMMUNICATOR.saveDockerImage(NONEXISTING_IMG);
    }

    @AfterClass
    public static void closeDocker() {
        DOCKER_COMMUNICATOR.closeDocker();
    }
}