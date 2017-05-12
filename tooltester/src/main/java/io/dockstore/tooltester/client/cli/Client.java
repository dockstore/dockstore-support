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
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.JobWithDetails;
import io.dockstore.tooltester.blueOceanJsonObjects.PipelineNodeImpl;
import io.dockstore.tooltester.blueOceanJsonObjects.PipelineStepImpl;
import io.dockstore.tooltester.helper.PipelineTester;
import io.dockstore.tooltester.helper.TimeHelper;
import io.dockstore.tooltester.helper.TinyUrl;
import io.dockstore.tooltester.jenkins.OutputFile;
import io.dockstore.tooltester.report.FileReport;
import io.dockstore.tooltester.report.StatusReport;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.GAGHApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private ContainersApi containersApi;
    private GAGHApi ga4ghApi;
    private StatusReport report;
    private FileReport fileReport;
    private int count = 0;
    private HierarchicalINIConfiguration config;
    private PipelineTester pipelineTester;

    Client() {

    }

    /**
     * Main method
     *
     * @param argv Command line arguments
     */
    public static void main(String[] argv) {
        Client client = new Client();
        CommandMain commandMain = new CommandMain();
        JCommander jc = new JCommander(commandMain);
        jc.setProgramName("autotool");
        CommandReport commandReport = new CommandReport();
        CommandEnqueue commandEnqueue = new CommandEnqueue();
        CommandFileReport commandFileReport = new CommandFileReport();
        CommandSync commandSync = new CommandSync();
        jc.addCommand("report", commandReport);
        jc.addCommand("enqueue", commandEnqueue);
        jc.addCommand("file-report", commandFileReport);
        jc.addCommand("sync", commandSync);
        try {
            jc.parse(argv);
        } catch (MissingCommandException e) {
            jc.usage();
            exceptionMessage(e, "Unknown command", COMMAND_ERROR);
        }
        if (commandMain.help) {
            jc.usage();
        } else {

            if (jc.getParsedCommand() != null) {
                switch (jc.getParsedCommand()) {
                case "report":
                    if (commandReport.help) {
                        jc.usage("report");
                    } else {
                        if (commandReport.all || !commandReport.tools.isEmpty()) {
                            client.handleReport(commandReport.tools);
                        } else {
                            LOG.warn("You must specify --all or --tool");
                            jc.usage("report");
                        }
                    }
                    break;
                case "enqueue":
                    if (commandEnqueue.help) {
                        jc.usage("enqueue");
                    } else {
                        if (commandEnqueue.all) {
                            client.handleRunTests(commandEnqueue.tools, commandEnqueue.unverifiedTool);
                        } else {

                            if (commandEnqueue.tools != null || commandEnqueue.unverifiedTool != null) {
                                client.handleRunTests(commandEnqueue.tools, commandEnqueue.unverifiedTool);
                            } else {
                                jc.usage("enqueue");
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
                case "sync":
                    if (commandSync.help) {
                        jc.usage("sync");
                    } else {
                        if (commandSync.tool != null) {
                            client.handleCreateTests(commandSync.api, commandSync.source, commandSync.execution, commandSync.tool);
                        } else {
                            client.handleCreateTests(commandSync.api, commandSync.source, commandSync.execution, null);
                        }
                    }
                    break;
                default:
                    jc.usage();
                }
            } else {
                jc.usage();
            }
        }
    }

    private void handleFileReport(String toolName) {
        setupClientEnvironment();
        setupTesters();
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        // Incrementing calendar month by 1 because it starts from 0
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        createFileReport(year + "-" + month + "-" + day + "-" + "FileReport.csv");
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
            JobWithDetails jobWithDetails = pipelineTester.getJenkinsJob(name);
            if (jobWithDetails == null) {
                continue;
            }
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
                                String cwlID = file.getKey();
                                String checksum = file.getValue().getChecksum();
                                String size = file.getValue().getSize().toString();
                                String basename = file.getValue().getBasename();
                                List<String> record = Arrays
                                        .asList(String.valueOf(buildId), tag.getName(), cwlID, basename, checksum, size);
                                fileReport.printAndWriteLine(record);
                                try {
                                    inputStream.close();
                                } catch (IOException e) {
                                    exceptionMessage(e, "Could not close input stream", IO_ERROR);
                                }
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
            setupTesters();
            List<Tool> tools = getVerifiedTools();
            Date date = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            int year = cal.get(Calendar.YEAR);
            // Incrementing calendar month by 1 because it starts from 0
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DAY_OF_MONTH);
            createResults(year + "-" + month + "-" + day + "-" + "Report.csv");
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

    // Deprecated, using blue ocean instead
    //    private JenkinsPipeline getJenkinsPipeline(String name, int buildId) {
    //        JenkinsPipeline jenkinsPipeline = null;
    //        try {
    //            String crumb = getJenkinsCrumb();
    //            String username = config.getString("jenkins-username", "travis");
    //            String password = config.getString("jenkins-password", "travis");
    //            String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
    //            HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);
    //            javax.ws.rs.client.Client client = ClientBuilder.newClient().register(feature);
    //            String entity = client.target(serverUrl).path("job/" + name + "/" + buildId + "/wfapi/describe")
    //                    .request(MediaType.TEXT_PLAIN_TYPE).header("crumbRequestField", crumb).get(String.class);
    //            Gson gson = new Gson();
    //            jenkinsPipeline = gson.fromJson(entity, JenkinsPipeline.class);
    //        } catch (Exception e) {
    //            LOG.warn("Could not get Jenkins build for: " + name);
    //        }
    //        return jenkinsPipeline;
    //    }

    /**
     * Creates or updates the tests
     *
     * @param api       api to pull the tools from
     * @param source    the testing group that verified the tools
     * @param execution the location to test the tools
     */
    private void handleCreateTests(String api, List<String> source, String execution, String toolname) {
        if (!execution.equals("jenkins")) {
            errorMessage("Can only execute on jenkins, no other location is currently supported.  Received " + execution, COMMAND_ERROR);
        }
        setupClientEnvironment();
        setupTesters();
        List<Tool> tools;
        if (toolname == null) {
            if (!source.isEmpty()) {
                tools = getVerifiedTools(source);
            } else {
                tools = getVerifiedTools();
            }
        } else {
            tools = getTools(toolname);

        }
        for (Tool tool : tools) {
            createToolTests(tool);
        }
    }

    private void handleRunTests(List<String> toolNames, String unverifiedTool) {
        setupClientEnvironment();
        setupTesters();
        List<Tool> tools;
        if (unverifiedTool != null) {
            tools = getTools(unverifiedTool);
        } else {
            tools = getVerifiedTools();
            if (toolNames != null) {
                tools = tools.parallelStream().filter(t -> toolNames.contains(t.getId())).collect(Collectors.toList());
            }
        }
        for (Tool tool : tools) {
            testTool(tool);
        }
    }

    int getCount() {
        return count;
    }

    void setCount(int count) {
        this.count = count;
    }

    void setupTesters() {
        pipelineTester = new PipelineTester(config);
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
        setGa4ghApi(new GAGHApi(defaultApiClient));
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
        String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
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
                    PipelineNodeImpl[] pipelineNodes = pipelineTester.getBlueOceanJenkinsPipeline(suffix);

                    for (PipelineNodeImpl pipelineNode : pipelineNodes) {
                        try {
                            // There's pretty much always going to be a parallel node that does not matter
                            if (pipelineNode.getDisplayName().equals("Parallel") || pipelineNode.getDurationInMillis() < 0L) {
                                continue;
                            }
                            String state = pipelineNode.getState();
                            String result = pipelineNode.getResult();
                            if (state.equals("RUNNING")) {
                                result = "RUNNING";
                            }
                            Long runtime = 0L;

                            String entity = pipelineTester.getEntity(pipelineNode.getLinks().getSteps().getHref());

                            String nodeLogURI = pipelineNode.getLinks().getSelf().getHref() + "log";
                            String logURL = TinyUrl.getTinyUrl(serverUrl + nodeLogURI);
                            Gson gson = new Gson();
                            result += " See " + logURL;
                            PipelineStepImpl[] pipelineSteps = gson.fromJson(entity, PipelineStepImpl[].class);
                            for (PipelineStepImpl pipelineStep : pipelineSteps) {
                                runtime += pipelineStep.getDurationInMillis();
                            }
                            String date = pipelineNode.getStartTime();
                            String duration;
                            // Blue Ocean REST API does not know how long the job is running for
                            // If it's still running, we get the duration since the start date
                            // If it's finished running, we sum up the duration of each step
                            if (state.equals("RUNNING")) {
                                duration = TimeHelper.getDurationSinceDate(date);
                            } else {
                                duration = TimeHelper.durationToString(runtime);
                            }

                            try {
                                date = TimeHelper.timeFormatConvert(date);
                            } catch (ParseException e) {
                                errorMessage("Could not parse start time " + date, CLIENT_ERROR);
                            }

                            List<String> record = Arrays
                                    .asList(toolversion.getId(), date, tag, "Jenkins", pipelineNode.getDisplayName(), duration, result);
                            report.printAndWriteLine(record);
                        } catch (NullPointerException e) {
                            LOG.warn(e.getMessage());
                        }

                    }
                }
            } else {
                errorMessage("Tool version is null", COMMAND_ERROR);
            }
        }
    }

    //    private String getLog(Stage stage) {
    //        String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
    //        String entity = getEntity(stage.getLinks().getSelf().getHref());
    //        Gson gson = new Gson();
    //        Node node = gson.fromJson(entity, Node.class);
    //        List<StageFlowNode> stageFlowNodes = node.getStageFlowNodes();
    //        for (StageFlowNode stageFlowNode : stageFlowNodes) {
    //            if (stageFlowNode.getStatus().equals("FAILED")) {
    //                try {
    //                    entity = getEntity(stageFlowNode.getLinks().getLog().getHref());
    //                } catch (Exception e) {
    //                    return node.getError().getMessage();
    //                }
    //                break;
    //            }
    //        }
    //        JenkinsLog jenkinsLog = gson.fromJson(entity, JenkinsLog.class);
    //        return serverUrl + jenkinsLog.getConsoleUrl().replaceFirst("^/", "");
    //    }

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

    private List<Tool> getTools(String toolname) {
        List<Tool> tools = null;
        GAGHApi ga4ghApi = getGa4ghApi();
        try {
            tools = ga4ghApi.toolsGet(toolname, null, null, null, null, null, null, null, null);

        } catch (ApiException e) {
            exceptionMessage(e, "", API_ERROR);
        }
        return tools;
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

    ContainersApi getContainersApi() {
        return containersApi;
    }

    private GAGHApi getGa4ghApi() {
        return ga4ghApi;
    }

    private void setGa4ghApi(GAGHApi ga4ghApi) {
        this.ga4ghApi = ga4ghApi;
    }

    private static class CommandMain {
        @Parameter(names = "--help", description = "Prints help for autotool", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Synchronizes with Jenkins to create tests for verified tools.")
    private static class CommandSync {
        @Parameter(names = { "--execution", "--runtime-environment" }, description = "Location of Testing")
        private String execution = "jenkins";
        @Parameter(names = { "--source" }, description = "Tester Group")
        private List<String> source = new ArrayList<>();
        @Parameter(names = { "--api", "--dockstore-url" }, description = "dockstore install that we wish to test tools from")
        private String api = "https://www.dockstore.org:8443/api/ga4gh/v1";
        @Parameter(names = "--help", description = "Prints help for main", help = true)
        private boolean help = false;
        @Parameter(names = "--unverified-tool", description = "Unverified tool to specifically test")
        private String tool;
    }

    @Parameters(separators = "=", commandDescription = "Test verified tools on Jenkins.")
    private static class CommandEnqueue {
        @Parameter(names = "--unverified-tool", description = "Unverified tool to specifically test.")
        public String unverifiedTool;
        @Parameter(names = "--all", description = "Whether to test all tools or not")
        private Boolean all = false;
        @Parameter(names = "--tool", description = "The specific tools to test", variableArity = true)
        private List<String> tools;
        @Parameter(names = "--help", description = "Prints help for enqueue", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Report status of verified tools tested.")
    private static class CommandReport {
        @Parameter(names = "--all", description = "Whether to report all tools or not")
        private Boolean all = false;
        @Parameter(names = "--tool", description = "The specific tools to report", variableArity = true)
        private List<String> tools = new ArrayList<>();
        @Parameter(names = "--help", description = "Prints help for report", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Reports the file sizes and checksum of a verified tool across all tested versions.")
    private static class CommandFileReport {
        @Parameter(names = "--tool", description = "Specific tool to report", required = true)
        private String tool;
        @Parameter(names = "--help", description = "Prints help for file-report", help = true)
        private boolean help = false;
    }
}

