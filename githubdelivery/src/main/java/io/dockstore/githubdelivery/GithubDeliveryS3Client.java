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

import static io.dockstore.githubdelivery.GithubDeliveryHelper.getGitHubInstallationRepositoriesPayloadByKey;
import static io.dockstore.githubdelivery.GithubDeliveryHelper.getGitHubPushPayloadByKey;
import static io.dockstore.githubdelivery.GithubDeliveryHelper.getGitHubReleasePayloadByKey;
import static io.dockstore.githubdelivery.GithubDeliveryHelper.logReadError;
import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;
import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.githubdelivery.GithubDeliveryCommandLineArgs.SubmitAllEventsCommand;
import io.dockstore.githubdelivery.GithubDeliveryCommandLineArgs.SubmitAllHourlyEventsCommand;
import io.dockstore.githubdelivery.GithubDeliveryCommandLineArgs.SubmitEventCommand;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.InstallationRepositoriesPayload;
import io.dockstore.openapi.client.model.PushPayload;
import io.dockstore.openapi.client.model.ReleasePayload;
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
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

public class GithubDeliveryS3Client {
    public static final Gson GSON = new Gson();
    public static final String SUBMIT_EVENT_COMMAND = "submit-event";
    public static final String SUBMIT_ALL_COMMAND = "submit-all";
    public static final String SUBMIT_HOUR_COMMAND = "submit-hour";
    private static final Logger LOG = LoggerFactory.getLogger(GithubDeliveryS3Client.class);
    private final S3Client s3Client;
    private final String bucketName;

    public GithubDeliveryS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3ClientHelper.getS3Client();
    }

    public static void main(String[] args) throws IOException {
        final GithubDeliveryCommandLineArgs commandLineArgs = new GithubDeliveryCommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final SubmitEventCommand submitEventCommand = new SubmitEventCommand();
        final SubmitAllEventsCommand submitAllEventsCommand = new SubmitAllEventsCommand();
        final SubmitAllHourlyEventsCommand submitAllHourlyEventsCommand = new SubmitAllHourlyEventsCommand();
        jCommander.addCommand(submitEventCommand);
        jCommander.addCommand(submitAllEventsCommand);
        jCommander.addCommand(submitAllHourlyEventsCommand);

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
            ApiClient apiClient = setupApiClient(githubDeliveryConfig.getDockstoreConfig().serverUrl(), githubDeliveryConfig.getDockstoreConfig().token());
            WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
            if (SUBMIT_EVENT_COMMAND.equals(jCommander.getParsedCommand())) {
                githubDeliveryS3Client.submitGitHubDeliveryEventsByKey(submitEventCommand.getBucketKey(), workflowsApi);
            }
            if (SUBMIT_ALL_COMMAND.equals(jCommander.getParsedCommand())) {
                githubDeliveryS3Client.submitGitHubDeliveryEventsByDate(submitAllEventsCommand.getDate(), workflowsApi);
            }
            if (SUBMIT_HOUR_COMMAND.equals(jCommander.getParsedCommand())) {
                githubDeliveryS3Client.submitGitHubDeliveryEventsByHour(submitAllHourlyEventsCommand.getKey(), workflowsApi);
            }
        }

    }
    private String getObject(String key) throws IOException {
        GetObjectRequest objectRequest = GetObjectRequest
                .builder()
                .key(key)
                .bucket(bucketName)
                .build();
        ResponseInputStream<GetObjectResponse> object = s3Client.getObject(objectRequest);
        return IOUtils.toString(object, StandardCharsets.UTF_8);
    }

    private void submitGitHubDeliveryEventsByDate(String date, WorkflowsApi workflowsApi) {

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request
                .builder()
                .bucket(bucketName)
                .prefix(date)
                .build();
        ListObjectsV2Iterable listObjectsV2Iterable = s3Client.listObjectsV2Paginator(listObjectsV2Request);
        for (ListObjectsV2Response response : listObjectsV2Iterable) {
            response.contents().forEach((S3Object event) -> {
                submitGitHubDeliveryEventsByKey(event.key(), workflowsApi);
            });
        }
        LOG.info("Successfully submitted events for date {}", date);
    }
    private void submitGitHubDeliveryEventsByHour(String prefix, WorkflowsApi workflowsApi) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request
                .builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();
        ListObjectsV2Iterable listObjectsV2Iterable = s3Client.listObjectsV2Paginator(listObjectsV2Request);
        for (ListObjectsV2Response response : listObjectsV2Iterable) {
            response.contents().forEach((S3Object event) -> {
                submitGitHubDeliveryEventsByKey(event.key(), workflowsApi);
            });
        }
        LOG.info("Successfully submitted events for date/hour {}", prefix);
    }
    private void submitGitHubDeliveryEventsByKey(String key, WorkflowsApi workflowsApi) {
        String deliveryid = key.split("/")[2]; //since key is in YYYY-MM-DD/HH/deliveryid format
        try {
            String s3GithubObject = getObject(key);
            JsonObject jsonObject = GSON.fromJson(s3GithubObject, JsonObject.class);
            JsonObject body = jsonObject.get("body").getAsJsonObject();
            String bodyString = body.toString();
            final String eventType = jsonObject.get("eventType").getAsString();
            switch (eventType) {
            case "installation_repositories" -> {
                InstallationRepositoriesPayload payload = getGitHubInstallationRepositoriesPayloadByKey(eventType, bodyString, key);
                if (payload != null) {
                    workflowsApi.handleGitHubInstallation(payload, deliveryid);
                } else {
                    logReadError(eventType, key);
                }
            }
            case "push" -> {
                //push events
                PushPayload payload = getGitHubPushPayloadByKey(eventType, bodyString, key);
                if (payload != null) {
                    if (body.get("deleted").getAsBoolean()) {
                        workflowsApi.handleGitHubBranchDeletion(payload.getRepository().getFullName(), payload.getSender().getLogin(), payload.getRef(), deliveryid, payload.getInstallation().getId());
                    } else {
                        workflowsApi.handleGitHubRelease(payload, deliveryid);
                    }
                } else {
                    logReadError(eventType, key);
                }
            }
            case "release" -> {
                final ReleasePayload releasePayload = getGitHubReleasePayloadByKey(eventType, bodyString, key);
                if (releasePayload != null) {
                    workflowsApi.handleGitHubTaggedRelease(releasePayload, deliveryid);
                } else {
                    logReadError(eventType, key);
                }
            }
            default -> {
                LOG.error("Invalid eventType {} format for key {}", eventType, key);
                return;
            }
            }
            LOG.info("Successfully submitted events for key {}", key);
        } catch (IOException e) {
            exceptionMessage(e, String.format("Could not submit github event from key %s", key), 1);
        } catch (ApiException e) {
            LOG.error("Could not submit github event from key {}", key, e);
        }
    }
}
