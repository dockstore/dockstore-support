package io.dockstore.githubdelivery;

import static io.dockstore.githubdelivery.GithubDeliveryHelper.getPayloadByKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.openapi.client.model.ReleasePayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class GithubDeliveryHelperTest {

    @Test
    void testPayloadGetByKey() throws IOException {
        final ReleasePayload payload = getPayloadByKey("release", readResourceAsString("/release.json"), "akey", ReleasePayload.class);
        assertEquals("coverbeck", payload.getSender().getLogin());
        assertEquals("1.0.0", payload.getRelease().getTagName());
    }

    private String readResourceAsString(String resource) throws IOException {
        try (var in = getClass().getResourceAsStream(resource)) {
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        }
    }
}
