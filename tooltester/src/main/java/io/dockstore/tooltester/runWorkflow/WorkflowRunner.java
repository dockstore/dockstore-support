/*
 *    Copyright 2023
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

package io.dockstore.tooltester.runWorkflow;

import static io.dockstore.common.S3ClientHelper.createFileName;
import static io.dockstore.common.metrics.MetricsDataS3Client.generateKey;
import static io.dockstore.tooltester.client.cli.JCommanderUtility.out;
import static io.dockstore.tooltester.helper.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.errorMessage;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.math.NumberUtils.max;
import static org.apache.commons.lang3.math.NumberUtils.min;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDuration;
import static software.amazon.awssdk.utils.DateUtils.parseIso8601Date;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.dockstore.common.Partner;
import io.dockstore.common.Utilities;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DeregisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionsRequest;
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionsResponse;

/**
 * A class to run workflows and get their metrics
 */
public class WorkflowRunner {

    public static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowRunner.class);
    private static final String COMPLETE = "COMPLETE";
    private static final int MAX_NUMBER_OF_TRIES = 15;
    private String entry;
    private String version;
    private String runID = null;
    private String pathOfTestParameter;
    private String agcWrapperPath = "agcWrapper.json";
    private JsonObject log = null;
    private Boolean finished = false;
    private String state;
    private Workflow.DescriptorTypeEnum descriptorType;
    private ExtendedGa4GhApi extendedGa4GhApi;
    private WorkflowsApi workflowsApi;
    private String configFilePath;
    private Date workflowStartTime = null;
    private Date workflowEndTime = null;
    private RunExecution runMetrics;
    private EcsClient ecsClient;
    private String taskDefinitionFamily = null;
    private String taskDefinitionArn = null;
    private String clusterName;
    private String resultDirectory;
    private ExecutionsRequestBody runMetricsExecutionRequestBody = null;

    private final List<String> inProgressStates = Arrays.asList("RUNNING", "INITIALIZING");

    private List<TimeStatisticForOneTask> timesForEachTask = null;

    /**
     * Construct the WorkflowRunner with a local test parameter file
     *
     * @param entry               The workflow's entry (e.g. github.com/dockstore/hello_world)
     * @param version             The workflow's version (e.g. 3.0.0)
     * @param pathOfTestParameter A relative path to the location of the test parameter (e.g. test/test-parameter-file.json)
     * @param extendedGa4GhApi
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public WorkflowRunner(String entry, String version, String pathOfTestParameter, ExtendedGa4GhApi extendedGa4GhApi, WorkflowsApi workflowsApi, WorkflowRunnerConfig workflowRunnerConfig,
        String resultDirectory) {
        this.entry = entry;
        this.version = version;
        this.pathOfTestParameter = pathOfTestParameter;
        this.extendedGa4GhApi = extendedGa4GhApi;
        this.workflowsApi = workflowsApi;
        this.runMetrics = new RunExecution();
        this.ecsClient = EcsClient.builder().build();
        this.resultDirectory = resultDirectory;
        setDescriptorLanguage(workflowRunnerConfig);
    }

    /**
     * Construct the WorkflowRunner with a test parameter file found on Dockstore site
     *
     * @param entry                           The workflow's entry (e.g. github.com/dockstore/hello_world)
     * @param version                         The workflow's version (e.g. 3.0.0)
     * @param relativePathToTestParameterFile The relative path to the test parameter file on the dockstore site (ex. agc-examples/fastq/input.json)
     * @param ga4Ghv20Api
     * @param extendedGa4GhApi
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public WorkflowRunner(String entry, String version, String relativePathToTestParameterFile, Ga4Ghv20Api ga4Ghv20Api, ExtendedGa4GhApi extendedGa4GhApi, WorkflowsApi workflowsApi,
        WorkflowRunnerConfig workflowRunnerConfig, String resultDirectory) {
        this(entry, version, relativePathToTestParameterFile, extendedGa4GhApi, workflowsApi, workflowRunnerConfig, resultDirectory);

        File testParameterFile = new File("test-parameter-file-" + randomUUID() + ".json");
        testParameterFile.deleteOnExit();

        String relativePathToTestParameterFileFormatted;
        if (!relativePathToTestParameterFile.startsWith("/")) {
            relativePathToTestParameterFileFormatted = "/" + relativePathToTestParameterFile;
        } else {
            relativePathToTestParameterFileFormatted = relativePathToTestParameterFile;
        }

        final FileWrapper testParameterFileWrapper = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(getEntryNameForApi(), "WDL", version, relativePathToTestParameterFileFormatted);
        try (
            BufferedWriter writer = new BufferedWriter(new FileWriter(testParameterFile.getPath()))
        ) {
            writer.write(testParameterFileWrapper.getContent());
            this.pathOfTestParameter = testParameterFile.getPath();
        } catch (IOException e) {
            exceptionMessage(e, "Error writing to testParameterFile", COMMAND_ERROR);
        }
    }

    private void setDescriptorLanguage(WorkflowRunnerConfig workflowRunnerConfig) {
        // Get the workflow object associated with the provided entry path
        final Workflow workflow = workflowsApi.getPublishedWorkflowByPath(entry, WorkflowSubClass.BIOWORKFLOW, null, version);
        descriptorType = workflow.getDescriptorType();
        switch (descriptorType) {
        case WDL:
            configFilePath = workflowRunnerConfig.getPathToWdlConfigFIle();
            clusterName = workflowRunnerConfig.getWdlEcsCluster();
            break;

        case CWL:
            configFilePath = workflowRunnerConfig.getPathToCwlConfigFile();
            clusterName = workflowRunnerConfig.getCwlEcsCluster();
            break;

        default:
            errorMessage("The descriptor type of " + workflow.getWorkflowName() + " (which is " + descriptorType.toString() + ") is not supported", GENERIC_ERROR);
        }
    }


    private void createAgcWrapper() {

        String commandToWrite = "{\"workflowInputs\": \"" + pathOfTestParameter + "\"}";

        try (
            BufferedWriter writer = new BufferedWriter(new FileWriter(agcWrapperPath))
        ) {
            writer.write(commandToWrite);
        } catch (IOException e) {
            exceptionMessage(e, "There as an error writing to " + agcWrapperPath, COMMAND_ERROR);
        }
    }

    private String getEntryNameForApi() {
        return "#workflow/" + entry;
    }

    private String getCompleteEntryName() {
        return entry + ":" + version;
    }

    public void runWorkflow() {
        ImmutablePair<String, String> result = null;

        final List<String> ecsTasksBeforeWorkflowWasRun = getListOfEcsTasks();

        switch (descriptorType) {
        case WDL:
            createAgcWrapper();
            result = Utilities.executeCommand("dockstore workflow wes launch --entry "
                + getCompleteEntryName() + " --json " + agcWrapperPath + " --attach " + pathOfTestParameter
                + " --config " + configFilePath + " --script");
            break;
        case CWL:
            result = Utilities.executeCommand("dockstore workflow wes launch --entry "
                + getCompleteEntryName() + " --json " + pathOfTestParameter + " --inline-workflow "
                + " --config " + configFilePath + " --script");
            break;
        default:
            errorMessage("The descriptor type of this workflow is not supported", GENERIC_ERROR);
        }
        runID = StringUtils.deleteWhitespace(result.getLeft());
        setTaskFamily(ecsTasksBeforeWorkflowWasRun);
    }

    public Boolean isWorkflowFinished() {
        if (finished) {
            return true;
        }
        ImmutablePair<String, String> result = Utilities.executeCommand("dockstore workflow wes logs --id " + runID
            + " --config " + configFilePath + " --script");
        String logJson = result.getLeft();
        log = GSON.fromJson(logJson, JsonObject.class);
        state = log.get("state").getAsString();
        if (inProgressStates.contains(state)) {
            return false;
        } else {
            finished = true;
            return true;
        }
    }


    private void setTimeForEachTask() {
        List<TimeStatisticForOneTask> times = new ArrayList<>();
        switch (descriptorType) {
        case WDL:
            JsonArray arr = log.getAsJsonArray("task_logs");
            for (JsonElement element : arr) {
                String startTime = element.getAsJsonObject().get("start_time").getAsString();
                String endTime = element.getAsJsonObject().get("end_time").getAsString();
                Date startTimeDate = Date.from(parseIso8601Date(startTime));
                Date endTimeDate = Date.from(parseIso8601Date(endTime));
                times.add(new TimeStatisticForOneTask(startTimeDate, endTimeDate, element.getAsJsonObject().get("name").getAsString()));
            }
            break;

        case CWL:
            JsonElement runLog = log.getAsJsonObject().get("run_log");
            String startTime = runLog.getAsJsonObject().get("start_time").getAsString();
            String endTime = runLog.getAsJsonObject().get("end_time").getAsString();
            Date startTimeDate = null;
            Date endTimeDate = null;
            try {
                startTimeDate = Date.from(parseIso8601Date(startTime + "Z"));
                endTimeDate = Date.from(parseIso8601Date(endTime + "Z"));
                // The TOIL (which is what runs CWL) endpoint gives times that look like this: 2023-03-20T16:49:23.664
                // the issue is, that the time given is in the UTC time zone, but that is not specified in the time
                // string. The ` + "Z"` specifies to the parser that the time is in the UTC time zone.
            } catch (Exception ex) {
                exceptionMessage(ex, "Unable to pass date for a CWL workflow", API_ERROR);
            }
            times.add(new TimeStatisticForOneTask(startTimeDate, endTimeDate, getCompleteEntryName()));
            break;

        default:
            errorMessage("Workflow is not finished, statistics are not available yet", COMMAND_ERROR);
        }
        this.timesForEachTask = times;
    }


    private Long getSumOfTimeForEachTaskInMilliseconds() {
        if (timesForEachTask == null) {
            setTimeForEachTask();
        }
        Long totalTimeTaken = 0L;
        for (TimeStatisticForOneTask time : timesForEachTask) {
            totalTimeTaken += time.getTimeTakenInMilliseconds();
        }
        return totalTimeTaken;
    }

    private Boolean isFirstDateBeforeSecondDate(Date first, Date second) {
        return first.getTime() < second.getTime();
    }


    private void setStartAndEndTime() {
        if (timesForEachTask == null) {
            setTimeForEachTask();
        }
        if (workflowStartTime != null || workflowEndTime != null) {
            LOGGER.debug("workflowStartTime or workflowEndTime has already been set");
            return;
        }
        Date timeFirstTaskStarted = null;
        Date timeLastTaskFinished = null;
        for (TimeStatisticForOneTask time : timesForEachTask) {
            if (time.getStartTime() == null) {
                continue;
            }
            if (timeFirstTaskStarted == null) {
                timeFirstTaskStarted = time.getStartTime();
            }
            if (time.getEndTime() == null) {
                continue;
            }
            if (timeLastTaskFinished == null) {
                timeLastTaskFinished = time.getEndTime();
            }
            if (isFirstDateBeforeSecondDate(time.getStartTime(), timeFirstTaskStarted)) {
                timeFirstTaskStarted = time.getStartTime();
            }

            if (isFirstDateBeforeSecondDate(timeLastTaskFinished, time.getEndTime())) {
                timeLastTaskFinished = time.getEndTime();
            }
        }

        workflowStartTime = timeFirstTaskStarted;
        workflowEndTime = timeLastTaskFinished;

    }

    private Long getWallClockTimeInMilliseconds() {
        if (timesForEachTask == null) {
            setTimeForEachTask();
        }
        setStartAndEndTime();
        if (workflowStartTime == null || workflowEndTime == null) {
            return null;
        }
        return workflowEndTime.getTime() - workflowStartTime.getTime();
    }


    private void printTimeStatistic() {
        if (timesForEachTask == null) {
            setTimeForEachTask();
        }
        for (TimeStatisticForOneTask time : timesForEachTask) {
            out("");
            out("TASK NAME: " + time.getTaskName());
            out("START TIME: " + time.getStartTime().toString());
            if (time.getEndTime() != null) {
                out("END TIME: " + time.getEndTime().toString());
                out("DURATION: " + formatDuration(time.getTimeTakenInMilliseconds(), "m' minutes 's' seconds 'S' milliseconds'"));
            } else {
                out("The end time for this task was null");
            }
        }
        out("");
        final Long wallTimeMilliseconds = getWallClockTimeInMilliseconds();
        out(String.format("TOTAL TIME (WALL CLOCK): %s", wallTimeMilliseconds == null ? "Not available" : formatDuration(getWallClockTimeInMilliseconds(), "m' minutes 's' seconds 'S' milliseconds'")));
        out("SUM OF TIMES TAKEN TO COMPLETE EACH TASK: " + formatDuration(getSumOfTimeForEachTaskInMilliseconds(), "m' minutes 's' seconds 'S' milliseconds'"));
    }

    private void printStatisticsInRunMetrics() {
        if (taskDefinitionArn == null || taskDefinitionFamily == null) {
            return;
        }
        for (Map.Entry<String, Object> additionalProperty : runMetrics.getAdditionalProperties().entrySet()) {
            out("");
            out("Property Name: " + additionalProperty.getKey());
            out("Property Value: " + additionalProperty.getValue());
        }
    }

    public void printRunStatistics() {
        if (isWorkflowFinished()) {
            out("RUN STATISTICS:");
            out("ENTRY NAME: " + getCompleteEntryName());
            out("END STATE: " + state);
            printTimeStatistic();
            printStatisticsInRunMetrics();

        } else {
            errorMessage("Workflow is not finished, statistics are not available yet", COMMAND_ERROR);
        }
    }

    private String getTotalWallClockTimeInISO861Standard() {
        if (getWallClockTimeInMilliseconds() == null) {
            return null;
        }
        Long totalTimeMilliseconds = getWallClockTimeInMilliseconds();
        Duration duration = Duration.ofMillis(totalTimeMilliseconds);
        return duration.toString();
    }

    private RunExecution.ExecutionStatusEnum getExecutionStatus() {
        if (COMPLETE.equals(state)) {
            return RunExecution.ExecutionStatusEnum.SUCCESSFUL;
        } else {
            return RunExecution.ExecutionStatusEnum.FAILED_RUNTIME_INVALID;
        }
    }

    private List<String> getListOfEcsTasks() {
        ListTaskDefinitionsRequest request = ListTaskDefinitionsRequest.builder().build();
        ListTaskDefinitionsResponse response = ecsClient.listTaskDefinitions(request);
        return response.taskDefinitionArns();
    }


    private void setTaskFamily(final List<String> listOfEcsTasksBeforeNewOneWasAdded) {
        int numberOfAttempts = 0;
        while (numberOfAttempts < MAX_NUMBER_OF_TRIES) {
            numberOfAttempts++;
            List<String> ecsTasks = new ArrayList<>();
            ecsTasks.addAll(getListOfEcsTasks());
            ecsTasks.removeAll(listOfEcsTasksBeforeNewOneWasAdded);

            if (!ecsTasks.isEmpty()) {
                String ecsTaskArn = ecsTasks.get(0);
                if (ecsTasks.size() != 1) {
                    LOGGER.warn("More than one ECS task has been created, assuming the first one is the ECS task"
                        + " for this workflow, the following were discovered: " + System.lineSeparator()
                        + ecsTasks);
                }
                DescribeTaskDefinitionRequest request = DescribeTaskDefinitionRequest.builder().taskDefinition(ecsTaskArn).build();
                DescribeTaskDefinitionResponse response = ecsClient.describeTaskDefinition(request);
                taskDefinitionFamily = response.taskDefinition().family();
                taskDefinitionArn = response.taskDefinition().taskDefinitionArn();
                break;
            }

            try {
                final Long waitTime = 20L;
                TimeUnit.SECONDS.sleep(waitTime);
            } catch (Exception ex) {
                exceptionMessage(ex, "time error", COMMAND_ERROR);
            }
        }
    }

    public static void uploadRunInfo(ExtendedGa4GhApi extendedGa4GhApi, ExecutionsRequestBody executionsRequestBody, String partnerName,
        String entryNameForApi, String version, String message) {
        try {
            extendedGa4GhApi.executionMetricsPost(executionsRequestBody, partnerName, entryNameForApi, version, message);
        } catch (Exception e) {
            LOGGER.error("Error uploading the metrics for {} to {} through extendedGa4GhApi.executionMetricsPost", entryNameForApi, extendedGa4GhApi.getApiClient().getBasePath(), e);
        }
    }

    private void saveRunInfo() {
        final String fileName = createFileName();
        final String filePath = resultDirectory + "/" + generateKey(getEntryNameForApi(), version, Partner.AGC.name(), fileName);
        File runFile = new File(filePath);
        runFile.getParentFile().mkdirs();
        try {
            runFile.createNewFile();
        } catch (IOException e) {
            LOGGER.error("Error creating directory {}", runFile.getParentFile(), e);
            return;
        }

        try (
            BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))
        ) {
            writer.write(GSON.toJson(runMetricsExecutionRequestBody));
        } catch (IOException e) {
            LOGGER.error("There as an error writing to {}", filePath, e);
        }
    }


    public void uploadAndSaveRunInfo() {
        runMetrics.setExecutionStatus(getExecutionStatus());
        if (getTotalWallClockTimeInISO861Standard() != null) {
            runMetrics.setExecutionTime(getTotalWallClockTimeInISO861Standard());
        }
        runMetrics.setDateExecuted(workflowStartTime.toString());
        addDataFromSingleMetric("CpuUtilized");
        addDataFromSingleMetric("MemoryUtilized");

        runMetricsExecutionRequestBody = new ExecutionsRequestBody().addRunExecutionsItem(runMetrics);

        uploadRunInfo(extendedGa4GhApi, runMetricsExecutionRequestBody, Partner.AGC.name(), getEntryNameForApi(), version, "generated with tooltester ('run-workflows-through-wes' command)");
        saveRunInfo();
    }

    private void addDataFromSingleMetric(String metricName) {
        if (taskDefinitionArn == null || taskDefinitionFamily == null) {
            return;
        }
        CloudWatchClient cloudWatchClient = CloudWatchClient.builder().build();
        Dimension clusterNameDimension = Dimension.builder()
            .name("ClusterName")
            .value(clusterName)
            .build();
        Dimension clusterTaskDefinitionFamily = Dimension.builder()
            .name("TaskDefinitionFamily")
            .value(taskDefinitionFamily)
            .build();
        final int period = 60;
        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
            .namespace("ECS/ContainerInsights")
            .metricName(metricName)
            .dimensions(clusterNameDimension, clusterTaskDefinitionFamily)
            .period(period)
            .statistics(Statistic.AVERAGE)
            .startTime(workflowStartTime.toInstant())
            .endTime(workflowEndTime.toInstant())
            .build();
        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
        Double sumOfDataPoints = 0D;
        Double maxDataPoint = response.datapoints().get(0).average();
        Double minDataPoint = response.datapoints().get(0).average();
        final int numberOfDataPoints = response.datapoints().size();
        for (Datapoint datapoint : response.datapoints()) {
            sumOfDataPoints += datapoint.average();
            maxDataPoint = max(maxDataPoint, datapoint.average());
            minDataPoint = min(minDataPoint, datapoint.average());
            // datapoint.average() is not actually obtaining the average. This is because the ECS container is only
            // collecting metrics every minute, and we have asked for a minute by minute metric breakdown.
            // So, we are getting the only statistic collected for each minute.
        }
        final Double averageOfDataPoints = sumOfDataPoints / numberOfDataPoints;

        runMetrics.putAdditionalPropertiesItem(metricName + "_AVERAGE", averageOfDataPoints);
        runMetrics.putAdditionalPropertiesItem(metricName + "_MAX", maxDataPoint);
        runMetrics.putAdditionalPropertiesItem(metricName + "_MIN", minDataPoint);
        runMetrics.putAdditionalPropertiesItem(metricName + "_NUMBER_OF_DATAPOINTS", numberOfDataPoints);

    }

    public void deregisterTasks() {
        if (Workflow.DescriptorTypeEnum.WDL == descriptorType && taskDefinitionArn != null) {
            DeregisterTaskDefinitionRequest request = DeregisterTaskDefinitionRequest.builder().taskDefinition(taskDefinitionArn).build();
            ecsClient.deregisterTaskDefinition(request);
        }
    }


    public static void printLine() {
        out("-----------------------------------");
    }

}
