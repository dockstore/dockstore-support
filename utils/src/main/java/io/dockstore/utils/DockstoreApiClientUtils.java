package io.dockstore.utils;

import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.Configuration;

public final class DockstoreApiClientUtils {
    private DockstoreApiClientUtils() {
    }

    public static ApiClient setupApiClient(String serverUrl, String token) {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(serverUrl);
        apiClient.addDefaultHeader("Authorization", "Bearer " + token);
        return apiClient;
    }
}
