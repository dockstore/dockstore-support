package io.dockstore.tooltester.jenkins;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
/**
 * @author gluu
 * @since 15/02/17
 */
public class OutputFile {
    @SerializedName("checksum")
    @Expose
    private String checksum;
    @SerializedName("basename")
    @Expose
    private String basename;
    @SerializedName("size")
    @Expose
    private Integer size;

    public String getChecksum() {
        return checksum;
    }

    public Integer getSize() {
        return size;
    }

    public String getBasename() {
        return basename;
    }
}
