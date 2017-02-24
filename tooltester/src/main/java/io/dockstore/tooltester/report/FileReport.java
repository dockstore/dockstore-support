package io.dockstore.tooltester.report;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gluu
 * @since 23/01/17
 */
public class FileReport extends Report {
    private static final int ID = 0;
    private static final int TAG = 1;
    private static final int NAME = 2;
    private static final int BASENAME = 3;
    private static final int MD5SUM = 4;
    private static final int FILESIZE = 5;
    private static final List<String> HEADER = Arrays.asList("Build ID", "Tag", "CWL ID", "File Name", "md5sum", "File Size");
    private static final List<Integer> MAXLENGTH = Arrays.asList(10, 15, 45, 25, 60, 20);

    public FileReport(String name) {
        super(name);
    }

    @Override
    public List<String> getHeader() {
        return HEADER;
    }

    public void printLine(List<String> values) {
        if (values.size() == HEADER.size()) {
            System.out.printf("\n%-" + MAXLENGTH.get(ID) + "s %-" + MAXLENGTH.get(TAG) + "s %-" + MAXLENGTH.get(NAME) + "s %-" + MAXLENGTH
                            .get(BASENAME) + "s %-" + MAXLENGTH.get(MD5SUM) + "s %-" + MAXLENGTH.get(FILESIZE) + "s", values.get(ID),
                    values.get(TAG), values.get(BASENAME), values.get(NAME), values.get(MD5SUM), values.get(FILESIZE));
        } else {
            System.out.println(values.stream().map(Object::toString).collect(Collectors.joining(TAB_SEPARATOR)));
        }
    }
}
