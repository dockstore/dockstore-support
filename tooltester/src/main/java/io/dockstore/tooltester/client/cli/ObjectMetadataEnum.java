package io.dockstore.tooltester.client.cli;

/**
 * TODO: Figure out how to share this between ToolTester and this
 * @author gluu
 * @since 24/04/19
 */
public enum ObjectMetadataEnum {
    TOOL_ID("tool_id"),
    VERSION_NAME("version_name"),
    TEST_FILE_PATH("test_file_path"),
    RUNNER("runner");

    private String metadataKey;

    ObjectMetadataEnum(String metadata) {
        this.metadataKey = metadata;
    }

    public String getMetadataKey() {
        return metadataKey;
    }

    @Override
    public String toString() {
        return metadataKey;
    }
}
