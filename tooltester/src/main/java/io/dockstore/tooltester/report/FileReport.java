package io.dockstore.tooltester.report;

import java.util.Arrays;
import java.util.List;

/**
 * @author gluu
 * @since 23/01/17
 */
public class FileReport extends Report {

    private static final List<String> HEADER = Arrays.asList("Build ID", "Tag", "CWL ID", "File Name", "md5sum", "File Size");

    public FileReport(String name) {
        super(name);
    }

    @Override
    public List<String> getHeader() {
        return HEADER;
    }
}
