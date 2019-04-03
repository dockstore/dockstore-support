package io.dockstore.tooltester.helper;

import java.util.ArrayList;

import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;

/**
 * @author gluu
 * @since 03/04/19
 */
public final class DockstoreEntryHelper {
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
        String command = String.join(" ", commandList);
        return command;
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
        String command = String.join(" ", commandList);
        return command;
    }




}
