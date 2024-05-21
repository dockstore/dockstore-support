package io.dockstore.githubdelivery;

import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.githubdelivery.GithubDeliveryCommandLineArgs.DownloadEventCommand;
import io.dockstore.openapi.client.model.PushPayload;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GithubDeliveryS3Client {
    private static final Logger LOG = LoggerFactory.getLogger(GithubDeliveryS3Client.class);
    private static final Gson GSON = new Gson();
    private final S3Client s3Client;
    private final String bucketName;

    public GithubDeliveryS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.getS3Client();
    }

    public static void main(String[] args) throws IOException {
        final GithubDeliveryCommandLineArgs commandLineArgs = new GithubDeliveryCommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final DownloadEventCommand downloadEventCommand = new DownloadEventCommand();
        jCommander.addCommand(downloadEventCommand);

        try {
            jCommander.parse(args);
        } catch (MissingCommandException e) {
            jCommander.usage();
            if (e.getUnknownCommand().isEmpty()) {
                LOG.error("No command entered");
            } else {
                LOG.error("Unknown command");
            }
            exceptionMessage(e, "The command is missing", GENERIC_ERROR);
        } catch (ParameterException e) {
            jCommander.usage();
            exceptionMessage(e, "Error parsing arguments", GENERIC_ERROR);
        }

        if (jCommander.getParsedCommand() == null || commandLineArgs.isHelp()) {
            jCommander.usage();
        } else {
            final INIConfiguration config = getConfiguration(commandLineArgs.getConfig());
            final GithubDeliveryConfig githubDeliveryConfig = new GithubDeliveryConfig(config);
            final GithubDeliveryS3Client githubDeliveryS3Client = new GithubDeliveryS3Client(githubDeliveryConfig.getS3Config().bucket());

            if ("download-event".equals(jCommander.getParsedCommand())) {
                System.out.println(githubDeliveryS3Client.getGitHubDeliveryEventByKey(downloadEventCommand.getBucketKey()));
            }
        }

    }
    private PushPayload getGitHubDeliveryEventByKey(String key) throws IOException, NoSuchKeyException {
        GetObjectRequest objectRequest = GetObjectRequest
                .builder()
                .key(key)
                .bucket(bucketName)
                .build();
        ResponseInputStream<GetObjectResponse> object = this.s3Client.getObject(objectRequest);

        try {
            PushPayload pushPayload;
            pushPayload = GSON.fromJson(IOUtils.toString(object, StandardCharsets.UTF_8), PushPayload.class);
            return pushPayload;
        } catch (JsonSyntaxException e) {
            LOG.error("Could not read github event from key {}", key, e);
        }
        return null;
    }
}
