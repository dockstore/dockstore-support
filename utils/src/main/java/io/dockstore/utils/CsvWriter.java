package io.dockstore.utils;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

/** Generic CSV writer that serializes POJOs to rows with a header row derived from the class schema. */
public class CsvWriter<T> implements Closeable {

    private final SequenceWriter sequenceWriter;

    public CsvWriter(Writer writer, Class<T> pojoClass) throws IOException {
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = mapper.schemaFor(pojoClass).withHeader();
        this.sequenceWriter = mapper.writerFor(pojoClass).with(schema).writeValues(writer);
    }

    public void write(T element) throws IOException {
        sequenceWriter.write(element);
    }

    public void writeAll(Iterable<? extends T> elements) throws IOException {
        sequenceWriter.writeAll(elements);
    }

    @Override
    public void close() throws IOException {
        sequenceWriter.close();
    }
}
