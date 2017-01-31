package io.dockstore.tooltester.client.cli;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.offbytwo.jenkins.JenkinsServer;
import io.swagger.client.ApiException;
import io.swagger.client.model.Tool;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static io.dockstore.tooltester.client.cli.Client.main;

public class ClientTest {
    private boolean development;
    private Client client;

    @Before
    public void initialize() {
        this.client = new Client();
        this.client.setupClientEnvironment();
        development = this.client.development;
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    @Test
    public void setupEnvironment() throws Exception {
        client = new Client();
        client.setupClientEnvironment();
        development = client.development;
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    /**
     * Tests if Jenkins server is set up
     */
    @Test
    public void setupJenkins() {
        if (development) {
            client.setupJenkins();
            Assert.assertTrue("Jenkins server can not be reached", client.getJenkins() != null);
        }
    }

    /**
     * This test deletes all created Jenkins pipelines
     */
    @Test
    public void deleteJenkinsTests() {
        if (development) {
            client.setupJenkins();
            JenkinsServer jenkins = client.getJenkins();
            Assert.assertTrue("Jenkins server can not be reached", jenkins != null);
            client.deleteJobs("DockerfileTest");
            client.deleteJobs("ParameterFileTest");
        }
    }

    /**
     * Creates the pipelines on Jenkins to test dockerfiles and parameter files
     */
    @Test
    public void createJenkinsTests() {
        if (development) {
            client.setupJenkins();
            client.setupTesters();
            JenkinsServer jenkins = client.getJenkins();
            Assert.assertTrue("Jenkins server can not be reached", jenkins != null);
            List<Tool> tools = client.getVerifiedTools();
            for (Tool tool : tools) {
                client.createToolTests(tool);
            }
        }
    }

    /**
     * This runs all the tool's dockerfiles
     */
    private void runJenkinsTests() {
        if (development) {
            client.setupJenkins();
            client.setupTesters();
            JenkinsServer jenkins = client.getJenkins();
            Assert.assertTrue("Jenkins server can not be reached", jenkins != null);
            List<Tool> tools = client.getVerifiedTools();
            for (Tool tool : tools) {
                client.testTool(tool);
            }
        }
    }

    /**
     * Creates the pipelines on Jenkins to test dockerfiles and parameter files
     */
    @Test
    public void createAndrunJenkinsTests() {
        if (development) {
            String[] argv = { "--execution", "local", "--source", "docktesters", "--api", "https://www.dockstore.org:8443/api/ga4gh/v1" };
            main(argv);
            runJenkinsTests();
        }
    }

    /**
     * This gets the report of all the tools
     */
    @Test
    public void getJenkinsTests() {
        if (development) {
            String[] argv = { "report" };
            main(argv);
        }
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