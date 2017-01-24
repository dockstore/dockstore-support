package io.dockstore.tooltester.client.cli;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static io.dockstore.tooltester.client.cli.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.client.cli.ExceptionHandler.exceptionMessage;

/**
 * @author gluu
 * @since 23/01/17
 */
class Report implements Closeable {
    private static final char COMMA_SEPARATOR = ',';
    //private static final char TAB_SEPERATOR = '\t';
    private static final List<String> HEADER = Arrays
            .asList("Tool/Workflow ID", "DATE", "Version", "Location of testing", "Parameter file", "Runtime", "Status of Test Files",
                    "Status of Dockerfiles");
    private Writer writer;

    Report(String name) {
        try {
            this.writer = new OutputStreamWriter(new FileOutputStream(name), StandardCharsets.UTF_8);
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
            writer.close();
            writer.flush();
        } catch (IOException e) {
            exceptionMessage(e, "Could not close file", IO_ERROR);
        }
    }
}
