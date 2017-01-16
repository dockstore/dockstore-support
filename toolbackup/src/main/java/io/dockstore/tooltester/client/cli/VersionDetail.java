package io.dockstore.tooltester.client.cli;

/**
 * Created by kcao on 11/01/17.
 */
class VersionDetail {
    private final String version;
    private final String metaVersion;
    private final double size;
    private final String creationTime;
    private final boolean valid;
    private final String scriptTime;

    VersionDetail(String version, String metaVersion, double size, String creationDate, boolean valid) {
        this.version = version;
        this.metaVersion = metaVersion;
        this.size = size;
        this.creationTime = creationDate;
        this.valid = valid;
        scriptTime = FormattedTimeGenerator.getFormattedTimeNow();
    }

    public String getVersion() {
        return version;
    }

    public String getMetaVersion() {
        return metaVersion;
    }

    public double getSize() {
        return size;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public boolean isValid() {
        return valid;
    }

    public String getScriptTime() {
        return scriptTime;
    }
}
