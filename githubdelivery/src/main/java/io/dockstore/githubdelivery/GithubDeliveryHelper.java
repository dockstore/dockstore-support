package io.dockstore.githubdelivery;

import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonSyntaxException;
import io.dockstore.openapi.client.model.InstallationRepositoriesPayload;
import io.dockstore.openapi.client.model.PushPayload;
import io.dockstore.openapi.client.model.ReleasePayload;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

final class GithubDeliveryHelper {

    private static final Logger LOG = LoggerFactory.getLogger(GithubDeliveryS3Client.class);
    
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private GithubDeliveryHelper() {
    }

    static PushPayload getGitHubPushPayloadByKey(String eventType, String body, String key) throws IOException, NoSuchKeyException {
        return GithubDeliveryHelper.getPayloadByKey(eventType, body, key, PushPayload.class);
    }

    static InstallationRepositoriesPayload getGitHubInstallationRepositoriesPayloadByKey(String eventType, String body, String key) throws IOException, NoSuchKeyException {
        return GithubDeliveryHelper.getPayloadByKey(eventType, body, key, InstallationRepositoriesPayload.class);
    }

    static ReleasePayload getGitHubReleasePayloadByKey(String eventType, String body, String key) throws IOException, NoSuchKeyException {
        return GithubDeliveryHelper.getPayloadByKey(eventType, body, key, ReleasePayload.class);
    }

    static <T> T getPayloadByKey(String eventType, String body, String key, Class<T> clazz) throws IOException,
        NoSuchKeyException {
        try {
            T payload = MAPPER.readValue(body, clazz);
            if (payload == null) {
                logReadError(eventType, key);
            }
            return payload;
        } catch (JsonSyntaxException e) {
            exceptionReadError(e, eventType, key);
        }
        return null;
    }

    static void logReadError(String eventType, String key) {
        LOG.error("Could not read github {} event from key {}", eventType, key);
    }
    
    static void exceptionReadError(Exception e, String eventType, String key) {
        exceptionMessage(e, String.format("Could not read github %s event from key %s", eventType, key), 1);
    }

}
