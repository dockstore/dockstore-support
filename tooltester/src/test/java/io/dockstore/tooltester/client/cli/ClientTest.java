package io.dockstore.tooltester.client.cli;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.offbytwo.jenkins.JenkinsServer;
import io.swagger.client.ApiException;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import static io.dockstore.tooltester.client.cli.Client.main;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.COMMAND_ERROR;

public class ClientTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();
    private Client client;

    @Before
    public void initialize() {
        this.client = new Client();
        this.client.setupClientEnvironment();
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    @Test
    public void setupEnvironment() throws Exception {
        client = new Client();
        client.setupClientEnvironment();
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    /**
     * Tests if Jenkins server is set up
     */
    @Test
    public void setupJenkins() {
        client.setupJenkins();
        Assert.assertTrue("Jenkins server can not be reached", client.getJenkins() != null);
    }

    /**
     * This test deletes all created Jenkins pipelines
     */
    @Test
    public void deleteJenkinsTests() {
        client.setupJenkins();
        JenkinsServer jenkins = client.getJenkins();
        Assert.assertTrue("Jenkins server can not be reached", jenkins != null);
        client.deleteJobs("DockerfileTest");
        client.deleteJobs("ParameterFileTest");
        client.deleteJobs("PipelineTest");
    }

    /**
     * Test with unknown command
     */
    @Test
    public void unknownCommand() {
        String[] argv = { "mmmrrrggglll" };
        exit.expectSystemExitWithStatus(COMMAND_ERROR);
        main(argv);
    }

    /**
     * Test enqueue with no parameters which should print the help usage
     */
    @Test
    public void enqueue() {
        String[] argv = { "enqueue" };
        main(argv);
    }

    /**
     * Test enqueue with no parameters which should print the help usage
     */
    @Test
    public void enqueueAll() {
        String[] argv = { "enqueue" , "--all"};
        main(argv);
    }

    /**
     * Test enqueue with default options
     */
    @Test
    public void enqueueTool() {
        String[] argv = { "enqueue", "--tool" , "quay.io/pancancer/pcawg_delly_workflow", "quay.io/pancancer/pcawg-dkfz-workflow"};
        main(argv);
    }

    /**
     * This tests the Jenkins pipeline creation
     */
    @Test
    public void createJenkinsTests() {
        String[] argv = { "--execution", "local", "--source", "Docktesters group", "--api", "https://www.dockstore.org:8443/api/ga4gh/v1" };
        main(argv);
    }

    /**
     * This tests the client with no parameters
     */
    @Test
    public void empty() {
        String[] argv = {""};
        main(argv);
    }

    /**
     * This attempts to test an invalid tool
     */
    @Test
    public void testInvalidTool() {
        exit.expectSystemExitWithStatus(API_ERROR);
        Tool tool = new Tool();
        client.testTool2(tool);
    }

    /**
     * This attempts to get the results of an invalid tool;
     */
    @Test
    public void getInvalidTool() {
        exit.expectSystemExitWithStatus(COMMAND_ERROR);
        Tool tool = new Tool();
        List<ToolVersion> versions = Arrays.asList(null, null);
        tool.setVersions(versions);
        client.getToolTestResults2(tool);
    }

    /**
     * This gets the report of all the tools
     */
    @Test
    public void report() {
        String[] argv = { "report" };
        main(argv);
    }

    /**
     * This reports on a specific tool
     */
    @Test
    public void getSpecificJenkinsTest() {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa-mem-workflow" };
        main(argv);
    }

    /**
     * This reports on specific tools
     */
    @Test
    public void getSpecificJenkinsTests() {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa-mem-workflow",
                "quay.io/pancancer/pcawg-dkfz-workflow" };
        main(argv);
    }

    @Test
    public void getSpecificInvalidJenkinsTest() {
        String[] argv = new String[] { "report", "--tool", "quay.io/pancancer/pcawg-bwa"};
        main(argv);
    }


    /**
     * This displays the help menu for the report command
     */
    @Test
    public void reportHelp() {
        String[] argv = { "report", "--help" };
        main(argv);
    }

    /**
     * This displays the help menu for the main command
     */
    @Test
    public void mainHelp() {
        String[] argv = { "--help" };
        main(argv);
    }

    /**
     * Gets all the file combinations with any verified source.
     */
    @Test
    public void getVerifiedToolsTest() {
        int count;
        List<Tool> verifiedTools = client.getVerifiedTools();
        for (Tool verifiedTool : verifiedTools) {
            try {
                client.countNumberOfTests(verifiedTool);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files", count == 5);
    }

    /**
     * Gets all the file combinations with specified sources.
     */
    @Test
    public void getVerifiedToolsWithFilterTest() {
        int count;
        List<String> verifiedSources = Collections.singletonList("Docktesters group");
        List<Tool> verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            try {
                client.countNumberOfTests(verifiedTool);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files. Got " + count, count == 5);
        client.setCount(0);
        verifiedSources = Arrays.asList("Docktesters group", "Another Group");
        verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            try {
                client.countNumberOfTests(verifiedTool);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files. Got " + count, count == 5);
        client.setCount(0);
        verifiedSources = Collections.singletonList("Another Group");
        verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            try {
                client.countNumberOfTests(verifiedTool);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files. Got " + count, count == 0);
    }

}