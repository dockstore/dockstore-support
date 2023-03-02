package io.dockstore.tooltester.client.cli;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.dockstore.common.Utilities;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.amazonaws.util.DateUtils.parseISO8601Date;
import static io.dockstore.tooltester.client.cli.JCommanderUtility.out;
import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.errorMessage;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;


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

    private final List<String> inProgressStates = Arrays.asList("RUNNING", "INITIALIZING");

    public WorkflowRunner(String entry, String version, String pathOfTestParameter) {
        this.entry = entry;
        this.version = version;
        this.pathOfTestParameter = pathOfTestParameter;
    }

    private void createAgcWrapper() {
        String commandToWrite = "{\"workflowInputs\": \"" + pathOfTestParameter + "\"}";
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(agcWrapperPath));
            writer.write(commandToWrite);
            writer.close();
        } catch (IOException e) {
            exceptionMessage(e, "There as an error writing to " + agcWrapperPath, COMMAND_ERROR);
        }
    }

    private String getCompleteEntryName() {
        return entry + ":" + version;
    }
    public void runWorkflow() {
        createAgcWrapper();
        ImmutablePair<String, String> result = Utilities.executeCommand("dockstore workflow wes launch --entry "
                + getCompleteEntryName() + " --json " + agcWrapperPath + " --attach " + pathOfTestParameter + " --script");
        runID = StringUtils.deleteWhitespace(result.getLeft());
        out(runID);
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

    private void printTimeStatistic() {
        JsonArray arr = log.getAsJsonArray("task_logs");
        for (JsonElement element: arr) {
            String startTime = element.getAsJsonObject().get("start_time").getAsString();
            String endTime = element.getAsJsonObject().get("end_time").getAsString();
            Date startTimeDate = parseISO8601Date(startTime);
            Date endTimeDate = parseISO8601Date(endTime);
            Long timeBetween = endTimeDate.getTime() - startTimeDate.getTime();
            Long milliseconds = timeBetween % MILLISECONDS_IN_SECOND;
            Long seconds = timeBetween / MILLISECONDS_IN_SECOND;
            Long minutes = seconds / SECONDS_IN_MINUTE;
            seconds = seconds % SECONDS_IN_MINUTE;


            out("TASK NAME: " + element.getAsJsonObject().get("name").getAsString());
            out("START TIME: " + startTime);
            out("END TIME: " + endTime);
            out("DURATION: " + minutes + " minutes " + seconds + " seconds " + milliseconds + " milliseconds");
        }
    }

    public void printRunStatistics() {
        if (isWorkflowFinished()) {
            out("RUN STATISTICS:");
            out("ENTRY NAME: " + getCompleteEntryName());
            if (state.equals("COMPLETE")) {
                printTimeStatistic();
            }
        } else {
            errorMessage("Workflow is not finished, statistic are not available yet", COMMAND_ERROR);
        }
    }

}
