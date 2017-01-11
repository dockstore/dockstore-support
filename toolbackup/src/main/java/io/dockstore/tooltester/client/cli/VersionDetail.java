package io.dockstore.tooltester.client.cli;

/**
 * Created by kcao on 11/01/17.
 */
class VersionDetail {
    private final String version;
    private final String date;
    private final double size;
    private final String creationTime;

    VersionDetail(String _version, String _date, double _size, String _creationDate) {
        this.version = _version;
        this.date = _date;
        this.size = _size;
        this.creationTime = _creationDate;
    }

    public String getVersion() {
        return version;
    }

    public String getDate() {
        return date;
    }

    public double getSize() {
        return size;
    }

    public String getCreationTime() {
        return creationTime;
    }
}
