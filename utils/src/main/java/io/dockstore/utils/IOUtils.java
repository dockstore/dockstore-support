package io.dockstore.utils;

import static io.dockstore.utils.ExceptionHandler.IO_ERROR;
import static io.dockstore.utils.ExceptionHandler.errorMessage;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import io.dockstore.common.S3ClientHelper;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

public final class IOUtils {

    private IOUtils() {
    }

    public static Reader read(String path) throws IOException {
        if (path.startsWith("s3://")) {
            final S3Client s3Client = S3ClientHelper.getS3Client();
            final String s3FileKey = path.replace("s3://", "");
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
            return new InputStreamReader(s3Client.getObject(getObjectRequest), StandardCharsets.UTF_8);
        } else {
            return new FileReader(path);
        }
    }

    public static List<CSVRecord> readCsv(String path, Class<? extends Enum<?>> csvHeaders) throws IOException {
        List<CSVRecord> csvRecords = new ArrayList<>();
        for (CSVRecord record : parseCsvRecords(read(path), csvHeaders)) {
            csvRecords.add(record);
        }
        return csvRecords;
    }

    private static Iterable<CSVRecord> parseCsvRecords(Reader reader, Class<? extends Enum<?>> csvHeaders) {
        Iterable<CSVRecord> csvRecords = null;
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(csvHeaders)
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        try {
            csvRecords = csvFormat.parse(reader);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read input CSV file", IO_ERROR);
        }
        return csvRecords;
    }
}
