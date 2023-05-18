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

import static io.dockstore.tooltester.client.cli.JCommanderUtility.out;
import static io.dockstore.tooltester.client.cli.JCommanderUtility.printJCommanderHelp;
import static io.dockstore.tooltester.helper.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.DEBUG;
import static io.dockstore.tooltester.helper.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;
import static io.dockstore.tooltester.helper.JenkinsHelper.buildName;
import static io.dockstore.tooltester.runWorkflow.WorkflowRunner.GSON;
import static io.dockstore.tooltester.runWorkflow.WorkflowRunner.printLine;
import static io.dockstore.tooltester.runWorkflow.WorkflowRunner.uploadRunInfo;
import static io.dockstore.webservice.helpers.S3ClientHelper.getMetricsPlatform;
import static io.dockstore.webservice.helpers.S3ClientHelper.getToolId;
import static io.dockstore.webservice.helpers.S3ClientHelper.getVersionName;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.JobWithDetails;
import io.dockstore.common.Utilities;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.Configuration;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolClass;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.tooltester.TooltesterConfig;
import io.dockstore.tooltester.blacklist.BlackList;
import io.dockstore.tooltester.helper.DockstoreConfigHelper;
import io.dockstore.tooltester.helper.DockstoreEntryHelper;
import io.dockstore.tooltester.helper.GA4GHHelper;
import io.dockstore.tooltester.helper.PipelineTester;
import io.dockstore.tooltester.helper.S3CacheHelper;
import io.dockstore.tooltester.helper.TimeHelper;
import io.dockstore.tooltester.jenkins.OutputFile;
import io.dockstore.tooltester.report.FileReport;
import io.dockstore.tooltester.runWorkflow.WorkflowList;
import io.dockstore.tooltester.runWorkflow.WorkflowRunner;
import io.dockstore.tooltester.runWorkflow.WorkflowRunnerConfig;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prototype for testing service
 */
public class Client {

    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private static final int WAIT_TIME = 15;
    private static final String DEFAULT_SAVE_DIRECTORY = "results";
    private static final String RESULT_DIRECTORY_FLAG = "--results-dir";
    private ContainersApi containersApi;
    private WorkflowsApi workflowsApi;
    private Ga4Ghv20Api ga4Ghv20Api;
    private FileReport fileReport;
    private PipelineTester pipelineTester;
    private TooltesterConfig tooltesterConfig;
    private WorkflowRunnerConfig workflowRunnerConfig;
    private ExtendedGa4GhApi extendedGa4GhApi;
    private Utilities utilities;

    Client() {

    }

    /**
     * Main method
     *
     * @param argv Command line arguments
     */
    public static void main(String[] argv) throws InterruptedException {
        Client client = new Client();
        CommandMain commandMain = new CommandMain();
        JCommander jc = new JCommander(commandMain);
        jc.setProgramName("autotool");
        CommandReport commandReport = new CommandReport();
        CommandEnqueue commandEnqueue = new CommandEnqueue();
        CommandFileReport commandFileReport = new CommandFileReport();
        CommandSync commandSync = new CommandSync();
        CommandRunWorkflows commandRunWorkflows = new CommandRunWorkflows();
        CommandUploadRunResults commandUploadRunResults = new CommandUploadRunResults();
        jc.addCommand("report", commandReport);
        jc.addCommand("enqueue", commandEnqueue);
        jc.addCommand("file-report", commandFileReport);
        jc.addCommand("sync", commandSync);
        jc.addCommand("run-workflows-through-wes", commandRunWorkflows);
        jc.addCommand("upload-results", commandUploadRunResults);
        try {
            jc.parse(argv);
        } catch (MissingCommandException e) {
            jc.usage();
            if (e.getUnknownCommand().isEmpty()) {
                out("No command entered");
                return;
            } else {
                exceptionMessage(e, "Unknown command", COMMAND_ERROR);
            }
        }
        if (commandMain.help) {
            jc.usage();

        } else if (jc.getParsedCommand() == null) {
            jc.usage();
        } else {
            switch (jc.getParsedCommand()) {
            case "report":
                if (commandReport.help) {
                    printJCommanderHelp(jc, "autotool", "report");
                } else {
                    client.handleReport(commandReport.tools, commandEnqueue.source);
                }
                break;
            case "enqueue":
                if (commandEnqueue.help) {
                    printJCommanderHelp(jc, "autotool", "enqueue");
                } else {
                    client.handleRunTests(commandEnqueue.tools, commandEnqueue.source);
                }
                break;
            case "file-report":
                if (commandFileReport.help) {
                    printJCommanderHelp(jc, "autotool", "file-report");
                } else {
                    client.handleFileReport(commandFileReport.tool);
                }
                break;
            case "sync":
                if (commandSync.help) {
                    printJCommanderHelp(jc, "autotool", "sync");
                } else {
                    client.handleCreateTests(commandSync.tools, commandSync.source);
                }
                break;
            case "run-workflows-through-wes":
                if (commandRunWorkflows.help) {
                    printJCommanderHelp(jc, "autotool", "run-workflows-through-wes");
                } else {
                    client.runToolTesterOnWorkflows(commandRunWorkflows.configFilePath, commandRunWorkflows.resultDirectory);
                }
                break;
            case "upload-results":
                if (commandUploadRunResults.help) {
                    printJCommanderHelp(jc, "autotool", "upload-results");
                } else {
                    client.uploadResults(commandUploadRunResults.url, commandUploadRunResults.location, commandUploadRunResults.configFilePath);
                }
                break;
            default:
                jc.usage();
                // This line should never get called, as this case would've been caught when
                // jc.parse(argv) was called
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
            dockstoreTool = containersApi.getPublishedContainerByToolPath(toolName, null);
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get container: " + toolName, API_ERROR);
        }
        assert dockstoreTool != null;
        List<Tag> tags = dockstoreTool.getWorkflowVersions();
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
                            String artifactString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
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

    static ApiClient getApiClient(String serverUrl) {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(serverUrl);
        apiClient.setDebugging(DEBUG.get());
        return apiClient;
    }

    /**
     * This function handles the report command
     *
     * @param toolNames The tools passed in as arguments from the command line
     * @param sources   The verified source to filter
     */
    private void handleReport(List<String> toolNames, List<String> sources) {
        try {
            ReportCommand reportCommand = new ReportCommand();
            reportCommand.report(toolNames, sources);
        } catch (Exception e) {
            exceptionMessage(e, "Can't handle report", GENERIC_ERROR);
        }
    }

    /**
     * Creates or updates the tests. If tool is verified, will create tests for verified versions.  If tool is not verified, will create test for valid versions.
     *
     * @param source    the testing group that verified the tools
     * @param toolnames Tools to specifically test
     */
    private void handleCreateTests(List<String> toolnames, List<String> source) {
        setupClientEnvironment();
        setupTesters();
        List<Tool> tools = GA4GHHelper.getTools(getGa4Ghv20Api(), true, source, toolnames, true, true);
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
        List<Tool> tools = GA4GHHelper.getTools(getGa4Ghv20Api(), true, sources, toolNames, true, true);
        for (Tool tool : tools) {
            testTool(tool);
        }
    }


    private void setUpGa4Ghv20Api() {
        io.dockstore.openapi.client.ApiClient defaultApiClient = io.dockstore.openapi.client.Configuration.getDefaultApiClient();
        defaultApiClient.setBasePath(this.workflowRunnerConfig.getServerUrl());
        setGa4Ghv20Api(new Ga4Ghv20Api(defaultApiClient));
    }

    private void setUpExtendedGa4GhApi(String serverUrl) {
        io.dockstore.openapi.client.ApiClient defaultApiClient = io.dockstore.openapi.client.Configuration.getDefaultApiClient();
        defaultApiClient.setBasePath(serverUrl);
        defaultApiClient.setAccessToken(this.workflowRunnerConfig.getDockstoreToken());
        setExtendedGa4GhApi(new ExtendedGa4GhApi(defaultApiClient));
    }

    private void setUpWorkflowApi() {
        ApiClient defaultApiClient = Configuration.getDefaultApiClient();
        defaultApiClient.setBasePath(this.workflowRunnerConfig.getServerUrl());
        setWorkflowsApi(new WorkflowsApi(defaultApiClient));
    }

    private List<File> getAllFilesInDirectory(File directory) {
        List<File> filesInDirectory = new ArrayList<>();
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                filesInDirectory.addAll(getAllFilesInDirectory(file));
            } else {
                filesInDirectory.add(file);
            }
        }
        return filesInDirectory;
    }

    private void uploadResults(String urlToUploadTo, String resultsDirectoryString, String pathToConfigFile) {
        this.workflowRunnerConfig = new WorkflowRunnerConfig(pathToConfigFile);
        setUpExtendedGa4GhApi(urlToUploadTo);

        Path resultsDirectoryPath = Path.of(resultsDirectoryString);
        File resultsDirectory = new File(resultsDirectoryString);
        List<File> allResultFiles = getAllFilesInDirectory(resultsDirectory);
        for (File file : allResultFiles) {

            Path path = Path.of(file.getPath());
            String fileContent = null;
            try {
                fileContent = Files.readString(path);
            } catch (IOException e) {
                LOG.error("There was an error reading {} to a string", path, e);
                continue;
            }
            ExecutionsRequestBody runMetricsExecutionsRequestBody = null;
            try {
                runMetricsExecutionsRequestBody = GSON.fromJson(fileContent, ExecutionsRequestBody.class);
            } catch (Exception e) {
                LOG.error("There was an error converting the contents of {} to a ExecutionsRequestBody Class", path, e);
                continue;
            }

            final String fileKey = resultsDirectoryPath.relativize(path).toString();

            final String toolID = getToolId(fileKey);
            final String versionName = getVersionName(fileKey);
            final String metricsPlatform = getMetricsPlatform(fileKey);

            LOG.info("Uploading run metrics for: " + fileKey);
            uploadRunInfo(extendedGa4GhApi, runMetricsExecutionsRequestBody, metricsPlatform, toolID, versionName,
                "metrics from a previous run of the tooltester 'run-workflows-through-wes' command");
        }

    }

    private void runToolTesterOnWorkflows(String pathToConfigFile, String resultDirectory) throws InterruptedException {
        this.workflowRunnerConfig = new WorkflowRunnerConfig(pathToConfigFile);
        setUpGa4Ghv20Api();
        setUpExtendedGa4GhApi(this.workflowRunnerConfig.getServerUrl());
        setUpWorkflowApi();
        WorkflowList workflowsToRun = new WorkflowList(getGa4Ghv20Api(), getExtendedGa4GhApi(), getWorkflowsApi(), this.workflowRunnerConfig, resultDirectory);

        for (WorkflowRunner workflow : workflowsToRun.getWorkflowsToRun()) {
            workflow.runWorkflow();
        }

        List<WorkflowRunner> workflowsStillRunning = new ArrayList<>();
        workflowsStillRunning.addAll(workflowsToRun.getWorkflowsToRun());

        while (!workflowsStillRunning.isEmpty()) {
            // Using sleep here as workflows take a while to run, and there is no point in continuously checking if they are finished
            TimeUnit.SECONDS.sleep(WAIT_TIME);
            List<WorkflowRunner> workflowsToCheck = new ArrayList<>();
            workflowsToCheck.addAll(workflowsStillRunning);
            for (WorkflowRunner workflow : workflowsToCheck) {
                if (workflow.isWorkflowFinished()) {
                    workflowsStillRunning.remove(workflow);
                }
            }
        }

        TimeUnit.MINUTES.sleep(WAIT_TIME);

        for (WorkflowRunner workflow : workflowsToRun.getWorkflowsToRun()) {
            workflow.uploadAndSaveRunInfo();
        }
        for (WorkflowRunner workflow : workflowsToRun.getWorkflowsToRun()) {
            workflow.deregisterTasks();
        }

        printLine();

        for (WorkflowRunner workflow : workflowsToRun.getWorkflowsToRun()) {
            workflow.printRunStatistics();
            printLine();
        }

    }

    void setupTesters() {
        pipelineTester = new PipelineTester(tooltesterConfig.getTooltesterConfig());
    }

    void setupClientEnvironment() {
        this.tooltesterConfig = new TooltesterConfig();
        ApiClient defaultApiClient = getApiClient(this.tooltesterConfig.getServerUrl());
        this.containersApi = new ContainersApi(defaultApiClient);
        this.workflowsApi = new WorkflowsApi(defaultApiClient);
        setGa4Ghv20Api(new Ga4Ghv20Api(defaultApiClient));
    }

    private void finalizeFileReport() {
        fileReport.close();
    }

    private void createFileReport(String name) {
        fileReport = new FileReport(name);
    }

    /**
     * Creates the pipeline(s) on Jenkins to test a tool
     *
     * @param tool The tool to create tests for
     */
    private void createToolTests(Tool tool) {
        String toolId = tool.getId();
        String jobTemplate = pipelineTester.getJenkinsJobTemplate();
        for (String runner : tooltesterConfig.getRunner()) {
            List<ToolVersion> toolVersions = tool.getVersions();
            List<ToolVersion> notBlacklistedToolVersions = toolVersions.stream()
                .filter(toolVersion -> BlackList.isNotBlacklisted(toolId, toolVersion.getName())).toList();
            for (ToolVersion toolversion : notBlacklistedToolVersions) {
                String name = buildName(runner, toolversion.getId());
                pipelineTester.createTest(name, jobTemplate);
            }
        }
    }

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
        parameter.put("Config", DockstoreConfigHelper.getConfig(tooltesterConfig.getServerUrl(), runner));
        parameter.put("DockstoreVersion", this.tooltesterConfig.getDockstoreVersion());
        parameter.put("Commands", commands);
        if ("toil".equals(runner)) {
            parameter.put("AnsiblePlaybook", "toilPlaybook");
        } else {
            parameter.put("AnsiblePlaybook", "cwltoolPlaybook");
        }
        return parameter;
    }

    /**
     * test the workflow by 1) Running available parameter files 2) Storing results for each workflow as it finishes
     *
     * @param tool The tool to test
     * @return boolean  Returns true
     */
    private boolean testWorkflow(Tool tool) {
        boolean status = true;
        String toolId = tool.getId();
        Workflow workflow = DockstoreEntryHelper.convertTRSToolToDockstoreEntry(tool, workflowsApi);
        String url = DockstoreEntryHelper.convertGitSSHUrlToGitHTTPSUrl(workflow.getGitUrl());
        List<String> versionsToRun = tool.getVersions().stream().map(ToolVersion::getName).toList();
        if (url == null) {
            return false;
        }
        Long entryId = workflow.getId();
        for (String runner : tooltesterConfig.getRunner()) {
            List<WorkflowVersion> matchingVersions = workflow.getWorkflowVersions().stream()
                .filter(version -> versionsToRun.contains(version.getName())).toList();
            for (WorkflowVersion version : matchingVersions) {
                List<String> commandsList = new ArrayList<>();
                List<String> descriptorsList = new ArrayList<>();
                List<String> parametersList = new ArrayList<>();
                String dockerfilePath = "";
                String tagName = version.getReference();
                Workflow.DescriptorTypeEnum descriptorType = workflow.getDescriptorType();
                List<SourceFile> testParameterFiles;
                SourceFile descriptor;
                String name = buildName(runner, toolId + "-" + tagName);
                try {
                    if (compatibleRunner(runner, descriptorType.toString())) {
                        descriptor = workflowsApi.primaryDescriptor1(entryId, tagName, descriptorType.toString());
                    } else {
                        continue;
                    }
                    String descriptorPath = DockstoreEntryHelper.convertDockstoreAbsolutePathToJenkinsRelativePath(descriptor.getPath());
                    testParameterFiles = workflowsApi.getTestParameterFiles1(entryId, tagName);
                    testParameterFiles.stream().map(testParameterFile -> DockstoreEntryHelper
                        .convertDockstoreAbsolutePathToJenkinsRelativePath(testParameterFile.getPath())).forEach(parameterPath -> {
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
                    String.join(" ", parametersList), String.join(" ", descriptorsList),
                    synapseCache, runner, String.join("%20", commandsList));
                if (!runTest(name, parameter)) {
                    status = false;
                    continue;
                }
            }
        }
        return status;
    }

    /**
     * Checks whether the runner is compatible with the descriptor type
     *
     * @param runner         The runner ("cwltool", "cwlrunner", "cromwell")
     * @param descriptorType The descriptor type in upper case ("CWL", "WDL", "NFL")
     * @return Whether the runner is compatible with the descriptor type or not
     */
    private boolean compatibleRunner(String runner, String descriptorType) {
        // Run CWL on anything except Cromwell
        boolean cwlOnAnythingExceptCromwell = !"cromwell".equals(runner) && descriptorType.equals(Workflow.DescriptorTypeEnum.CWL.toString());
        // Run WDL on Cromwell only
        boolean wdlOnOnlyCromwell = "cromwell".equals(runner) && descriptorType.equals(Workflow.DescriptorTypeEnum.WDL.toString());
        return cwlOnAnythingExceptCromwell || wdlOnOnlyCromwell;
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
     * test the tool by 1) Running available parameter files 2) Rebuilding docker images 3) Storing results for each tool as it finishes
     * <p>
     * in the early phases of the project, we can try to run these locally sequentially however, due to system requirements, it will quickly become necessary to hook this up to either a fixed network
     * of slaves or Consonance on-demand hosts
     *
     * @param tool The tool to test
     * @return boolean  Returns true
     */
    private boolean testDockstoreTool(Tool tool) {
        boolean status = true;
        String toolId = tool.getId();
        DockstoreTool dockstoreTool = DockstoreEntryHelper.convertTRSToolToDockstoreEntry(tool, containersApi);
        String url = DockstoreEntryHelper.convertGitSSHUrlToGitHTTPSUrl(dockstoreTool.getGitUrl());
        List<String> versionsToRun = tool.getVersions().stream().map(ToolVersion::getName).toList();
        if (url == null) {
            return false;
        }
        Long entryId = dockstoreTool.getId();
        for (String runner : tooltesterConfig.getRunner()) {
            List<Tag> matchingVersions = dockstoreTool.getWorkflowVersions().stream()
                .filter(version -> versionsToRun.contains(version.getName())).toList();
            for (Tag tag : matchingVersions) {
                List<String> commandsList = new ArrayList<>();
                List<String> descriptorsList = new ArrayList<>();
                List<String> parametersList = new ArrayList<>();
                String dockerfilePath = tag.getDockerfilePath() != null ? DockstoreEntryHelper
                    .convertDockstoreAbsolutePathToJenkinsRelativePath(tag.getDockerfilePath()) : null;
                String tagName = tag.getName();
                String referenceName = tag.getReference();
                try {
                    List<SourceFile> testParameterFiles;
                    SourceFile descriptor;
                    for (String descriptorType : dockstoreTool.getDescriptorType()) {
                        if (compatibleRunner(runner, descriptorType.toUpperCase())) {
                            descriptor = containersApi.primaryDescriptor(entryId, tagName, descriptorType.toUpperCase());
                        } else {
                            continue;
                        }
                        String descriptorPath = DockstoreEntryHelper
                            .convertDockstoreAbsolutePathToJenkinsRelativePath(descriptor.getPath());
                        testParameterFiles = containersApi.getTestParameterFiles(entryId, descriptorType, tagName);
                        testParameterFiles.stream().map(testParameterFile -> DockstoreEntryHelper
                            .convertDockstoreAbsolutePathToJenkinsRelativePath(testParameterFile.getPath())).forEach(parameterPath -> {
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

    private ExtendedGa4GhApi getExtendedGa4GhApi() {
        return extendedGa4GhApi;
    }

    private void setExtendedGa4GhApi(ExtendedGa4GhApi extendedGa4GhApi) {
        this.extendedGa4GhApi = extendedGa4GhApi;
    }

    private Ga4Ghv20Api getGa4Ghv20Api() {
        return ga4Ghv20Api;
    }

    private void setGa4Ghv20Api(Ga4Ghv20Api ga4Ghv20Api) {
        this.ga4Ghv20Api = ga4Ghv20Api;
    }

    public WorkflowsApi getWorkflowsApi() {
        return workflowsApi;
    }

    public void setWorkflowsApi(WorkflowsApi workflowsApi) {
        this.workflowsApi = workflowsApi;
    }

    private static class CommandMain {

        @Parameter(names = "--help", description = "Prints help for tooltester", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Synchronizes with Jenkins to create tests for verified tools.")
    private static class CommandSync {

        @Parameter(names = {"--source"}, description = "Tester Group")
        private List<String> source = new ArrayList<>();
        @Parameter(names = "--tools", description = "The specific tools to sync", variableArity = true)
        private List<String> tools;
        @Parameter(names = "--help", description = "Prints help for sync", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Test verified tools on Jenkins.")
    private static class CommandEnqueue {

        @Parameter(names = {"--source"}, description = "Tester Group")
        private List<String> source = new ArrayList<>();
        @Parameter(names = "--tool", description = "The specific tools to test", variableArity = true)
        private List<String> tools;
        @Parameter(names = "--help", description = "Prints help for enqueue", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Report status of verified tools tested.")
    private static class CommandReport {

        @Parameter(names = {"--source"}, description = "Tester Group")
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

    @Parameters(separators = "=", commandDescription = "Runs workflows through the Dockstore CLI and AGC, then both prints and uploads to Dockstore the execution statistics.")
    private static class CommandRunWorkflows {

        @Parameter(names = "--help", description = "Prints help for run-workflows-through-wes", help = true)
        private boolean help = false;
        @Parameter(names = "--config-file-path", description = "Path to config file")
        private String configFilePath = "tooltesterConfig.yml";

        @Parameter(names = RESULT_DIRECTORY_FLAG, description = "Name of the directory you want to save the run results to")
        private String resultDirectory = DEFAULT_SAVE_DIRECTORY;

    }

    @Parameters(separators = "=", commandDescription = "Uploads run results from the `run-workflows-through-wes` command to a specified dockstore site.")
    private static class CommandUploadRunResults {

        @Parameter(names = "--help", description = "Prints help for upload-results", help = true)
        private boolean help = false;
        @Parameter(names = "--config-file-path", description = "Path to config file")
        private String configFilePath = "tooltesterConfig.yml";

        @Parameter(names = "--url-to-upload-to", description = "Where the workflow results are being uploaded to (ex. https://qa.dockstore.org/api)", required = true)
        private String url;

        @Parameter(names = RESULT_DIRECTORY_FLAG, description = "Location of result files")
        private String location = DEFAULT_SAVE_DIRECTORY;

    }
}

