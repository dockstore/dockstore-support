package io.dockstore.tooltester.report;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gluu
 * @since 23/01/17
 */
public class StatusReport extends Report {
    private static final int ID = 0;
    private static final int DATE = 1;
    private static final int VERSION = 2;
    private static final int LOCATION = 3;
    private static final int ACTION = 4;
    private static final int RUNTIME = 5;
    private static final int STATUS = 6;
    private static final List<String> HEADER = Arrays
            .asList("Tool/Workflow ID", "DATE", "Version", "Location of testing", "Action Performed", "Runtime", "Status of Test Files");

    public StatusReport(String name) {
        super(name);
    }

    @Override
    public List<String> getHeader() {
        return HEADER;
    }

    public void printLine(List<String> values) {
        if (values.size() == HEADER.size()) {
            System.out.printf("\n%-55s %-25s %-20s %-20s %-40s %-15s %-20s", values.get(ID), values.get(DATE), values.get(VERSION),
                    values.get(LOCATION), values.get(ACTION), values.get(RUNTIME), values.get(STATUS));
        } else {
            System.out.println(values.stream().map(Object::toString).collect(Collectors.joining(TAB_SEPARATOR)));
        }
    }
}
