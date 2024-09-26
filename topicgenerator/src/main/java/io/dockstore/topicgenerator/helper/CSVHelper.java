package io.dockstore.topicgenerator.helper;

import static io.dockstore.utils.ExceptionHandler.IO_ERROR;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.topicgenerator.helper.AIModel.AIResponseInfo;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CSVHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CSVHelper.class);

    private CSVHelper() {
        // Intentionally empty
    }

    public static Iterable<CSVRecord> readFile(String inputCsvFilePath, Class<? extends Enum<?>> csvHeaders) {
        // Read CSV file
        Iterable<CSVRecord> csvRecords = null;
        try {
            final Reader entriesCsv = new FileReader(inputCsvFilePath);
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(csvHeaders)
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build();
            csvRecords = csvFormat.parse(entriesCsv);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read input CSV file", IO_ERROR);
        }
        return csvRecords;
    }

    public static void writeRecord(CSVPrinter csvPrinter, String trsId, String versionId, FileWrapper descriptorFile, AIResponseInfo aiResponseInfo) {
        String descriptorChecksum = descriptorFile.getChecksum().isEmpty() ? "" : descriptorFile.getChecksum().get(0).getChecksum();
        try {
            csvPrinter.printRecord(trsId, versionId, descriptorFile.getUrl(), descriptorChecksum, aiResponseInfo.isTruncated(), aiResponseInfo.inputTokens(), aiResponseInfo.outputTokens(), aiResponseInfo.cost(), aiResponseInfo.stopReason(), aiResponseInfo.aiTopic());
        } catch (IOException e) {
            LOG.error("Unable to write CSV record to file, skipping", e);
        }
    }
}
