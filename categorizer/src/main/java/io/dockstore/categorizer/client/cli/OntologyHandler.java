package io.dockstore.categorizer.client.cli;

import io.dockstore.utils.ai.AIModel;
import java.util.List;

public interface OntologyHandler {
    List<String> categorize(AIModel aiModel, String entryType, String trsId, String description, String descriptorFileContent);
}
