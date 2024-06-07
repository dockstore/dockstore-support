/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.githubdelivery;

import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;
import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonSyntaxException;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.githubdelivery.GithubDeliveryCommandLineArgs.DownloadEventCommand;
import io.dockstore.githubdelivery.GithubDeliveryCommandLineArgs.SubmitAllEventsCommand;
import io.dockstore.githubdelivery.GithubDeliveryCommandLineArgs.SubmitEventCommand;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.PushPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

public class GithubDeliveryS3Client {
    public static final String DOWNLOAD_EVENT_COMMAND = "download-event";
    public static final String SUBMIT_EVENT_COMMAND = "submit-event";
    public static final String SUBMIT_ALL_COMMAND = "submit-all";
    private static final Logger LOG = LoggerFactory.getLogger(GithubDeliveryS3Client.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
        final SubmitEventCommand submitEventCommand = new SubmitEventCommand();
        final SubmitAllEventsCommand submitAllEventsCommand = new SubmitAllEventsCommand();
        jCommander.addCommand(downloadEventCommand);
        jCommander.addCommand(submitEventCommand);
        jCommander.addCommand(submitAllEventsCommand);

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

            if (DOWNLOAD_EVENT_COMMAND.equals(jCommander.getParsedCommand())) {
                System.out.println(githubDeliveryS3Client.getGitHubDeliveryEventByKey(downloadEventCommand.getBucketKey()));
            }
            if (SUBMIT_EVENT_COMMAND.equals(jCommander.getParsedCommand())) {
                githubDeliveryS3Client.submitGitHubDeliveryEventsByKey(githubDeliveryConfig, submitEventCommand.getBucketKey());
            }
            if (SUBMIT_ALL_COMMAND.equals(jCommander.getParsedCommand())) {
                githubDeliveryS3Client.submitGitHubDeliveryEventsByDate(githubDeliveryConfig, submitAllEventsCommand.getDate());
            }
        }

    }
    private PushPayload getGitHubDeliveryEventByKey(String key) throws IOException, NoSuchKeyException {
        GetObjectRequest objectRequest = GetObjectRequest
                .builder()
                .key(key)
                .bucket(bucketName)
                .build();
        ResponseInputStream<GetObjectResponse> object = s3Client.getObject(objectRequest);

        try {
            PushPayload pushPayload;
            pushPayload = MAPPER.readValue(IOUtils.toString(object, StandardCharsets.UTF_8), PushPayload.class);
            return pushPayload;
        } catch (JsonSyntaxException e) {
            LOG.error("Could not read github event from key {}", key, e);
            System.exit(1);
        }
        return null;
    }
    private void submitGitHubDeliveryEventsByDate(GithubDeliveryConfig config, String date) {

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request
                .builder()
                .bucket(bucketName)
                .prefix(date)
                .build();
        ListObjectsV2Iterable listObjectsV2Iterable = s3Client.listObjectsV2Paginator(listObjectsV2Request);

        ApiClient apiClient = setupApiClient(config.getDockstoreConfig().serverUrl(), config.getDockstoreConfig().token());
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);

        for (ListObjectsV2Response response : listObjectsV2Iterable) {
            response.contents().forEach((S3Object event) -> {
                try {
                    String deliveryid = event.key().split("/")[1]; //since key is in YYYY-MM-DD/deliveryid format
                    PushPayload body = getGitHubDeliveryEventByKey(event.key());
                    workflowsApi.handleGitHubRelease(body, deliveryid);
                } catch (IOException e) {
                    LOG.error("Could not submit github event from key {}", event.key(), e);
                    System.exit(1);
                }
            });
        }
    }
    private void submitGitHubDeliveryEventsByKey(GithubDeliveryConfig config, String key) {
        ApiClient apiClient = setupApiClient(config.getDockstoreConfig().serverUrl(), config.getDockstoreConfig().token());
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        try {
            String deliveryid = key.split("/")[1]; //since key is in YYYY-MM-DD/deliveryid format
            PushPayload body = getGitHubDeliveryEventByKey(key);
            System.out.println(body);
            workflowsApi.handleGitHubRelease(body, deliveryid);
        } catch (IOException e) {
            LOG.error("Could not submit github event from key {}", key, e);
            System.exit(1);
        }
    }
}
