package io.dockstore.tooltester.client.cli;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 24/01/17
 */
public class JenkinsJobTest {
    private boolean development = false;
    /**
     * This tests if a parameter file test can be created and ran.
     * Also tests if the test results can be attained and the console output file can be generated
     */
    @Test
    public void ParameterTestJobIT(){
        if (development) {
            Client client = new Client(null);
            final String suffix = "id-tag-test.json";
            client.setupClientEnvironment();
            Assert.assertTrue("client API could not start", client.getContainersApi() != null);
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
            int count = 0;
            while (count < 60 && !parameterFileTester.getTestResults(suffix).equals("SUCCESS")) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                count++;
            }
            String status = parameterFileTester.getTestResults(suffix);
            assertTrue("Status is not SUCCESS: " + status, status.equals("SUCCESS"));
            String path = parameterFileTester.createConsoleOutputFile(suffix);
            try {
                client.getJenkins().deleteJob("ParameterFileTest" + "-" + suffix, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println(path);
        }
    }
}