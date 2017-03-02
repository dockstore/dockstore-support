package io.dockstore.tooltester.client.cli;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gluu
 * @since 23/01/17
 */
class FileReport extends Report {
    private static final int ID = 0;
    private static final int TAG = 1;
    private static final int NAME = 2;
    private static final int MD5SUM = 3;
    private static final int FILESIZE = 4;
    private static final List<String> HEADER = Arrays.asList("Build ID", "Tag", "File Name", "md5sum", "File Size");

    FileReport(String name) {
        super(name);
    }

    @Override
    public List<String> getHeader() {
        return HEADER;
    }

    public void printLine(List<String> values) {
        if (values.size() == HEADER.size()) {
            System.out.printf("\n%-10s %-15s %-45s %-60s %-20s", values.get(ID), values.get(TAG), values.get(NAME), values.get(MD5SUM),
                    values.get(FILESIZE));
        } else {
            System.out.println(values.stream().map(Object::toString).collect(Collectors.joining(TAB_SEPARATOR)));
        }
    }
}
