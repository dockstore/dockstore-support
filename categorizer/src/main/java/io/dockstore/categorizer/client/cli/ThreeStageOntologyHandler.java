package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ThreeStageOntologyHandler implements OntologyHandler {
    protected static final int MAX_SUMMARIZE_TOKENS = 500;
    protected static final int MAX_CLASSIFY_TOKENS = 200;
    protected static final int MAX_VERIFY_TOKENS = 5;

    private static final Logger LOG = LoggerFactory.getLogger(ThreeStageOntologyHandler.class);
    private final String prefix;

    ThreeStageOntologyHandler(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public List<Ontology.Node> coverage(Ontology ontology) {
        return ontology.getNodes().stream().filter(node -> prefix.equals(node.id()) || node.id().startsWith(prefix + "-")).toList();
    }

    @Override
    public List<Ontology.Node> categorize(List<Ontology.Node> nodes, EntryData entryData, AIModel aiModel) {
        String summary = summarize(entryData, aiModel);
        List<Ontology.Node> matches = classify(nodes, summary, entryData, aiModel);
        return verify(matches, summary, entryData, aiModel);
    }

    private String summarize(EntryData entryData, AIModel aiModel) {
        AIModel.Response response = aiModel.submitPrompt(createSummarizeInstruction(entryData));
        return response.text();
    }

    @Override
    public abstract String getName();

    protected abstract AIModel.Prompt createSummarizeInstruction(EntryData entryData);

    protected String formatEntryData(EntryData entryData) {
        // TODO: Limit some of these?
        return joinLines(
            tag("type", entryData.entryType()),
            tag("trsId", entryData.trsId()),
            tag("code", entryData.descriptorFileContent()),
            tag("description", entryData.description())
        );
    }

    private List<Ontology.Node> classify(List<Ontology.Node> nodes, String summary, EntryData entryData, AIModel aiModel) {
        AIModel.Response response = aiModel.submitPrompt(createClassifyInstruction(nodes, summary, entryData));
        List<String> ids = Arrays.stream(response.text().split("\n")).map(String::trim).distinct().toList();
        return filterHallucinations(ids, nodes);
    }

    private List<Ontology.Node> filterHallucinations(List<String> ids, List<Ontology.Node> nodes) {
        Map<String, Ontology.Node> candidateIdToNode = nodes.stream().collect(Collectors.toMap(Ontology.Node::id, node -> node));
        return ids.stream().filter(candidateIdToNode::containsKey).map(candidateIdToNode::get).toList();
    }

    private List<Ontology.Node> verify(List<Ontology.Node> nodes, String summary, EntryData entryData, AIModel aiModel) {
        return nodes.stream().filter(node -> verify(node, summary, entryData, aiModel)).toList();
    }

    private boolean verify(Ontology.Node node, String summary, EntryData entryData, AIModel aiModel) {
        AIModel.Response response = aiModel.submitPrompt(createVerifyInstruction(node, summary, entryData));
        boolean verified = response.text().length() > 0 && response.text().substring(0, 1).toLowerCase().equals("y");
        LOG.info("VERIFIED {} {}", node.id(), verified);
        return verified;
    }

    protected abstract AIModel.Prompt createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData);

    protected abstract AIModel.Prompt createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData);

    protected static String stateIdentity() {
        return "You are a genomics and bioinformatics expert.";
    }

    protected String presentEntry(EntryData entryData) {
        return joinLines(
            "Summarize the following %s:".formatted(entryData.entryType()),
             formatEntryData(entryData),
            ""
        );
    }

    protected String presentCategories(List<Ontology.Node> nodes, String what, String csvPrefix) {
        return joinLines(
            "Classify the %s into the following categories:".formatted(what),
            createTaggedOntologyCsv(nodes, csvPrefix),
            ""
        );
    }

    protected String createTaggedOntologyCsv(List<Ontology.Node> nodes, String csvPrefix) {
        String tagName = csvPrefix + "csv";
        return tag(tagName, createOntologyCsv(nodes, csvPrefix));
    }

    /*
    protected static String createOntologyMarkdownTable(List<Ontology.Node> nodes, String idHeader, String labelHeader, String definitionHeader) {
        StringBuilder sb = new StringBuilder();
        sb.append("| %s | %s | %s |\n".formatted(idHeader, labelHeader, definitionHeader));
        sb.append("| --- | --- | --- |\n");
        for (Ontology.Node node: nodes) {
            sb.append("| %s | %s | %s |\n".formatted(node.id(), node.label(), node.definition()));
        }
        return sb.toString();
    }
    */

    // TODO: investigate 3rd party library
    protected String createOntologyCsv(List<Ontology.Node> nodes, String csvPrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("%sid,%sname,%sdescription\n".formatted(csvPrefix, csvPrefix, csvPrefix));
        for (Ontology.Node node: nodes) {
            sb.append(escapeCsvField(node.id())).append(",")
                .append(escapeCsvField(node.label())).append(",")
                .append(escapeCsvField(node.definition())).append("\n");
        }
        return sb.toString();
    }

    private String escapeCsvField(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    protected String tag(String tagName, String content) {
        return joinLines("<%s>".formatted(tagName), content, "</%s>".formatted(tagName));
    }

    protected String joinLines(String... values) {
        return String.join("\n", values);
    }
}
