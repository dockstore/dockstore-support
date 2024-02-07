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

import static io.dockstore.common.S3ClientHelper.getMetricsPlatform;
import static io.dockstore.common.S3ClientHelper.getToolId;
import static io.dockstore.common.S3ClientHelper.getVersionName;
import static io.dockstore.tooltester.client.cli.JCommanderUtility.out;
import static io.dockstore.tooltester.client.cli.JCommanderUtility.printJCommanderHelp;
import static io.dockstore.tooltester.runWorkflow.WorkflowRunner.GSON;
import static io.dockstore.tooltester.runWorkflow.WorkflowRunner.printLine;
import static io.dockstore.tooltester.runWorkflow.WorkflowRunner.uploadRunInfo;
import static io.dockstore.utils.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.utils.ExceptionHandler.DEBUG;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.Configuration;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.tooltester.runWorkflow.WorkflowList;
import io.dockstore.tooltester.runWorkflow.WorkflowRunner;
import io.dockstore.tooltester.runWorkflow.WorkflowRunnerConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    private WorkflowsApi workflowsApi;
    private Ga4Ghv20Api ga4Ghv20Api;
    private WorkflowRunnerConfig workflowRunnerConfig;
    private ExtendedGa4GhApi extendedGa4GhApi;

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
        CommandRunWorkflows commandRunWorkflows = new CommandRunWorkflows();
        CommandUploadRunResults commandUploadRunResults = new CommandUploadRunResults();
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

    static ApiClient getApiClient(String serverUrl) {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(serverUrl);
        apiClient.setDebugging(DEBUG.get());
        return apiClient;
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

        List<WorkflowRunner> workflowsStillRunning = new ArrayList<>(workflowsToRun.getWorkflowsToRun());

        while (!workflowsStillRunning.isEmpty()) {
            // Using sleep here as workflows take a while to run, and there is no point in continuously checking if they are finished
            TimeUnit.SECONDS.sleep(WAIT_TIME);
            List<WorkflowRunner> workflowsToCheck = new ArrayList<>(workflowsStillRunning);
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

