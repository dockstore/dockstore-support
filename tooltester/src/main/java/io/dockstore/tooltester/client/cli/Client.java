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
import io.dockstore.tooltester.blacklist.BlackList;
import io.dockstore.tooltester.blueOceanJsonObjects.PipelineNodeImpl;
import io.dockstore.tooltester.blueOceanJsonObjects.PipelineStepImpl;
import io.dockstore.tooltester.helper.DockstoreConfigHelper;
import io.dockstore.tooltester.helper.DockstoreEntryHelper;
import io.dockstore.tooltester.helper.GA4GHHelper;
import io.dockstore.tooltester.helper.JenkinsHelper;
import io.dockstore.tooltester.helper.PipelineTester;
import io.dockstore.tooltester.helper.S3CacheHelper;
import io.dockstore.tooltester.helper.TimeHelper;
import io.dockstore.tooltester.helper.TinyUrl;
import io.dockstore.tooltester.jenkins.OutputFile;
import io.dockstore.tooltester.report.FileReport;
import io.dockstore.tooltester.report.StatusReport;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolClass;
import io.swagger.client.model.ToolVersion;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
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
    private WorkflowsApi workflowsApi;
    private Ga4GhApi ga4ghApi;
    private StatusReport report;
    private FileReport fileReport;
    private int count = 0;
    private HierarchicalINIConfiguration config;
    private PipelineTester pipelineTester;
    private String[] runner;
    private String url;
    private String dockstoreVersion;

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
                        client.handleReport(commandReport.tools, commandEnqueue.source);
                    }
                    break;
                case "enqueue":
                    if (commandEnqueue.help) {
                        jc.usage("enqueue");
                    } else {
                        client.handleRunTests(commandEnqueue.tools, commandEnqueue.source);
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
                        client.handleCreateTests(commandSync.tools, commandSync.source);
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
        String prefix = TimeHelper.getDateFilePrefix();
        createFileReport(prefix + "FileReport.csv");
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
     * @param toolNames The tools passed in as arguments from the command line
     * @param sources   The verified source to filter
     */
    private void handleReport(List<String> toolNames, List<String> sources) {
        try {
            setupClientEnvironment();
            setupTesters();
            List<Tool> tools = GA4GHHelper.getTools(this.getGa4ghApi(), true, sources, toolNames);
            String prefix = TimeHelper.getDateFilePrefix();
            createResults(prefix + "Report.csv");
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
     * Creates or updates the tests. If tool is verified, will create tests for verified versions.  If tool is not verified, will create test for valid versions.
     *
     * @param source    the testing group that verified the tools
     * @param toolnames Tools to specifically test
     */
    private void handleCreateTests(List<String> toolnames, List<String> source) {
        setupClientEnvironment();
        setupTesters();
        List<Tool> tools = GA4GHHelper.getTools(getGa4ghApi(), true, source, toolnames);
        for (Tool tool : tools) {
            createToolTests(tool);
        }
    }

    /**
     * Runs tests that should've been created.  If tool is verified, it will run tests for verified versions.  If tool is not verified, it will run tests for valid versions.
     *
     * @param toolNames Tools to specifically test
     * @param sources   Verified sources to filter by
     */
    private void handleRunTests(List<String> toolNames, List<String> sources) {
        setupClientEnvironment();
        setupTesters();
        List<Tool> tools = GA4GHHelper.getTools(getGa4ghApi(), true, sources, toolNames);
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
        String serverUrl = config.getString("server-url", "https://staging.dockstore.org:443/api");
        this.runner = this.config.getString("runner", "cwltool cwl-runner toil cromwell").split(" ");
        this.dockstoreVersion = config.getString("dockstore-version", "1.5.0-beta.5");
        this.url = serverUrl;

        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();
        defaultApiClient.setBasePath(serverUrl);

        this.containersApi = new ContainersApi(defaultApiClient);
        this.workflowsApi = new WorkflowsApi(defaultApiClient);
        setGa4ghApi(new Ga4GhApi(defaultApiClient));
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
        Workflow workflow;
        Long containerId;
        if (verifiedTool.getToolclass().getName().equals("Workflow")) {
            workflow = workflowsApi.getPublishedWorkflowByPath(verifiedTool.getId().replace("#workflow/", ""));
            containerId = workflow.getId();
            for (ToolVersion version : verifiedTool.getVersions()) {
                String tag = version.getName();
                for (ToolVersion.DescriptorTypeEnum descriptorType : version.getDescriptorType()) {
                    switch (descriptorType.toString()) {
                    case "CWL":
                        descriptor = workflowsApi.cwl(containerId, tag);
                        break;
                    case "WDL":
                        descriptor = workflowsApi.wdl(containerId, tag);
                        break;
                    default:
                        break;
                    }
                    testParameterFiles = workflowsApi.getTestParameterFiles(containerId, tag);
                    for (SourceFile testParameterFile : testParameterFiles) {
                        testParameter = testParameterFile;
                        count++;
                    }
                }
            }
        } else {
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
                    testParameterFiles = containersApi.getTestParameterFiles(containerId, descriptorType.toString(), tag);
                    for (SourceFile testParameterFile : testParameterFiles) {
                        testParameter = testParameterFile;
                        count++;
                    }
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
        String toolId = tool.getId();
        for (String runner : this.runner) {
            List<ToolVersion> toolVersions = tool.getVersions();
            for (ToolVersion toolversion : toolVersions) {
                boolean blacklisted = BlackList.BLACKLIST.stream()
                        .anyMatch(object -> object.getToolId().equals(toolId) && object.getToolVersionName().equals(toolversion.getName()));
                if (blacklisted) {
                    continue;
                }
                String name = buildName(runner, toolversion.getId());
                pipelineTester.createTest(name);
            }
        }
    }

    /**
     * Creates the pipeline(s) on Jenkins to test a tool
     *
     * @param tool The tool to get the test results for
     */
    private void getToolTestResults(Tool tool) {
        String toolId = tool.getId();
        for (String runner : this.runner) {
            String serverUrl = config.getString("jenkins-server-url", "http://172.18.0.22:8080");
            List<ToolVersion> toolVersions = tool.getVersions();
            for (ToolVersion toolversion : toolVersions) {
                if (toolversion != null) {
                    boolean blacklisted = BlackList.BLACKLIST.stream()
                            .anyMatch(object -> object.getToolId().equals(toolId) && object.getToolVersionName().equals(toolversion.getName()));
                    if (blacklisted) {
                        continue;
                    }
                    String id = toolversion.getId();
                    String tag = toolversion.getName();
                    String name = buildName(runner, id);
                    if (pipelineTester.getJenkinsJob(name) == null) {
                        LOG.info("Could not get job: " + name);
                    } else {
                        int buildId = pipelineTester.getLastBuildId(name);
                        if (buildId == 0 || buildId == -1) {
                            LOG.info("No build was ran for " + name);
                            continue;
                        }
                        PipelineNodeImpl[] pipelineNodes = pipelineTester.getBlueOceanJenkinsPipeline(name);

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
                                String longURL = serverUrl + nodeLogURI;
                                String logURL = TinyUrl.getTinyUrl(longURL);
                                Gson gson = new Gson();
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
                                        .asList(date, toolversion.getId(), tag, runner, pipelineNode.getDisplayName(), result, duration,
                                                logURL);
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

    @SuppressWarnings("checkstyle:parameternumber")
    private Map<String, String> constructParameterMap(String url, String referenceName, String entryType, String dockerfilePath,
            String parameterPath, String descriptorPath, String synapseCache, String runner, String commands) {
        Map<String, String> parameter = new HashMap<>();
        parameter.put("URL", url);
        parameter.put("ParameterPath", parameterPath);
        parameter.put("DescriptorPath", descriptorPath);
        parameter.put("Tag", referenceName);
        parameter.put("EntryType", entryType);
        parameter.put("DockerfilePath", dockerfilePath);
        parameter.put("SynapseCache", synapseCache);
        parameter.put("Config", DockstoreConfigHelper.getConfig(this.url, runner));
        parameter.put("DockstoreVersion", this.dockstoreVersion);
        parameter.put("Commands", commands);
        if (runner == "toil") {
            parameter.put("AnsiblePlaybook", "toilPlaybook");
        } else {
            parameter.put("AnsiblePlaybook", "cwltoolPlaybook");
        }
        return parameter;
    }

    /**
     * test the workflow by
     * 1) Running available parameter files
     * 2) Storing results for each workflow as it finishes
     *
     * @param tool The tool to test
     * @return boolean  Returns true
     */
    private boolean testWorkflow(Tool tool) {
        boolean status = true;
        String toolId = tool.getId();
        Workflow workflow = DockstoreEntryHelper.convertTRSToolToDockstoreEntry(tool, workflowsApi);
        String url = DockstoreEntryHelper.convertGitSSHUrlToGitHTTPSUrl(workflow.getGitUrl());
        if (url == null) {
            return false;
        }
        Long entryId = workflow.getId();
        for (String runner : this.runner) {
            List<WorkflowVersion> verifiedVersions = workflow.getWorkflowVersions().stream().filter(version -> version.isVerified())
                    .collect(Collectors.toList());
            for (WorkflowVersion version : verifiedVersions) {
                boolean blacklisted = BlackList.BLACKLIST.stream()
                        .anyMatch(object -> object.getToolId().equals(toolId) && object.getToolVersionName().equals(version.getName()));
                if (blacklisted) {
                    continue;
                }
                List<String> commandsList = new ArrayList<>();
                List<String> descriptorsList = new ArrayList<>();
                List<String> parametersList = new ArrayList<>();
                String dockerfilePath = "";
                String tagName = version.getReference();
                String descriptorType = workflow.getDescriptorType();
                List<SourceFile> testParameterFiles;
                SourceFile descriptor;
                String name = buildName(runner, toolId + "-" + tagName);
                try {
                    switch (descriptorType) {
                    case "cwl":
                        if (!runner.equals("cromwell")) {
                            descriptor = workflowsApi.cwl(entryId, tagName);
                            break;
                        } else {
                            continue;
                        }
                    case "wdl":
                        if (runner.equals("cromwell")) {
                            descriptor = workflowsApi.wdl(entryId, tagName);
                            break;
                        } else {
                            continue;
                        }
                    default:
                        LOG.info("Unknown descriptor, skipping");
                        continue;
                    }
                    String descriptorPath = DockstoreEntryHelper.convertDockstoreAbsolutePathToJenkinsRelativePath(descriptor.getPath());
                    testParameterFiles = workflowsApi.getTestParameterFiles(entryId, tagName);
                    testParameterFiles.stream()
                            .map(testParameterFile -> DockstoreEntryHelper.convertDockstoreAbsolutePathToJenkinsRelativePath(testParameterFile.getPath()))
                            .forEach(parameterPath -> {
                                parametersList.add(parameterPath);
                                descriptorsList.add(descriptorPath);
                                commandsList.add(DockstoreEntryHelper.generateLaunchEntryCommand(workflow, version, parameterPath));
                            });

                } catch (ApiException e) {
                    exceptionMessage(e, "Could not get cwl or wdl and test parameter files using the workflows API for " + name, API_ERROR);
                }
                String synapseCache = S3CacheHelper.mapRepositoryToCache(workflow.getFullWorkflowPath());
                if (parametersList.size() != descriptorsList.size() || descriptorsList.size() != commandsList.size()) {
                    throw new RuntimeException();
                }
                Map<String, String> parameter = constructParameterMap(url, tagName, "workflow", dockerfilePath,
                        parametersList.stream().collect(Collectors.joining(" ")), descriptorsList.stream().collect(Collectors.joining(" ")),
                        synapseCache, runner, String.join("%20", commandsList));
                if (!runTest(name, parameter)) {
                    status = false;
                    continue;
                }
            }
        }
        return status;
    }

    private boolean runTest(String name, Map<String, String> parameter) {
        String parameterPath = parameter.get("ParameterPath");
        String descriptorPath = parameter.get("DescriptorPath");
        if (parameterPath.length() == 0 || descriptorPath.length() == 0) {
            LOG.debug("Skipping test, no descriptor or test parameter file found for " + name);
            return false;
        }
        if (!pipelineTester.isRunning(name)) {
            pipelineTester.runTest(name, parameter);
        } else {
            LOG.info("Job " + name + " is already running");
        }
        return true;
    }

    private boolean testTool(Tool tool) {
        ToolClass toolClass = tool.getToolclass();
        if (toolClass == null) {
            LOG.error("toolclass not found");
            return false;
        } else {
            String name = toolClass.getName();
            if (name == null) {
                LOG.error("toolclass name not found");
                return false;
            } else {
                switch (name) {
                case "CommandLineTool":
                    testDockstoreTool(tool);
                    break;
                case "Workflow":
                    testWorkflow(tool);
                    break;
                default:
                    LOG.error("Unrecognized toolclass name.  Expected 'CommandLineTool' or 'Workflow'.  Got " + name);
                    return false;
                }
            }
        }
        return true;
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
    private boolean testDockstoreTool(Tool tool) {
        boolean status = true;
        String toolId = tool.getId();
        DockstoreTool dockstoreTool = DockstoreEntryHelper.convertTRSToolToDockstoreEntry(tool, containersApi);
        String url = DockstoreEntryHelper.convertGitSSHUrlToGitHTTPSUrl(dockstoreTool.getGitUrl());
        if (url == null) {
            return false;
        }
        Long entryId = dockstoreTool.getId();
        for (String runner : this.runner) {
            List<Tag> verifiedTags = dockstoreTool.getTags().stream().filter(tag -> tag.isVerified()).collect(Collectors.toList());
            for (Tag tag : verifiedTags) {
                boolean blacklisted = BlackList.BLACKLIST.stream()
                        .anyMatch(object -> object.getToolId().equals(toolId) && object.getToolVersionName().equals(tag.getName()));
                if (blacklisted) {
                    continue;
                }
                List<String> commandsList = new ArrayList<>();
                List<String> descriptorsList = new ArrayList<>();
                List<String> parametersList = new ArrayList<>();
                String dockerfilePath =
                        tag.getDockerfilePath() != null ? DockstoreEntryHelper.convertDockstoreAbsolutePathToJenkinsRelativePath(tag.getDockerfilePath()) : null;
                String tagName = tag.getName();
                String referenceName = tag.getReference();
                try {
                    List<SourceFile> testParameterFiles;
                    SourceFile descriptor;
                    for (String descriptorType : dockstoreTool.getDescriptorType()) {
                        switch (descriptorType.toUpperCase()) {
                        case "CWL":
                            if (!runner.equals("cromwell")) {
                                descriptor = containersApi.cwl(entryId, tagName);
                                break;
                            } else {
                                continue;
                            }
                        case "WDL":
                            if (runner.equals("cromwell")) {
                                descriptor = containersApi.wdl(entryId, tagName);
                                break;
                            } else {
                                continue;
                            }
                        default:
                            LOG.info("Unknown descriptor, skipping");
                            continue;
                        }
                        String descriptorPath = DockstoreEntryHelper.convertDockstoreAbsolutePathToJenkinsRelativePath(descriptor.getPath());
                        testParameterFiles = containersApi.getTestParameterFiles(entryId, descriptorType, tagName);
                        testParameterFiles.stream()
                                .map(testParameterFile -> DockstoreEntryHelper.convertDockstoreAbsolutePathToJenkinsRelativePath(testParameterFile.getPath()))
                                .forEach(parameterPath -> {
                                    parametersList.add(parameterPath);
                                    descriptorsList.add(descriptorPath);
                                    commandsList.add(DockstoreEntryHelper.generateLaunchEntryCommand(dockstoreTool, tag, parameterPath));
                            });
                    }
                } catch (ApiException e) {
                    exceptionMessage(e, "Could not get cwl or wdl and test parameter files using the container API", API_ERROR);
                }
                if (parametersList.size() != descriptorsList.size() || descriptorsList.size() != commandsList.size()) {
                    throw new RuntimeException();
                }
                Map<String, String> parameter = constructParameterMap(url, referenceName, "tool", dockerfilePath,
                        String.join(" ", parametersList), String.join(" ", descriptorsList), "", runner, String.join("%20", commandsList));
                String name = buildName(runner, toolId + "-" + tagName);
                if (!runTest(name, parameter)) {
                    status = false;
                    continue;
                }
            }
        }
        return status;
    }

    ContainersApi getContainersApi() {
        return containersApi;
    }

    private Ga4GhApi getGa4ghApi() {
        return ga4ghApi;
    }

    private void setGa4ghApi(Ga4GhApi ga4ghApi) {
        this.ga4ghApi = ga4ghApi;
    }

    /**
     * Constructs the name of the Pipeline on Jenkins based on several properties
     *
     * @param runner        The runner (cwltool, toil, cromwell)
     * @param ToolVersionId The ToolVersion ID, which is also equivalent to the Tool ID + version name
     * @return
     */
    private String buildName(String runner, String ToolVersionId) {
        String prefix = PipelineTester.PREFIX;
        String name = String.join("-", prefix, runner, ToolVersionId);
        name = JenkinsHelper.cleanSuffx(name);
        return name;
    }

    private static class CommandMain {
        @Parameter(names = "--help", description = "Prints help for tooltester", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Synchronizes with Jenkins to create tests for verified tools.")
    private static class CommandSync {
        @Parameter(names = { "--source" }, description = "Tester Group")
        private List<String> source = new ArrayList<>();
        @Parameter(names = "--tools", description = "The specific tools to sync", variableArity = true)
        private List<String> tools;
        @Parameter(names = "--help", description = "Prints help for sync", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Test verified tools on Jenkins.")
    private static class CommandEnqueue {
        @Parameter(names = { "--source" }, description = "Tester Group")
        private List<String> source = new ArrayList<>();
        @Parameter(names = "--tool", description = "The specific tools to test", variableArity = true)
        private List<String> tools;
        @Parameter(names = "--help", description = "Prints help for enqueue", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Report status of verified tools tested.")
    private static class CommandReport {
        @Parameter(names = { "--source" }, description = "Tester Group")
        private List<String> source = new ArrayList<>();
        @Parameter(names = "--tool", description = "The specific tools to report", variableArity = true)
        private List<String> tools = new ArrayList<>();
        @Parameter(names = "--help", description = "Prints help for report", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Reports the file sizes and checksum of a verified tool across all tested versions.")
    private static class CommandFileReport {
        @Parameter(names = "--tool", description = "The specific tool to report", required = true)
        private String tool;
        @Parameter(names = "--help", description = "Prints help for file-report", help = true)
        private boolean help = false;
    }
}

