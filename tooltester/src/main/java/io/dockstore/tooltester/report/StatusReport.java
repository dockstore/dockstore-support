package io.dockstore.tooltester.report;

import java.util.Arrays;
import java.util.List;

/**
 * @author gluu
 * @since 23/01/17
 */
public class StatusReport extends Report {
    private static final List<String> HEADER = Arrays
            .asList("Tool/Workflow ID", "DATE", "Version", "Engine", "Action Performed", "Runtime", "Status of Test Files", "Log");

    public StatusReport(String name) {
        super(name);
    }

    @Override
    public List<String> getHeader() {
        return HEADER;
    }
}
