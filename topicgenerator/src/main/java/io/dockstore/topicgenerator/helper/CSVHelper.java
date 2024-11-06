package io.dockstore.topicgenerator.helper;

import static io.dockstore.utils.ExceptionHandler.IO_ERROR;
import static io.dockstore.utils.ExceptionHandler.errorMessage;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import io.dockstore.common.S3ClientHelper;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.topicgenerator.helper.BaseAIModel.AIResponseInfo;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public final class CSVHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CSVHelper.class);

    private CSVHelper() {
        // Intentionally empty
    }

    public static CSVPrinter createCsvPrinter(String fileName, Class<? extends Enum<?>> csvHeaders) throws IOException {
        return new CSVPrinter(new FileWriter(fileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(csvHeaders).build());
    }

    public static Iterable<CSVRecord> readFile(String inputCsvFilePath, Class<? extends Enum<?>> csvHeaders) {
        // Read CSV file
        Iterable<CSVRecord> csvRecords = null;
        try {
            final Reader entriesCsv = new FileReader(inputCsvFilePath);
            csvRecords = parseCsvRecords(entriesCsv, csvHeaders);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read input CSV file", IO_ERROR);
        }
        return csvRecords;
    }

    public static Iterable<CSVRecord> readS3File(String s3FileUri, Class<? extends Enum<?>> csvHeaders) {
        final S3Client s3Client = S3ClientHelper.getS3Client();
        final String s3FileKey = s3FileUri.replace("s3://", "");
        final List<String> s3FileKeyComponents = List.of(s3FileKey.split("/"));

        if (s3FileKeyComponents.size() < 2) {
            errorMessage("Invalid S3 URI", IO_ERROR);
        }

        final String bucketName = s3FileKeyComponents.get(0);
        final String fileKey = String.join("/", s3FileKeyComponents.subList(1, s3FileKeyComponents.size()));

        final GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();
        ResponseInputStream<GetObjectResponse> getObjectResponse = s3Client.getObject(getObjectRequest);
        final InputStreamReader streamReader = new InputStreamReader(getObjectResponse, StandardCharsets.UTF_8);
        return parseCsvRecords(streamReader, csvHeaders);
    }

    public static Iterable<CSVRecord> parseCsvRecords(Reader reader, Class<? extends Enum<?>> csvHeaders) {
        Iterable<CSVRecord> csvRecords = null;
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(csvHeaders)
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        try {
            csvRecords = csvFormat.parse(reader);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read input CSV file from S3", IO_ERROR);
        }
        return csvRecords;
    }

    public static void writeRecord(CSVPrinter csvPrinter, String trsId, String versionId) {
        try {
            csvPrinter.printRecord(trsId, versionId);
        } catch (IOException e) {
            LOG.error("Unable to write CSV record to file, skipping", e);
        }
    }

    public static void writeRecord(CSVPrinter csvPrinter, String trsId, String versionId, FileWrapper descriptorFile, AIResponseInfo aiResponseInfo) {
        String descriptorChecksum = descriptorFile.getChecksum().isEmpty() ? "" : descriptorFile.getChecksum().get(0).getChecksum();
        try {
            csvPrinter.printRecord(trsId, versionId, descriptorFile.getUrl(), descriptorChecksum, aiResponseInfo.isTruncated(), aiResponseInfo.inputTokens(), aiResponseInfo.outputTokens(), aiResponseInfo.cost(), aiResponseInfo.stopReason(), aiResponseInfo.aiResponse());
        } catch (IOException e) {
            LOG.error("Unable to write CSV record to file, skipping", e);
        }
    }
}
