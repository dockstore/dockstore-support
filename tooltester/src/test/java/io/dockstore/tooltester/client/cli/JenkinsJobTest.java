package io.dockstore.tooltester.client.cli;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author gluu
 * @since 24/01/17
 */
public class JenkinsJobTest {
    private boolean development = false;
    private Client client;

    /**
     * This tests if a parameter file test can be created and ran.
     * Also tests if the test results can be attained and the console output file can be generated
     */

    @Before
    public void initialize() {
        client = new Client();
        client.setupClientEnvironment();
        development = client.development;
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    @Test
    public void ParameterTestJobIT() {
        if (development) {
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
            assertTrue("Status is not SUCCESS: " + status, status.equals("SUCCESS") || status.equals("Building") || status.equals("NOT_BUILT"));
            try {
                client.getJenkins().deleteJob("ParameterFileTest" + "-" + suffix, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void DockerfileTestJobIT() {
        if (development) {
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
            assertTrue("Status is not SUCCESS: " + status, status.equals("SUCCESS") || status.equals("Building") || status.equals("NOT_BUILT"));
            try {
                client.getJenkins().deleteJob("DockerfileTest" + "-" + suffix, true);
            } catch (IOException e) {
                System.out.println("Could not delete Jenkins job");
            }
        }
    }
}