package io.dockstore.tooltester.Models;

/**
 * @author gluu
 * @since 04/04/19
 */
public class BlackListObject {
    // This is the TRS Tool ID (e.g. #workflow/thing/thing
    private String toolId;
    // This is the TRS ToolVersion name (e.g. 3.0.0)
    private String toolVersionName;
    // Reason why it was blacklisted
    private String reasoning = null;

    public BlackListObject(String toolId, String toolVersionName, String reasoning) {
        this.toolId = toolId;
        this.toolVersionName = toolVersionName;
        this.reasoning = reasoning;
    }

    public BlackListObject(String toolId, String toolVersionName) {
        this.toolId = toolId;
        this.toolVersionName = toolVersionName;
    }

    public String getToolId() {
        return toolId;
    }

    public String getToolVersionName() {
        return toolVersionName;
    }

    public String getReasoning() {
        return reasoning;
    }
}
