package io.dockstore.tooltester.report;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.stream.Collectors;

import de.vandermeer.asciitable.v2.RenderedTable;
import de.vandermeer.asciitable.v2.V2_AsciiTable;
import de.vandermeer.asciitable.v2.render.V2_AsciiTableRenderer;
import de.vandermeer.asciitable.v2.render.WidthLongestLine;
import de.vandermeer.asciitable.v2.themes.V2_E_TableThemes;

import static io.dockstore.tooltester.helper.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author gluu
 * @since 23/01/17
 */
public abstract class Report implements Closeable {
    private static final CharSequence COMMA_SEPARATOR = ",";
    private BufferedWriter writer;
    private V2_AsciiTable at;

    Report(String name) {
        at = new V2_AsciiTable();
        List<String> header = getHeader();
        try {

            File file = new File(name);
            this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF_8));
        } catch (FileNotFoundException e) {
            exceptionMessage(e, "Cannot create new file", IO_ERROR);
        }
        printAndWriteLine(header);
    }

    public abstract List<String> getHeader();

    public void printAndWriteLine(List<String> string) {
        writeLine(string);
    }

    public void close() {
        try {
            System.out.println();
            writer.flush();
            writer.close();
            V2_AsciiTableRenderer rend = new V2_AsciiTableRenderer();
            rend.setTheme(V2_E_TableThemes.UTF_LIGHT.get());
            rend.setWidth(new WidthLongestLine());
            at.addRule();
            RenderedTable rt = rend.render(at);
            System.out.println(rt);
        } catch (IOException e) {
            exceptionMessage(e, "Could not close file", IO_ERROR);
        }
    }

    private void writeLine(List<String> values) {
        at.addRule();
        at.addRow(values.toArray());
        String commaSeparatedValues = values.stream().map(Object::toString).collect(Collectors.joining(COMMA_SEPARATOR));
        try {
            writer.append(commaSeparatedValues);
            writer.append("\n");
        } catch (IOException e) {
            exceptionMessage(e, "Cannot write to file", IO_ERROR);
        }
    }
}

