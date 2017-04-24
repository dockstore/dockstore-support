package io.dockstore.toolbackup.client.cli;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.DockerRequestException;

import java.io.IOException;
import java.io.InputStream;

import static com.spotify.docker.client.DefaultDockerClient.fromEnv;
import static io.dockstore.toolbackup.client.cli.Client.CONNECTION_ERROR;
import static io.dockstore.toolbackup.client.cli.Client.GENERIC_ERROR;
import static io.dockstore.toolbackup.client.cli.Client.IO_ERROR;
import static java.lang.System.err;
import static java.lang.System.out;

/**
 * Created by kcao on 18/01/17.
 */
class DockerCommunicator {
    private DockerClient dockerClient;

    DockerCommunicator() {
        try {
            dockerClient = fromEnv().build();
        } catch (DockerCertificateException e) {
            ErrorExit.exceptionMessage(e, "Ensure that you have docker installed properly in your environment", CONNECTION_ERROR);
        }
    }

    long getImageSize(String img) {
        try {
            return dockerClient.inspectImage(img).size();
        } catch (DockerException e) {
            throw new RuntimeException("Could not inspect: " + img + " because of docker");
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not inspect: " + img + " as it was interrupted");
        }
    }

    boolean pullDockerImage(String img) {
        boolean pulled = true;
        try {
            dockerClient.pull(img);
            out.println("Pulled image: " + img);
        } catch (DockerException e) {
            pulled = false;
            err.println("Unable to pull: " + img + ". It might not exist.");
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not copy: " + img + " to file");
        }
        return pulled;
    }

    InputStream saveDockerImage(String img) {
        InputStream inputStream = null;
        try {
            inputStream = dockerClient.save(img);
        } catch (IOException e) {
            ErrorExit.exceptionMessage(e, "Could not save: " + img, IO_ERROR);
        } catch (DockerRequestException e) {
            throw new RuntimeException("Make sure you have pulled " + img);
        } catch (DockerException e) {
            ErrorExit.exceptionMessage(e, "Could not save: " + img + " because of Docker client", GENERIC_ERROR);
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not save: " + img + " in its entirety");
        }
        return  inputStream;
    }

    void removeDockerImage(String img) {
        try {
            dockerClient.removeImage(img);
        } catch (DockerException e) {
            throw new RuntimeException("Could not remove: " + img + " because of docker");
        } catch (InterruptedException e) {
            throw new RuntimeException("Program was interrupted while removing: " + img);
        }
    }

    void closeDocker() {
        dockerClient.close();
    }
}
