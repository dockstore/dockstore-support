package io.dockstore.toolbackup.client.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kcao on 11/01/17.
 */
class VersionDetail {
    private final String version;
    private final String metaVersion;

    private final long dockerSize;                      // docker shows only the virtual size NOT the disk size
    private final long fileSize;                        // disk size still needed to compare with cloud

    private final boolean valid;

    private List<String> timesOfExecution;              // times the script ran but the local file remained the same

    private String path;                                // local file path

    VersionDetail(String version, String metaVersion, long dockerSize, long fileSize, String time, boolean valid, String path) {
        this.version = version;
        this.metaVersion = metaVersion;
        this.dockerSize = dockerSize;
        this.fileSize = fileSize;
        this.timesOfExecution = new ArrayList<String>();
        this.timesOfExecution.add(time);
        this.valid = valid;
        this.path = path;
    }

    public String getVersion() {
        return version;
    }

    public String getMetaVersion() {
        return metaVersion;
    }

    public long getDockerSize() {
        return dockerSize;
    }

    public long getFileSize() {
        return fileSize;
    }

    public List<String> getTimesOfExecution() {
        return timesOfExecution;
    }

    public boolean isValid() {
        return valid;
    }

    public void addTime(String timeOfExecution) {
        timesOfExecution.add(timeOfExecution);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
