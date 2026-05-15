package io.dockstore.utils;

import io.dockstore.common.S3ClientHelper;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

public final class IOUtils {

    private IOUtils() {
    }

    public static Reader reader(String path) throws IOException {
        if (path.startsWith("s3://")) {
            final S3Client s3Client = S3ClientHelper.getS3Client();
            final String s3FileKey = path.replace("s3://", "");
            final List<String> s3FileKeyComponents = List.of(s3FileKey.split("/"));
            if (s3FileKeyComponents.size() < 2) {
                throw new IOException("Invalid S3 URI");
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

    public static Iterable<CSVRecord> readCsv(Reader reader, Class<? extends Enum<?>> csvHeaders) throws IOException {
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(csvHeaders)
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        return csvFormat.parse(reader);
    }

    public static Iterable<CSVRecord> readCsv(String path, Class<? extends Enum<?>> csvHeaders) throws IOException {
        Reader reader = reader(path);
        return readCsv(reader, csvHeaders);
    }
}
