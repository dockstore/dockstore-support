package io.dockstore.tooltester.runWorkflow;

import static io.dockstore.tooltester.helper.ExceptionHandler.COMMAND_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;
import static java.util.UUID.randomUUID;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class WorkflowRunnerConfig {

    private String dockstoreToken;
    private String serverUrl;
    private String awsAuthorization;
    private String wdlWesUrl;
    private String cwlWesUrl;
    private String wdlEcsCluster;
    private String cwlEcsCluster;

    private String pathToWdlConfigFIle;
    private String pathToCwlConfigFile;

    public WorkflowRunnerConfig(String configFilePathString)  {
        Path configFilePath = Paths.get(configFilePathString);

        final Yaml safeYaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, String> yamlMap = null;
        try {
            yamlMap = safeYaml.load(Files.readString(configFilePath));
        } catch (IOException e) {
            exceptionMessage(e, "Error loading the yaml " + configFilePath, IO_ERROR);
        }

        this.dockstoreToken = yamlMap.get("TOKEN");
        this.serverUrl = yamlMap.get("SERVER-URL");
        this.awsAuthorization = yamlMap.get("AWS-AUTHORIZATION");
        this.wdlWesUrl = yamlMap.get("WDL-WES-URL");
        this.cwlWesUrl = yamlMap.get("CWL-WES-URL");
        this.wdlEcsCluster = yamlMap.get("WDL-ECS-CLUSTER");
        this.cwlEcsCluster = yamlMap.get("CWL-ECS-CLUSTER");

        createDockstoreCliConfigFiles();
    }

    private String getContentOfCliConfigFile(String wesUrl) {
        String content = "token = " + getDockstoreToken() + System.lineSeparator()
                + "server-url = " + getServerUrl() + System.lineSeparator()
                + "[WES]" + System.lineSeparator()
                + "url: " + wesUrl + System.lineSeparator()
                + "authorization: " + getAwsAuthorization() + System.lineSeparator()
                + "type: aws" + System.lineSeparator();
        return content;
    }

    private void createDockstoreCliConfigFiles()  {
        File wdlCliConfigFile = new File("wdlCliConfigFile-" + randomUUID());
        wdlCliConfigFile.deleteOnExit();

        try (
                BufferedWriter writer = new BufferedWriter(new FileWriter(wdlCliConfigFile.getPath()))
        ) {
            writer.write(getContentOfCliConfigFile(getWdlWesUrl()));
            this.pathToWdlConfigFIle = wdlCliConfigFile.getPath();
        } catch (IOException e) {
            exceptionMessage(e, "Error writing to WDL CLI config file", COMMAND_ERROR);
        }

        File cwlCliConfigFile = new File("cwlCliConfigFile-" + randomUUID());
        cwlCliConfigFile.deleteOnExit();

        try (
                BufferedWriter writer = new BufferedWriter(new FileWriter(cwlCliConfigFile.getPath()))
        ) {
            writer.write(getContentOfCliConfigFile(getCwlWesUrl()));
            this.pathToCwlConfigFile = cwlCliConfigFile.getPath();
        } catch (IOException e) {
            exceptionMessage(e, "Error writing to CWL CLI config file", COMMAND_ERROR);
        }

    }

    public String getDockstoreToken() {
        return dockstoreToken;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    private String getAwsAuthorization() {
        return awsAuthorization;
    }

    private String getWdlWesUrl() {
        return wdlWesUrl;
    }

    private String getCwlWesUrl() {
        return cwlWesUrl;
    }

    public String getWdlEcsCluster() {
        return wdlEcsCluster;
    }

    public String getCwlEcsCluster() {
        return cwlEcsCluster;
    }

    public String getPathToWdlConfigFIle() {
        return pathToWdlConfigFIle;
    }

    public String getPathToCwlConfigFile() {
        return pathToCwlConfigFile;
    }

}
