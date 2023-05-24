package io.dockstore.tooltester.helper;

import static io.dockstore.tooltester.helper.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;

import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import java.util.ArrayList;

/**
 * @author gluu
 * @since 03/04/19
 */
public final class DockstoreEntryHelper {

    private DockstoreEntryHelper() {
        // hidden constructor
    }

    public static String generateLaunchEntryCommand(Workflow workflow, WorkflowVersion workflowVersion, String parameterFilePath) {
        String fileTypeFlag = "--json";
        if (parameterFilePath.endsWith(".yml") || parameterFilePath.endsWith(".yaml")) {
            fileTypeFlag = "--yaml";
        }
        String entryPath = String.join(":", workflow.getFullWorkflowPath(), workflowVersion.getName());
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("dockstore");
        commandList.add("workflow");
        commandList.add("launch");
        commandList.add("--entry");
        commandList.add(entryPath);
        commandList.add(fileTypeFlag);
        commandList.add(parameterFilePath);
        commandList.add("--script");
        return String.join(" ", commandList);
    }

    public static String generateLaunchEntryCommand(DockstoreTool tool, Tag tag, String parameterFilePath) {
        String fileTypeFlag = "--json";
        if (parameterFilePath.endsWith(".yml") || parameterFilePath.endsWith(".yaml")) {
            fileTypeFlag = "--yaml";
        }
        String entryPath = String.join(":", tool.getToolPath(), tag.getName());
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("dockstore");
        commandList.add("tool");
        commandList.add("launch");
        commandList.add("--entry");
        commandList.add(entryPath);
        commandList.add(fileTypeFlag);
        commandList.add(parameterFilePath);
        commandList.add("--script");
        return String.join(" ", commandList);
    }

    public static Workflow convertTRSToolToDockstoreEntry(Tool tool, WorkflowsApi workflowsApi) {
        String toolId = tool.getId();
        String path = toolId.replace("#workflow/", "");
        try {
            return workflowsApi.getPublishedWorkflowByPath(path, null, null, null);
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get " + path + " using the workflowsApi API", API_ERROR);
        }
        return null;
    }

    public static DockstoreTool convertTRSToolToDockstoreEntry(Tool tool, ContainersApi containersApi) {
        try {
            return containersApi.getPublishedContainerByToolPath(tool.getId(), null);
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get published containers using the container API", API_ERROR);
        }
        return null;
    }

    /**
     * Converts the "Clone with SSH" Git URL to the "Clone with HTTPS" Git URL so Jenkins can actually clone it
     *
     * @param gitSSHUrl The "Clone with SSH" Git URL
     * @return The "Clone with HTTPS" Git URL
     */
    public static String convertGitSSHUrlToGitHTTPSUrl(String gitSSHUrl) {
        return gitSSHUrl != null ? gitSSHUrl.replace("git@github.com:", "https://github.com/") : null;
    }

    /**
     * Removes the leading slash from the absolute path because it is absolute within the Git repository but not the Jenkins file system.  Jenkins will need a relative path.
     *
     * @param uncleanDockerfilePath Example: "/Dockerfile"
     * @return Example: "Dockerfile"
     */
    public static String convertDockstoreAbsolutePathToJenkinsRelativePath(String uncleanDockerfilePath) {
        return uncleanDockerfilePath.replaceFirst("^/", "");
    }
}
