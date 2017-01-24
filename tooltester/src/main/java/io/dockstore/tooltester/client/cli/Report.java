package io.dockstore.tooltester.client.cli;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * @author gluu
 * @since 23/01/17
 */
class Report {
    private static final char COMMA_SEPARATOR = ',';
    //private static final char TAB_SEPERATOR = '\t';
    private static final List<String> HEADER = Arrays.asList("ID", "DATE", "Tool/Workflow ID", "Version", "Location of testing", "Parameter file", "Runtime", "Status of Test Files", "Status of Dockerfiles");

    private Writer writer;

    Report(String name) {

        try {
            this.writer = new OutputStreamWriter(new FileOutputStream(name), StandardCharsets.UTF_8);
            writeLine(HEADER);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void writeLine(List<String> values){

        boolean first = true;
        StringBuilder sb = new StringBuilder();
        for (String value: values) {
            if (!first) {
                sb.append(COMMA_SEPARATOR);
            }
            sb.append(value);
            first = false;
        }
        try {
            sb.append("\n");
            this.writer.append(sb.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void closeReport(){
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
