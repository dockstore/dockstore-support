package io.dockstore.tooltester.client.cli;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.offbytwo.jenkins.JenkinsServer;
import io.swagger.client.model.Tool;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.junit.Assert;
import org.junit.Test;

public class ClientTest {
    private boolean development = true;

    @Test
    public void setupEnvironment() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        client.setupClientEnvironment();
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

    /**
     * This test deletes all Dockerfile related jobs
     */
    @Test
    public void deleteTestDockerfile() {
        if (development) {
            OptionParser parser = new OptionParser();
            final OptionSet parsed = parser.parse("");
            Client client = new Client(parsed);
            client.setupClientEnvironment();
            Assert.assertTrue("client API could not start", client.getContainersApi() != null);
            client.setupJenkins();
            JenkinsServer jenkins = client.getJenkins();
            Assert.assertTrue("Jenkins server can not be reached", jenkins != null);
            client.deleteJobs("DockerfileTest");
            client.deleteJobs("ParameterFileTest");
        }
    }


    /**
     * Tests if Jenkins server is set up
     */
    @Test
    public void setupJenkins(){
        if (development) {
            OptionParser parser = new OptionParser();
            final OptionSet parsed = parser.parse("");
            Client client = new Client(parsed);
            client.setupClientEnvironment();
            Assert.assertTrue("client API could not start", client.getContainersApi() != null);
            client.setupJenkins();
            Assert.assertTrue("Jenkins server can not be reached", client.getJenkins() != null);

        }
    }

    /**
     * Creates the pipelines on Jenkins to test dockerfiles and parameter files
     */
    @Test
    public void createTests(){
        if (development) {
            OptionParser parser = new OptionParser();
            final OptionSet parsed = parser.parse("");
            Client client = new Client(parsed);
            client.setupClientEnvironment();
            Assert.assertTrue("client API could not start", client.getContainersApi() != null);
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
    @Test
    public void testDockerfile() {
        if (development) {
            OptionParser parser = new OptionParser();
            final OptionSet parsed = parser.parse("");
            Client client = new Client(parsed);
            client.setupClientEnvironment();
            Assert.assertTrue("client API could not start", client.getContainersApi() != null);
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
     * This runs all the tool's dockerfiles
     */
    @Test
    public void getTestResults() {
        if (development) {
            OptionParser parser = new OptionParser();
            final OptionSet parsed = parser.parse("");
            Client client = new Client(parsed);
            client.setupClientEnvironment();
            Assert.assertTrue("client API could not start", client.getContainersApi() != null);
            client.setupJenkins();
            client.setupTesters();
            JenkinsServer jenkins = client.getJenkins();
            Assert.assertTrue("Jenkins server can not be reached", jenkins != null);
            List<Tool> tools = client.getVerifiedTools();
            for (Tool tool : tools) {
                client.getToolTestResults(tool);
            }
        }
    }

    /**
     * Gets all the file combinations with any verified source.
     * @throws Exception
     */
    @Test
    public void getVerifiedToolsTest() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        client.setupClientEnvironment();
        int count;
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
        List<Tool> verifiedTools = client.getVerifiedTools();
        for (Tool verifiedTool : verifiedTools) {
            client.printAllFilesFromTool(verifiedTool);
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files", count == 5);
    }

    /**
     * Gets all the file combinations with specified sources.
     * @throws Exception
     */
    @Test
    public void getVerifiedToolsWithFilterTest() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        client.setupClientEnvironment();
        int count;
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
        List<String> verifiedSources = Arrays.asList("Docktesters group");
        List<Tool> verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            client.printAllFilesFromTool(verifiedTool);
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files. Got "+ count, count == 5);
        client.setCount(0);
        verifiedSources = Arrays.asList("Docktesters group", "Another Group");
        verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            client.printAllFilesFromTool(verifiedTool);
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files Got. "+ count, count == 5);
        client.setCount(0);
        verifiedSources = Arrays.asList("Another Group");
        verifiedTools = client.getVerifiedTools(verifiedSources);
        for (Tool verifiedTool : verifiedTools) {
            client.printAllFilesFromTool(verifiedTool);
        }
        count = client.getCount();
        Assert.assertTrue("There is an incorrect number of dockerfile, descriptors, and test parameter files Got. "+ count, count == 0);
    }


}