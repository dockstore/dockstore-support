package io.dockstore.tooltester;


import io.dockstore.openapi.client.model.ToolVersion;

/**
 * @author gluu
 * @since 07/05/19
 */
public class CommandObject {
    String toolId;
    ToolVersion toolVersion;
    String runnner;

    public CommandObject(String toolId, ToolVersion toolVersion, String runnner) {
        this.toolId = toolId;
        this.toolVersion = toolVersion;
        this.runnner = runnner;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public ToolVersion getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(ToolVersion toolVersion) {
        this.toolVersion = toolVersion;
    }

    public String getRunnner() {
        return runnner;
    }

    public void setRunnner(String runnner) {
        this.runnner = runnner;
    }
}
