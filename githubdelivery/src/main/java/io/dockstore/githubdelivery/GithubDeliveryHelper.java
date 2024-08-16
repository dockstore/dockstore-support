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

/**
 * Helper for deserializing GitHub events in JSON to Java objects, as well as some logging
 * methods.
 */
final class GithubDeliveryHelper {

    private static final Logger LOG = LoggerFactory.getLogger(GithubDeliveryHelper.class);
    
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private GithubDeliveryHelper() {
    }

    static PushPayload getGitHubPushPayloadByKey(String eventType, String body, String key) throws IOException {
        return getPayloadByKey(eventType, body, key, PushPayload.class);
    }

    static InstallationRepositoriesPayload getGitHubInstallationRepositoriesPayloadByKey(String eventType, String body, String key) throws IOException {
        return getPayloadByKey(eventType, body, key, InstallationRepositoriesPayload.class);
    }

    static ReleasePayload getGitHubReleasePayloadByKey(String eventType, String body, String key) throws IOException {
        return getPayloadByKey(eventType, body, key, ReleasePayload.class);
    }

    /**
     * Deserializes JSON into an object of type <code>T</code>. Returns <code>null</code> if it
     * fails.
     *
     * @param eventType the event type, e.g., push, release, only used for logging an error
     * @param body the JSON to deserialize
     * @param key the AWS S3 key where the body came from, only used for logging an error
     * @param clazz - the Java class to deserialize to
     * @return
     * @param <T>
     * @throws IOException
     * @throws NoSuchKeyException
     */
    static <T> T getPayloadByKey(String eventType, String body, String key, Class<T> clazz) throws IOException {
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
