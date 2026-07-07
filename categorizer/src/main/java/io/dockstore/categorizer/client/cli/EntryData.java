package io.dockstore.categorizer.client.cli;

import org.apache.commons.lang3.StringUtils;

public record EntryData(String entryType, String trsId, String description, String descriptorFileContent) {
    public EntryData limit(int maxFieldLength) {
        return new EntryData(
            StringUtils.truncate(entryType, maxFieldLength),
            StringUtils.truncate(trsId, maxFieldLength),
            StringUtils.truncate(description, maxFieldLength),
            StringUtils.truncate(descriptorFileContent, maxFieldLength)
        );
    }
}
