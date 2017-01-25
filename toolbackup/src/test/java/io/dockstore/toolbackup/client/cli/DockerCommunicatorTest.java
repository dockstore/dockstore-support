package io.dockstore.toolbackup.client.cli;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class DockerCommunicatorTest extends Base {

    private final DockerCommunicator dockerCommunicator = new DockerCommunicator();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    @Test
    public void pullDockerImage() throws Exception {
        dockerCommunicator.pullDockerImage(IMG);
    }

    @Test
    public void pullDockerImage_nonexistent() throws Exception {
        System.setErr(new PrintStream(errContent));
        dockerCommunicator.pullDockerImage(NONEXISTING_IMG);
        assertTrue(errContent.toString().contains("Unable to pull"));
    }

    @Test
    public void saveDockerImage() throws Exception {
        dockerCommunicator.saveDockerImage(IMG);
    }

    @Test (expected = RuntimeException.class)
    public void saveDockerImage_nonexistent() throws Exception {
        dockerCommunicator.saveDockerImage(NONEXISTING_IMG);
    }

}