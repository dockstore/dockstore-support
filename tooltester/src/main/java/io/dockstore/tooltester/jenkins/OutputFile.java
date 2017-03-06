package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 15/02/17
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class OutputFile {

    @SerializedName("format")
    @Expose
    private String format;
    @SerializedName("checksum")
    @Expose
    private String checksum;
    @SerializedName("basename")
    @Expose
    private String basename;
    @SerializedName("location")
    @Expose
    private String location;
    @SerializedName("path")
    @Expose
    private String path;
    @SerializedName("size")
    @Expose
    private Integer size;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getBasename() {
        return basename;
    }

    public void setBasename(String basename) {
        this.basename = basename;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

}
