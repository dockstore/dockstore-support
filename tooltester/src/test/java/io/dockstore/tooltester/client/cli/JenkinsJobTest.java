package io.dockstore.tooltester.client.cli;

<<<<<<< HEAD
import java.util.HashMap;
import java.util.Map;

import io.dockstore.tooltester.helper.PipelineTester;
=======
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

>>>>>>> d7e8c79... Feature/jenkins example (#5)
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
    public void PipelineTestJobIT() {
        final String suffix = "id-tag";
<<<<<<< HEAD
        client.setupTesters();
        Assert.assertTrue("Jenkins server can not be reached", client.getPipelineTester().getJenkins() != null);
=======
        client.setupJenkins();
        Assert.assertTrue("Jenkins server can not be reached", client.getJenkins() != null);
>>>>>>> d7e8c79... Feature/jenkins example (#5)
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
    public void getNonExistantTest() {
        exit.expectSystemExitWithStatus(10);
<<<<<<< HEAD
=======
        client.setupJenkins();
>>>>>>> d7e8c79... Feature/jenkins example (#5)
        client.setupTesters();
        PipelineTester pipelineTester = client.getPipelineTester();
        pipelineTester.getTestResults("SuffixOfATestThatShouldNotExist");
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> d7e8c79... Feature/jenkins example (#5)
