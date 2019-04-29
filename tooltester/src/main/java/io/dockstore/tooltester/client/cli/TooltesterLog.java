package io.dockstore.tooltester.client.cli;

/**
 * @author gluu
 * @since 23/04/19
 */
public class TooltesterLog {
    private String toolId;
    private String toolVersionName;
    private String testFilename;
    private String runner;
    private LogType logType;
    private String filename;

    public TooltesterLog(String toolId, String toolVersionName, String testFilename, String runner, LogType logType, String filename) {
        this.toolId = toolId;
        this.toolVersionName = toolVersionName;
        this.testFilename = testFilename;
        this.runner = runner;
        this.logType = logType;
        this.filename = filename;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getToolVersionName() {
        return toolVersionName;
    }

    public void setToolVersionName(String toolVersionName) {
        this.toolVersionName = toolVersionName;
    }

    public String getTestFilename() {
        return testFilename;
    }

    public void setTestFilename(String testFilename) {
        this.testFilename = testFilename;
    }

    public String getRunner() {
        return runner;
    }

    public void setRunner(String runner) {
        this.runner = runner;
    }

    public LogType getLogType() {
        return logType;
    }

    public void setLogType(LogType logType) {
        this.logType = logType;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}


