package io.dockstore.utils;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;

/** Generic CSV reader that maps rows to POJOs using the header row as a schema. */
public class CsvReader<T> implements Iterable<T>, Closeable {

    private final MappingIterator<T> iterator;
    private boolean iteratorRetrieved = false;

    public CsvReader(Reader reader, Class<T> pojoClass) throws IOException {
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = mapper.schemaFor(pojoClass).withHeader();
        this.iterator = mapper.readerFor(pojoClass).with(schema).readValues(reader);
    }

    @Override
    public Iterator<T> iterator() {
        if (iteratorRetrieved) {
            throw new IllegalStateException("iterator already retrieved");
        }
        iteratorRetrieved = true;
        return iterator;
    }

    public List<T> readAll() throws IOException {
        return iterator.readAll();
    }

    @Override
    public void close() throws IOException {
        iterator.close();
    }
}
