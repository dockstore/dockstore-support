/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.tooltester.client.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.helper.JenkinsVersion;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.Job;
import io.dockstore.tooltester.helper.PipelineTester;
import io.dockstore.tooltester.helper.TimeHelper;
import io.dockstore.tooltester.jenkins.CrumbJsonResult;
import io.dockstore.tooltester.jenkins.JenkinsLog;
import io.dockstore.tooltester.jenkins.JenkinsPipeline;
import io.dockstore.tooltester.jenkins.Node;
import io.dockstore.tooltester.jenkins.OutputFile;
import io.dockstore.tooltester.jenkins.Stage;
import io.dockstore.tooltester.jenkins.StageFlowNode;
import io.dockstore.tooltester.report.FileReport;
import io.dockstore.tooltester.report.StatusReport;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.GAGHApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import static io.dockstore.tooltester.helper.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.CLIENT_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.DEBUG;
import static io.dockstore.tooltester.helper.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.errorMessage;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;

/**
 * Prototype for testing service
 */
public class Client {
    private static final Logger LOG;

    static {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        LOG = LoggerFactory.getLogger(Client.class);
    }

    private ContainersApi containersApi;
    private GAGHApi ga4ghApi;
    private StatusReport report;
    private FileReport fileReport;
    private int count = 0;
    private HierarchicalINIConfiguration config;
    private PipelineTester pipelineTester;
    private JenkinsServer jenkins;

    Client() {

    }

    /**
     * Main method
     *
     * @param argv Command line arguments
     */
    public static void main(String[] argv) {
        Client client = new Client();
        CommandMain cm = new CommandMain();
        JCommander jc = new JCommander(cm);
        jc.setProgramName("autotool");
        CommandReport commandReport = new CommandReport();
        CommandEnqueue commandEnqueue = new CommandEnqueue();
        CommandFileReport commandFileReport = new CommandFileReport();
        jc.addCommand("report", commandReport);
        jc.addCommand("enqueue", commandEnqueue);
        jc.addCommand("file-report", commandFileReport);
        try {
            jc.parse(argv);
        } catch (MissingCommandException e) {
            jc.usage();
            exceptionMessage(e, "Unknown command", COMMAND_ERROR);
        }
        if (jc.getParsedCommand() != null) {
            switch (jc.getParsedCommand()) {
            case "report":
                if (commandReport.help) {
                    jc.usage("report");
                } else {
                    client.handleReport(commandReport.tools);
                }
                break;
            case "enqueue":
                if (commandEnqueue.help) {
                    jc.usage("enqueue");
                } else {
                    if (commandEnqueue.all) {
                        client.handleRunTests(commandEnqueue.tools);
                    } else {

                        if (!commandEnqueue.tools.isEmpty()) {
                            client.handleRunTests(commandEnqueue.tools);
                        } else {
                            jc.usage();
                        }
                    }
                }
                break;
            case "file-report":
                if (commandFileReport.help) {
                    jc.usage("file-report");
                } else {
                    client.handleFileReport(commandFileReport.tool);
                }
                break;
            default:
                jc.usage();
            }
        } else {
            if (cm.help) {
                jc.usage();
            } else {
                client.handleCreateTests(cm.api, cm.source, cm.execution);
            }
        }

    }

    private void handleFileReport(String toolName) {
        setupClientEnvironment();
        setupJenkins();
        setupTesters();
        createFileReport("FileReport.csv");
        DockstoreTool dockstoreTool = null;
        try {
            dockstoreTool = containersApi.getPublishedContainerByToolPath(toolName);
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get container: " + toolName, API_ERROR);
        }
        assert dockstoreTool != null;
        List<Tag> tags = dockstoreTool.getTags();
        for (Tag tag : tags) {
            String name = dockstoreTool.getPath();
            name = name.replaceAll("/", "-");
            name = name + "-" + tag.getName();
            List<Build> builds = pipelineTester.getAllBuilds(name);
            if (builds == null) {
                continue;
            }
            for (Build build : builds) {
                int buildId = build.getNumber();
                try {
                    List<Artifact> artifactList = build.details().getArtifacts();
                    for (Artifact artifact : artifactList) {
                        try {
                            InputStream inputStream = build.details().downloadArtifact(artifact);
                            String artifactString = IOUtils.toString(inputStream, "UTF-8");
                            Gson gson = new Gson();
                            Type mapType = new TypeToken<Map<String, OutputFile>>() {
                            }.getType();
                            Map<String, OutputFile> outputFiles = gson.fromJson(artifactString, mapType);
                            outputFiles.entrySet().parallelStream().forEach(file -> {
                                String basename = file.getKey();
                                String checksum = file.getValue().getChecksum();
                                String size = file.getValue().getSize().toString();
                                List<String> record = Arrays.asList(String.valueOf(buildId), tag.getName(), basename, checksum, size);
                                fileReport.printAndWriteLine(record);
                            });
                        } catch (URISyntaxException e) {
                            exceptionMessage(e, "Could not download artifact", GENERIC_ERROR);
                        }
                    }
                } catch (IOException e) {
                    exceptionMessage(e, "Could not get artifacts", IO_ERROR);
                }
            }
        }
        finalizeFileReport();
    }

    PipelineTester getPipelineTester() {
        return pipelineTester;
    }

    /**
     * This function handles the report command
     *
     * @param toolNames The tools passed in as arguments from the commmand line
     */
    private void handleReport(List<String> toolNames) {
        try {
            setupClientEnvironment();
            setupJenkins();
            setupTesters();
            List<Tool> tools = getVerifiedTools();
            createResults("Report.csv");
            if (!toolNames.isEmpty()) {
                tools = tools.parallelStream().filter(t -> toolNames.contains(t.getId())).collect(Collectors.toList());
            }
            for (Tool tool : tools) {
                getToolTestResults(tool);
            }
            finalizeResults();
        } catch (Exception e) {
            exceptionMessage(e, "Can't handle report", GENERIC_ERROR);
        }
    }

    /**
     * This function gets the jenkins crumb in the event that the java jenkins api does not work
     *
     * @return The crumb string
     */
    private String getJenkinsCrumb() {
        String username = config.getString("jenkins-username", "travis");
        String password = config.getString("jenkins-password", "travis");
        String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
        HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);

        javax.ws.rs.client.Client client = ClientBuilder.newClient();
        client.register(feature);
        String entity = client.target(serverUrl).path("crumbIssuer/api/json").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
        Gson gson = new Gson();
        CrumbJsonResult result = gson.fromJson(entity, CrumbJsonResult.class);
        return result.crumb;
    }

    private JenkinsPipeline getJenkinsPipeline(String name, int buildId) {
        JenkinsPipeline jenkinsPipeline = null;
        try {
            String crumb = getJenkinsCrumb();
            String username = config.getString("jenkins-username", "travis");
            String password = config.getString("jenkins-password", "travis");
            String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
            HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);
            javax.ws.rs.client.Client client = ClientBuilder.newClient().register(feature);
            String entity = client.target(serverUrl).path("job/" + name + "/" + buildId + "/wfapi/describe")
                    .request(MediaType.TEXT_PLAIN_TYPE).header("crumbRequestField", crumb).get(String.class);
            Gson gson = new Gson();
            jenkinsPipeline = gson.fromJson(entity, JenkinsPipeline.class);
        } catch (Exception e) {
            LOG.warn("Could not get Jenkins build for: " + name);
        }
        return jenkinsPipeline;
    }

    /**
     * Deletes all the DockerfileTest and ParameterFileTest jobs and creates all the tests again
     *
     * @param api       api to pull the tools from
     * @param source    the testing group that verified the tools
     * @param execution the location to test the tools
     */
    private void handleCreateTests(String api, List<String> source, String execution) {
        if (execution != "jenkins") {
            errorMessage("Can only execute on jenkins, no other location is currently supported", COMMAND_ERROR);
        }
        if (api != "https://www.dockstore.org:8443/api/ga4gh/v1") {
            errorMessage("Can only use https://www.dockstore.org:8443/api/ga4gh/v1, no other api is currently supported", COMMAND_ERROR);
        }
        setupClientEnvironment();
        setupJenkins();
        setupTesters();
        List<Tool> tools;
        if (!source.isEmpty()) {
            tools = getVerifiedTools(source);
        } else {
            tools = getVerifiedTools();
        }
        for (Tool tool : tools) {
            createToolTests(tool);
        }
    }

    private void handleRunTests(List<String> toolNames) {
        setupClientEnvironment();
        setupJenkins();
        setupTesters();
        List<Tool> tools = getVerifiedTools();
        if (!toolNames.isEmpty()) {
            tools = tools.parallelStream().filter(t -> toolNames.contains(t.getId())).collect(Collectors.toList());
        }
        for (Tool tool : tools) {
            testTool(tool);
        }
    }

    public JenkinsServer getJenkins() {
        return jenkins;
    }

    private void setJenkins(JenkinsServer jenkins) {
        this.jenkins = jenkins;
    }

    int getCount() {
        return count;
    }

    void setCount(int count) {
        this.count = count;
    }

    void setupTesters() {
        pipelineTester = new PipelineTester(getJenkins());
    }

    void setupJenkins() {
        try {
            String serverUrl;
            String username;
            String password;
            serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
            username = config.getString("jenkins-username", "travis");
            password = config.getString("jenkins-password", "travis");
            setJenkins(new JenkinsServer(new URI(serverUrl), username, password));
            Map<String, Job> jobs = jenkins.getJobs();
            JenkinsVersion version = jenkins.getVersion();
            LOG.trace("Jenkins is version " + version.getLiteralVersion() + " and has " + jobs.size() + " jobs");
        } catch (URISyntaxException e) {
            exceptionMessage(e, "Jenkins server URI is not valid", CLIENT_ERROR);
        } catch (IOException e) {
            exceptionMessage(e, "Could not connect to Jenkins server", IO_ERROR);
        }
    }

    void setupClientEnvironment() {
        String userHome = System.getProperty("user.home");
        try {
            // This is the config file for production use.
            // If there's no configuration file, all properties will default to Travis ones.
            File configFile = new File(userHome + File.separator + ".tooltester" + File.separator + "config");
            this.config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            exceptionMessage(e, "", API_ERROR);
        }

        // pull out the variables from the config if it exists
        String token = config.getString("token", "");
        String serverUrl = config.getString("server-url", "https://www.dockstore.org:8443");

        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();
        defaultApiClient.addDefaultHeader("Authorization", "Bearer " + token);
        defaultApiClient.setBasePath(serverUrl);

        this.containersApi = new ContainersApi(defaultApiClient);
        UsersApi usersApi = new UsersApi(defaultApiClient);
        setGa4ghApi(new GAGHApi(defaultApiClient));

        boolean isAdmin = false;
        try {
            if (usersApi.getApiClient() != null) {
                isAdmin = usersApi.getUser().getIsAdmin();
            }
        } catch (ApiException e) {
            //exceptionMessage(e, "Could not use user API", API_ERROR);
            LOG.warn("Could not use user API.  Admin is false by default");
            isAdmin = false;
        }
        defaultApiClient.setDebugging(DEBUG.get());
    }

    /**
     * Finalize the results of testing
     */
    private void finalizeResults() {
        report.close();
    }

    private void finalizeFileReport() {
        fileReport.close();
    }

    public void md5sumChallenge() {
        setupClientEnvironment();
        setupJenkins();
        setupTesters();
        List<Tool> verifiedTools = null;
        GAGHApi ga4ghApi = getGa4ghApi();
        List<Tool> tools = null;
        try {
            tools = ga4ghApi.toolsGet("quay.io/briandoconnor/dockstore-tool-md5sum", null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            exceptionMessage(e, "", API_ERROR);
        }
        for (Tool tool : tools) {
            createToolTests(tool);
            testTool(tool);
        }
    }

    private void createResults(String name) {
        report = new StatusReport(name);
    }

    private void createFileReport(String name) {
        fileReport = new FileReport(name);
    }

    /**
     * do the actual work here
     */
    //    private void run() {
    //        setupClientEnvironment();
    //        setupJenkins();
    //        boolean toolTestResult = false;
    //        /** use swagger-generated classes to talk to dockstore */
    //        try {
    //            final List<Tool> tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);
    //            System.out.println("Number of tools on Dockstore: " + tools.size());
    //            LongStream longStream = tools.parallelStream().filter(Tool::getVerified)
    //                    .mapToLong(tool -> tool.getVersions().parallelStream().filter(ToolVersion::getVerified).count());
    //            System.out.println("Number of versions of tools to test on Dockstore (currently): " + longStream.sum());
    //            //            toolTestResult = tools.parallelStream().filter(Tool::getVerified).map(this::testTool).reduce(true, Boolean::logicalAnd);
    //            //            System.out.println("Successful \"testing\" of tools found on Dockstore: " + toolTestResult);
    //        } catch (ApiException e) {
    //            exceptionMessage(e, "", API_ERROR);
    //        }
    //    }

    /**
     * This function counts the number of tests that need to be created
     *
     * @param verifiedTool The verified tool
     */
    void countNumberOfTests(Tool verifiedTool) throws ApiException {
        SourceFile dockerfile;
        SourceFile descriptor;
        SourceFile testParameter;
        List<SourceFile> testParameterFiles;
        DockstoreTool dockstoreTool;
        Long containerId;
        dockstoreTool = containersApi.getPublishedContainerByToolPath(verifiedTool.getId());
        containerId = dockstoreTool.getId();
        for (ToolVersion version : verifiedTool.getVersions()) {
            String tag = version.getName();
            dockerfile = containersApi.dockerfile(containerId, tag);
            for (ToolVersion.DescriptorTypeEnum descriptorType : version.getDescriptorType()) {
                switch (descriptorType.toString()) {
                case "CWL":
                    descriptor = containersApi.cwl(containerId, tag);
                    break;
                case "WDL":
                    descriptor = containersApi.wdl(containerId, tag);
                    break;
                default:
                    break;
                }
                testParameterFiles = containersApi.getTestParameterFiles(containerId, tag, descriptorType.toString());
                for (SourceFile testParameterFile : testParameterFiles) {
                    testParameter = testParameterFile;
                    count++;
                }
            }
        }
    }

    /**
     * Creates the pipeline(s) on Jenkins to test a tool
     *
     * @param tool The tool to create tests for
     */
    private void createToolTests(Tool tool) {
        List<ToolVersion> toolVersions = tool.getVersions();
        for (ToolVersion toolversion : toolVersions) {
            String name = toolversion.getId();
            name = name.replace("/", "-");
            name = name.replace(":", "-");
            pipelineTester.createTest(name);
        }
    }

    /**
     * Creates the pipeline(s) on Jenkins to test a tool
     *
     * @param tool The tool to get the test results for
     */
    private void getToolTestResults(Tool tool) {
        List<ToolVersion> toolVersions = tool.getVersions();
        for (ToolVersion toolversion : toolVersions) {
            if (toolversion != null) {
                String id = toolversion.getId();
                String tag = toolversion.getName();

                String suffix = id;
                suffix = suffix.replace("/", "-");
                suffix = suffix.replace(":", "-");
                if (pipelineTester.getJenkinsJob(suffix) == null) {
                    LOG.info("Could not get job: " + suffix);
                } else {
                    int buildId = pipelineTester.getLastBuildId(suffix);
                    if (buildId == 0 || buildId == -1) {
                        LOG.info("No build was ran");
                        continue;
                    }
                    String name = "PipelineTest" + "-" + suffix;
                    JenkinsPipeline jenkinsPipeline = getJenkinsPipeline(name, buildId);
                    List<Stage> stages = jenkinsPipeline.getStages();

                    for (Stage stage : stages) {

                        String status = stage.getStatus();

                        // Jenkins reports the wrong status and duration for in-progress stages, return the total job info instead
                        Long runtime = stage.getDurationMillis();
                        if (stage.getDurationMillis() <= 0) {
                            status = jenkinsPipeline.getStatus();
                            runtime = jenkinsPipeline.getDurationMillis();
                        }
                        if (status.equals("FAILED")) {

                            status += " See " + getLog(stage);
                        }
                        LocalDateTime date = LocalDateTime
                                .ofInstant(Instant.ofEpochMilli(stage.getStartTimeMillis()), ZoneId.systemDefault());
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm");
                        String formatDateTime = date.format(formatter);

                        List<String> record = Arrays.asList(toolversion.getId(), formatDateTime, tag, "Jenkins", stage.getName(),
                                TimeHelper.durationToString(runtime), status);
                        report.printAndWriteLine(record);
                    }
                }
            } else {
                errorMessage("Tool version is null", COMMAND_ERROR);
            }
        }
    }

    private String getLog(Stage stage) {
        String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
        String entity = getEntity(stage.getLinks().getSelf().getHref());
        Gson gson = new Gson();
        Node node = gson.fromJson(entity, Node.class);
        List<StageFlowNode> stageFlowNodes = node.getStageFlowNodes();
        for (StageFlowNode stageFlowNode : stageFlowNodes) {
            if (stageFlowNode.getStatus().equals("FAILED")) {
                try {
                    entity = getEntity(stageFlowNode.getLinks().getLog().getHref());
                } catch (Exception e) {
                    return node.getError().getMessage();
                }
                break;
            }
        }
        JenkinsLog jenkinsLog = gson.fromJson(entity, JenkinsLog.class);
        return serverUrl + jenkinsLog.getConsoleUrl().replaceFirst("^/", "");
    }

    private String getEntity(String uri) {
        String entity = null;
        try {
            String crumb = getJenkinsCrumb();

            // The configuration file is only used for production.  Otherwise it defaults to the travis ones.
            String username = config.getString("jenkins-username", "travis");
            String password = config.getString("jenkins-password", "travis");
            String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
            HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);
            javax.ws.rs.client.Client client = ClientBuilder.newClient().register(feature);
            entity = client.target(serverUrl).path(uri).request(MediaType.TEXT_PLAIN_TYPE).header("crumbRequestField", crumb)
                    .get(String.class);
        } catch (Exception e) {
            LOG.warn("Could not get Jenkins stage: " + uri);
        }
        return entity;
    }

    /**
     * This function deletes all jobs on jenkins matching "Test.*"
     */
    void deleteJobs(String pattern) {
        try {
            Map<String, Job> jobs = jenkins.getJobs();
            jobs.entrySet().stream().filter(map -> map.getKey().matches(pattern + ".+")).forEach(map -> {
                try {

                    jenkins.deleteJob(map.getKey(), true);
                } catch (IOException e) {
                    exceptionMessage(e, "Could not delete Jenkins job", IO_ERROR);
                }
            });
        } catch (IOException e) {
            exceptionMessage(e, "Could not find and delete Jenkins job", IO_ERROR);
        }
    }

    /**
     * This function checks if the any of the tool's verified source matches the filter
     *
     * @param filter          The list of verified sources that we're interested in
     * @param verifiedSources The tool version's verified sources
     * @return True if the one of the verified sources matches the filter
     */
    private boolean matchVerifiedSource(List<String> filter, String verifiedSources) {
        return filter.stream().anyMatch(str -> str.trim().equals(verifiedSources));

    }

    /**
     * Gets the list of verified tools
     *
     * @return The list of verified tools
     */
    List<Tool> getVerifiedTools() {
        List<Tool> verifiedTools = null;
        GAGHApi ga4ghApi = getGa4ghApi();
        try {
            final List<Tool> tools = ga4ghApi.toolsGet(null, null, null, null, null, null, null, null, null);
            verifiedTools = tools.parallelStream().filter(Tool::getVerified).collect(Collectors.toList());
            for (Tool tool : verifiedTools) {
                tool.setVersions(tool.getVersions().parallelStream().filter(ToolVersion::getVerified).collect(Collectors.toList()));
            }
        } catch (ApiException e) {
            exceptionMessage(e, "", API_ERROR);
        }
        return verifiedTools;
    }

    /**
     * Gets the list of verified tools and applies filter to it
     *
     * @param verifiedSources Filter parameter to filter the verified sources
     * @return The list of verified tools
     */
    List<Tool> getVerifiedTools(List<String> verifiedSources) {
        List<Tool> verifiedTools = getVerifiedTools();
        for (Tool tool : verifiedTools) {
            tool.setVersions(tool.getVersions().parallelStream().filter(p -> matchVerifiedSource(verifiedSources, p.getVerifiedSource()))
                    .collect(Collectors.toList()));
        }
        return verifiedTools;
    }

    /**
     * test the tool by
     * 1) Running available parameter files
     * 2) Rebuilding docker images
     * 3) Storing results for each tool as it finishes
     * <p>
     * in the early phases of the project, we can try to run these locally sequentially
     * however, due to system requirements, it will quickly become necessary to hook this up to
     * either a fixed network of slaves or Consonance on-demand hosts
     *
     * @param tool The tool to test
     * @return boolean  Returns true
     */
    private boolean testTool(Tool tool) {
        DockstoreTool dockstoreTool = null;
        try {
            dockstoreTool = containersApi.getPublishedContainerByToolPath(tool.getId());
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get published containers using the container API", API_ERROR);
        }
        Map<String, String> parameter = new HashMap<>();
        List<ToolVersion> toolVersions = tool.getVersions();
        for (ToolVersion toolversion : toolVersions) {
            String url = dockstoreTool != null ? dockstoreTool.getGitUrl() : null;
            url = url != null ? url.replace("git@github.com:", "https://github.com/") : null;
            parameter.put("URL", url);

            String dockerfilePath = null;
            assert dockstoreTool != null;
            Long containerId = dockstoreTool.getId();
            String id = toolversion.getId();
            String tagName = toolversion.getName();
            String referenceName = tagName;
            List<Tag> tags = dockstoreTool.getTags();
            for (Tag tag : tags) {
                if (tag.getName().equals(tagName)) {
                    referenceName = tag.getReference();
                    dockerfilePath = tag.getDockerfilePath();
                }
            }

            assert dockerfilePath != null;
            dockerfilePath = dockerfilePath.replaceFirst("^/", "");
            String name = id;
            name = name.replace("/", "-");
            name = name.replace(":", "-");
            parameter.put("Tag", referenceName);

            parameter.put("DockerfilePath", dockerfilePath);
            StringBuilder descriptorStringBuilder = new StringBuilder();
            StringBuilder parameterStringBuilder = new StringBuilder();
            try {
                List<SourceFile> testParameterFiles;
                SourceFile descriptor;
                for (ToolVersion.DescriptorTypeEnum descriptorType : toolversion.getDescriptorType()) {
                    switch (descriptorType.toString()) {
                    case "CWL":
                        descriptor = containersApi.cwl(containerId, tagName);

                        break;
                    case "WDL":
                        descriptor = containersApi.wdl(containerId, tagName);
                        break;
                    default:
                        LOG.info("Unknown descriptor, skipping");
                        continue;
                    }
                    String descriptorPath = descriptor.getPath().replaceFirst("^/", "");
                    testParameterFiles = containersApi.getTestParameterFiles(containerId, tagName, descriptorType.toString());
                    for (SourceFile testParameterFile : testParameterFiles) {
                        String parameterPath = testParameterFile.getPath();
                        parameterPath = parameterPath.replaceFirst("^/", "");
                        if (!descriptorStringBuilder.toString().equals("")) {
                            descriptorStringBuilder.append(" ");
                            parameterStringBuilder.append(" ");

                        }
                        descriptorStringBuilder.append(descriptorPath);
                        parameterStringBuilder.append(parameterPath);
                    }

                }
            } catch (ApiException e) {
                exceptionMessage(e, "Could not get cwl or wdl and test parameter files using the container API", API_ERROR);
            }
            parameter.put("ParameterPath", parameterStringBuilder.toString());
            parameter.put("DescriptorPath", descriptorStringBuilder.toString());
            if (!pipelineTester.isRunning(name)) {
                pipelineTester.runTest(name, parameter);
            } else {
                LOG.info("Job " + name + " is already running");
            }

        }
        return true;
    }

    public ContainersApi getContainersApi() {
        return containersApi;
    }

    public GAGHApi getGa4ghApi() {
        return ga4ghApi;
    }

    public void setGa4ghApi(GAGHApi ga4ghApi) {
        this.ga4ghApi = ga4ghApi;
    }

    @Parameters(separators = "=", commandDescription = "Report status of tools tested")
    private static class CommandReport {
        @Parameter(names = "--all", description = "Whether to report all tools or not")
        private Boolean all = false;
        @Parameter(names = "--tool", description = "The specific tools to report", variableArity = true)
        private List<String> tools = new ArrayList<>();
        @Parameter(names = "--help", description = "Prints help for report", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Test available tools on Jenkins")
    private static class CommandEnqueue {
        @Parameter(names = "--all", description = "Whether to test all tools or not")
        private Boolean all = false;
        @Parameter(names = "--tool", description = "The specific tools to report", variableArity = true)
        private List<String> tools = new ArrayList<>();
        @Parameter(names = "--help", description = "Prints help for enqueue", help = true)
        private boolean help = false;
    }

    private static class CommandMain {
        @Parameter(names = { "--execution", "--runtime-environment" }, description = "Location of Testing")
        private String execution = "jenkins";
        @Parameter(names = { "--source" }, description = "Tester Group")
        private List<String> source = new ArrayList<>();
        @Parameter(names = { "--api", "--dockstore-url" }, description = "dockstore install that we wish to test tools from")
        private String api = "https://www.dockstore.org:8443/api/ga4gh/v1";
        @Parameter(names = "--help", description = "Prints help for main", help = true)
        private boolean help = false;
    }

    private static class CommandFileReport {
        @Parameter(names = "--tool", description = "Specific tool to report", required = true)
        private String tool = "";
        @Parameter(names = "--help", description = "Prints help for file-report", help = true)
        private boolean help = false;
    }
}

