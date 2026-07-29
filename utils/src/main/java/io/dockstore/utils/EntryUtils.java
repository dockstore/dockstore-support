package io.dockstore.utils;

import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.NextflowUtilities.NextflowParsingException;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.ToolVersion.DescriptorTypeEnum;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities for retrieving Dockstore entry information via the GA4GH TRS API. */
public final class EntryUtils {
    private static final Logger LOG = LoggerFactory.getLogger(EntryUtils.class);

    private EntryUtils() {
    }

    public static Optional<FileWrapper> retrievePrimaryDescriptor(ApiClient apiClient, String trsId, String versionName) throws ApiException {
        final Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(apiClient);
        final Tool tool = ga4Ghv20Api.toolsIdGet(trsId);
        final List<ToolVersion> filteredVersion = tool.getVersions().stream()
                .filter(v -> v.getName().equals(versionName)).toList();
        if (filteredVersion.isEmpty()) {
            return Optional.empty();
        }
        final ToolVersion version = filteredVersion.get(0);
        return Optional.of(getDescriptorFile(ga4Ghv20Api, trsId, versionName, version.getDescriptorType()));
    }

    private static FileWrapper getDescriptorFile(Ga4Ghv20Api ga4Ghv20Api, String trsId, String versionId, List<DescriptorTypeEnum> descriptorTypes) throws ApiException {
        FileWrapper descriptorFile = null;
        for (int i = 0; i < descriptorTypes.size(); ++i) {
            DescriptorTypeEnum descriptorType = descriptorTypes.get(i);
            try {
                descriptorFile = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorGet(trsId, descriptorType.toString(), versionId);
            } catch (ApiException ex) {
                if (i == descriptorTypes.size() - 1) {
                    throw ex;
                }
                continue;
            }

            if (descriptorType == DescriptorTypeEnum.NFL) {
                Optional<FileWrapper> nextflowMainScript = getNextflowMainScript(descriptorFile.getContent(), ga4Ghv20Api, trsId, versionId, descriptorType);
                if (nextflowMainScript.isPresent()) {
                    descriptorFile = nextflowMainScript.get();
                }
            }
        }

        return descriptorFile;
    }

    private static Optional<FileWrapper> getNextflowMainScript(String nextflowConfigFileContent, Ga4Ghv20Api ga4Ghv20Api, String trsId, String versionId, DescriptorTypeEnum descriptorType) {
        final String mainScriptPath;
        try {
            mainScriptPath = NextflowUtilities.grabConfig(nextflowConfigFileContent).getString("manifest.mainScript", "main.nf");
        } catch (NextflowParsingException e) {
            LOG.error("Could not grab config", e);
            return Optional.empty();
        }
        try {
            return Optional.of(ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(trsId, descriptorType.toString(), versionId, mainScriptPath));
        } catch (ApiException exception) {
            LOG.error("Could not get Nextflow main script {}", mainScriptPath, exception);
            return Optional.empty();
        }
    }
}
