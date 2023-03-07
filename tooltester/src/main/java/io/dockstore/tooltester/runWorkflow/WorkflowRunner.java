package io.dockstore.tooltester.runWorkflow;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.dockstore.common.Utilities;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.FileWrapper;
import org.apache.commons.lang3.StringUtils;
import io.dockstore.webservice.core.Partner;
import org.apache.commons.lang3.tuple.ImmutablePair;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.amazonaws.util.DateUtils.parseISO8601Date;
import static io.dockstore.tooltester.client.cli.JCommanderUtility.out;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.errorMessage;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;
import static java.util.UUID.randomUUID;

/**
 * A class to run workflows and get their metrics
 */
public class WorkflowRunner {

    public static final int MILLISECONDS_IN_SECOND = 1000;
    public static final int SECONDS_IN_MINUTE = 60;
    private String entry;
    private String version;
    private String runID = null;
    private String pathOfTestParameter;
    private String agcWrapperPath = "agcWrapper.json";
    private JsonObject log = null;
    private Boolean finished = false;
    private String state;
    private ExtendedGa4GhApi extendedGa4GhApi;

    private final List<String> inProgressStates = Arrays.asList("RUNNING", "INITIALIZING");

    private List<TimeStatisticForOneTask> timesForEachTask = null;

    /** Construct the WorkflowRunner with a local test parameter file
     *
     * @param entry The workflow's entry (e.g. github.com/dockstore/hello_world)
     * @param version The workflow's version (e.g. 3.0.0)
     * @param pathOfTestParameter A relative path to the location of the test parameter (e.g. test/test-parameter-file.json)
     * @param extendedGa4GhApi
     */
    public WorkflowRunner(String entry, String version, String pathOfTestParameter, ExtendedGa4GhApi extendedGa4GhApi) {
        this.entry = entry;
        this.version = version;
        this.pathOfTestParameter = pathOfTestParameter;
        this.extendedGa4GhApi = extendedGa4GhApi;
    }

    /** Construct the WorkflowRunner with a local test parameter file
     *
     * @param entry The workflow's entry (e.g. github.com/dockstore/hello_world)
     * @param version The workflow's version (e.g. 3.0.0)
     * @param relativePathToTestParameterFile The relative path to the test parameter file on the dockstore site (ex. agc-examples/fastq/input.json)
     * @param ga4Ghv20Api
     * @param extendedGa4GhApi
     * @throws InterruptedException
     */
    public WorkflowRunner(String entry, String version, String relativePathToTestParameterFile, Ga4Ghv20Api ga4Ghv20Api, ExtendedGa4GhApi extendedGa4GhApi) throws InterruptedException {
        this.entry = entry;
        this.version = version;
        this.extendedGa4GhApi = extendedGa4GhApi;

        File testParameterFile = new File("test-paramter-file-" + randomUUID() + ".json");
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
        createAgcWrapper();
        ImmutablePair<String, String> result = Utilities.executeCommand("dockstore workflow wes launch --entry "
                + getCompleteEntryName() + " --json " + agcWrapperPath + " --attach " + pathOfTestParameter + " --script");
        runID = StringUtils.deleteWhitespace(result.getLeft());
    }

    public Boolean isWorkflowFinished() {
        if (finished) {
            return true;
        }
        ImmutablePair<String, String> result = Utilities.executeCommand("dockstore workflow wes logs --id " + runID + " --script");
        String logJson = result.getLeft();
        log = new Gson().fromJson(logJson, JsonObject.class);
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
        JsonArray arr = log.getAsJsonArray("task_logs");
        for (JsonElement element: arr) {
            String startTime = element.getAsJsonObject().get("start_time").getAsString();
            String endTime = element.getAsJsonObject().get("end_time").getAsString();
            Date startTimeDate = parseISO8601Date(startTime);
            Date endTimeDate = parseISO8601Date(endTime);
            times.add(new TimeStatisticForOneTask(startTimeDate, endTimeDate, element.getAsJsonObject().get("name").getAsString()));
        }
        this.timesForEachTask = times;
    }


    private Long getTotalTimeInMilliseconds() {
        if (timesForEachTask == null) {
            setTimeForEachTask();
        }
        Long totalTimeTaken = 0L;
        for (TimeStatisticForOneTask time: timesForEachTask) {
            totalTimeTaken += time.getTimeTakenInMilliseconds();
        }
        return totalTimeTaken;
    }


    private void printTimeStatistic() {
        if (timesForEachTask == null) {
            setTimeForEachTask();
        }
        for (TimeStatisticForOneTask time: timesForEachTask) {
            Long timeTakenInMilliseconds = time.getTimeTakenInMilliseconds();
            Long milliseconds = timeTakenInMilliseconds % MILLISECONDS_IN_SECOND;
            Long seconds = timeTakenInMilliseconds / MILLISECONDS_IN_SECOND;
            Long minutes = seconds / SECONDS_IN_MINUTE;
            seconds = seconds % SECONDS_IN_MINUTE;

            out("");
            out("TASK NAME: " + time.getTaskName());
            out("START TIME: " + time.getStartTime().toString());
            out("END TIME: " + time.getEndTime().toString());
            out("DURATION: " + minutes + " minutes " + seconds + " seconds " + milliseconds + " milliseconds");
        }
        out("");
        Long totalTimeTakenMilliseconds = getTotalTimeInMilliseconds();
        Long milliseconds = totalTimeTakenMilliseconds % MILLISECONDS_IN_SECOND;
        Long seconds = totalTimeTakenMilliseconds / MILLISECONDS_IN_SECOND;
        Long minutes = seconds / SECONDS_IN_MINUTE;
        seconds = seconds % SECONDS_IN_MINUTE;
        out("TOTAL TIME: " + minutes + " minutes " + seconds + " seconds " + milliseconds + " milliseconds");
    }

    public void printRunStatistics() {
        if (isWorkflowFinished()) {
            out("RUN STATISTICS:");
            out("ENTRY NAME: " + getCompleteEntryName());
            out("END STATE: " + state);
            if (state.equals("COMPLETE")) {
                printTimeStatistic();
            }
        } else {
            errorMessage("Workflow is not finished, statistic are not available yet", COMMAND_ERROR);
        }
    }

    private String getTotalTimeInISO861Standard() {
        if (timesForEachTask == null) {
            setTimeForEachTask();
        }
        Long totalTimeMilliseconds = getTotalTimeInMilliseconds();
        Duration duration = Duration.ofMillis(totalTimeMilliseconds);
        return duration.toString();
    }

    private Execution.ExecutionStatusEnum getExecutionStatus() {
        if ("COMPLETE".equals(state)) {
            return Execution.ExecutionStatusEnum.SUCCESSFUL;
        } else {
            return Execution.ExecutionStatusEnum.FAILED_RUNTIME_INVALID;
        }
    }

    public void uploadRunInfo() {
        List<Execution> executions = new ArrayList<>();
        Execution execution = new Execution();
        execution.setExecutionStatus(getExecutionStatus());

        // Only include the time metric if the workflow successfully ran
        if (getExecutionStatus() == Execution.ExecutionStatusEnum.SUCCESSFUL) {
            execution.setExecutionTime(getTotalTimeInISO861Standard());
        }

        executions.add(execution);

        extendedGa4GhApi.executionMetricsPost(executions, Partner.OTHER.name(), getEntryNameForApi(), version, "this was generated with tooltester [TEST]");
    }

    public static void printLine() {
        out("-----------------------------------");
    }

}
