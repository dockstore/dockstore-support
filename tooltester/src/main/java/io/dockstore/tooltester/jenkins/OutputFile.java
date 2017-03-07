package io.dockstore.tooltester.jenkins;

<<<<<<< HEAD
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
=======
>>>>>>> d7e8c79... Feature/jenkins example (#5)
/**
 * @author gluu
 * @since 15/02/17
 */
<<<<<<< HEAD
public class OutputFile {
=======

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class OutputFile {

    @SerializedName("format")
    @Expose
    private String format;
>>>>>>> d7e8c79... Feature/jenkins example (#5)
    @SerializedName("checksum")
    @Expose
    private String checksum;
    @SerializedName("basename")
    @Expose
    private String basename;
<<<<<<< HEAD
=======
    @SerializedName("location")
    @Expose
    private String location;
    @SerializedName("path")
    @Expose
    private String path;
>>>>>>> d7e8c79... Feature/jenkins example (#5)
    @SerializedName("size")
    @Expose
    private Integer size;

<<<<<<< HEAD
=======
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

>>>>>>> d7e8c79... Feature/jenkins example (#5)
    public String getChecksum() {
        return checksum;
    }

<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 8353215... Pretty print reports and added documentation
    public Integer getSize() {
        return size;
=======
    public void setChecksum(String checksum) {
        this.checksum = checksum;
>>>>>>> d7e8c79... Feature/jenkins example (#5)
    }

    public String getBasename() {
        return basename;
    }
<<<<<<< HEAD
=======

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

>>>>>>> d7e8c79... Feature/jenkins example (#5)
}
