package io.dockstore.utils;

import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.Configuration;

/** Utilities for constructing authenticated Dockstore API clients. */
public final class DockstoreApiClientUtils {
    private DockstoreApiClientUtils() {
    }

    public static ApiClient setupApiClient(String serverUrl) {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(serverUrl);
        return apiClient;
    }

    public static ApiClient setupApiClient(String serverUrl, String token) {
        ApiClient apiClient = setupApiClient(serverUrl);
        apiClient.addDefaultHeader("Authorization", "Bearer " + token);
        return apiClient;
    }
}
