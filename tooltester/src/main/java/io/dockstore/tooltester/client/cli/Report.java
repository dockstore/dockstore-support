package io.dockstore.tooltester.client.cli;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;

import static io.dockstore.tooltester.client.cli.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.exceptionMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author gluu
 * @since 23/01/17
 */
class Report implements Closeable {
    private static final char COMMA_SEPARATOR = ',';
    //private static final char TAB_SEPERATOR = '\t';
    private static final List<String> HEADER = Arrays
            .asList("Tool/Workflow ID", "DATE", "Version", "Location of testing", "Action Performed", "Runtime", "Status of Test Files");
    private BufferedWriter writer;

    Report(String name) {
        try {
            File file = new File("target/" + name);
            this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF_8));
        } catch (FileNotFoundException e) {
            exceptionMessage(e, "Cannot create new file", IO_ERROR);
        }
        writeLine(HEADER);
    }

    void writeLine(List<String> values) {
        boolean first = true;
        try {
            for (String value : values) {
                if (!first) {
                    this.writer.append(COMMA_SEPARATOR);
                }
                this.writer.append(value);
                first = false;
            }
            this.writer.append("\n");
        } catch (IOException e) {
            exceptionMessage(e, "Cannot write to file", IO_ERROR);
        }
    }

    @Override
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            exceptionMessage(e, "Could not close file", IO_ERROR);
        }
    }
}
