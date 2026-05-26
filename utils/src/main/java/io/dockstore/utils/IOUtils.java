package io.dockstore.utils;

import io.dockstore.common.S3ClientHelper;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/** Utilities for reading local file paths or S3 URIs. */
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

}
