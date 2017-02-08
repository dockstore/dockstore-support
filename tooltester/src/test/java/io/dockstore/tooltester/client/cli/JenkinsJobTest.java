package io.dockstore.tooltester.client.cli;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import static org.junit.Assert.assertTrue;

/**
 * @author gluu
 * @since 24/01/17
 */
public class JenkinsJobTest {
    /**
     * This tests if a parameter file test can be created and ran.
     * Also tests if the test results can be attained and the console output file can be generated
     */
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();
    private Client client;

    @Before
    public void initialize() {
        client = new Client();
        client.setupClientEnvironment();
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    @Test
    public void ParameterTestJobIT() {
        final String suffix = "id-tag-test.json";
        client.setupJenkins();
        Assert.assertTrue("Jenkins server can not be reached", client.getJenkins() != null);
        client.setupTesters();
        ParameterFileTester parameterFileTester = client.getParameterFileTester();

        parameterFileTester.createTest(suffix);

        Map<String, String> map = new HashMap<>();
        map.put("URL", "https://github.com/CancerCollaboratory/dockstore-tool-kallisto.git");
        map.put("Tag", "master");
        map.put("DescriptorPath", "Dockstore.cwl");
        map.put("ParameterPath", "test1.json");

        parameterFileTester.runTest(suffix, map);
        String status = parameterFileTester.getTestResults(suffix).get("status");
        assertTrue("Status is not SUCCESS: " + status != null);
        try {
            client.getJenkins().deleteJob("ParameterFileTest" + "-" + suffix, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void PipelineTestJobIT() {
        final String suffix = "id-tag";
        client.setupJenkins();
        Assert.assertTrue("Jenkins server can not be reached", client.getJenkins() != null);
        client.setupTesters();
        PipelineTester pipelineTester = client.getPipelineTester();
        pipelineTester.createTest(suffix);

        Map<String, String> map = new HashMap<>();
        map.put("URL", "https://github.com/CancerCollaboratory/dockstore-tool-kallisto.git");
        map.put("Tag", "master");
        map.put("DockerfilePath", "Dockerfile");
        map.put("DescriptorPath", "Dockstore.cwl Dockstore.cwl");
        map.put("ParameterPath", "test1.json test2.json");

        pipelineTester.runTest(suffix, map);
        String status = pipelineTester.getTestResults(suffix).get("status");
        assertTrue("Status is not SUCCESS: " + status != null);
        //        try {
        //            client.getJenkins().deleteJob("PipelineTest" + "-" + suffix, true);
        //        } catch (IOException e) {
        //            System.out.println("Could not delete Jenkins job");
        //        }
    }

    @Test
    public void DockerfileTestJobIT() {
        final String suffix = "id-tag-test.json";
        client.setupJenkins();
        Assert.assertTrue("Jenkins server can not be reached", client.getJenkins() != null);
        client.setupTesters();
        DockerfileTester dockerfileTester = client.getDockerfileTester();
        dockerfileTester.createTest(suffix);

        Map<String, String> map = new HashMap<>();
        map.put("URL", "https://github.com/CancerCollaboratory/dockstore-tool-kallisto.git");
        map.put("Tag", "master");
        map.put("DockerfilePath", "Dockerfile");

        dockerfileTester.runTest(suffix, map);
        String status = dockerfileTester.getTestResults(suffix).get("status");
        assertTrue("Status is not SUCCESS: " + status != null);
        try {
            client.getJenkins().deleteJob("DockerfileTest" + "-" + suffix, true);
        } catch (IOException e) {
            System.out.println("Could not delete Jenkins job");
        }
    }

    @Test
    public void getNonExistantTest() {
        exit.expectSystemExitWithStatus(10);
        client.setupJenkins();
        client.setupTesters();
        DockerfileTester dockerfileTester = client.getDockerfileTester();
        dockerfileTester.getTestResults("SuffixOfATestThatShouldNotExist");

    }
}