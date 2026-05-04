package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.List;

public interface OntologyHandler {
    List<Ontology.Node> handlesNodes(Ontology ontology);
    List<Ontology.Node> categorizeIntoNodes(List<Ontology.Node> nodes, EntryData entryData, AIModel aiModel);
}
