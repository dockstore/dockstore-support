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
    private static final Logger LOG = LoggerFactory.getLogger(ThreeStageOntologyHandler.class);
    private static final int MAX_SUMMARIZE_TOKENS = 500;
    private static final int MAX_CLASSIFY_TOKENS = 200;
    private static final int MAX_VERIFY_TOKENS = 5;

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
        String prompt = joinLines(
            createIdentityStatement(),
            createSummarizeInstruction(entryData)
        );
        AIModel.Response response = aiModel.submitPrompt(AIModel.Prompt.builder().text(prompt).outputTokens(MAX_SUMMARIZE_TOKENS).build());
        return response.text();
    }

    @Override
    public abstract String getName();

    protected abstract String createSummarizeInstruction(EntryData entryData);

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
        String prompt = joinLines(
            createIdentityStatement(),
            createClassifyInstruction(nodes, summary, entryData)
        );

        AIModel.Response response = aiModel.submitPrompt(AIModel.Prompt.builder().text(prompt).outputTokens(MAX_CLASSIFY_TOKENS).build());
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
        String prompt = joinLines(
            createIdentityStatement(),
            createVerifyInstruction(node, summary, entryData)
        );
        AIModel.Response response = aiModel.submitPrompt(AIModel.Prompt.builder().text(prompt).outputTokens(MAX_VERIFY_TOKENS).build());
        boolean verified = response.text().length() > 0 && response.text().substring(0, 1).toLowerCase().equals("y");
        LOG.info("VERIFIED {} {}", node.id(), verified);
        return verified;
    }

    protected abstract String createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData);

    protected abstract String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData);

    private String createIdentityStatement() {
        return "You are a scientist and genomics and bioinformatics expert.\n";
    }

    protected static String createTaggedOntologyCsv(List<Ontology.Node> nodes, String prefix) {
        String tagName = prefix + "csv";
        return tag(tagName, createOntologyCsv(nodes, prefix));
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
    protected static String createOntologyCsv(List<Ontology.Node> nodes, String prefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("%sid,%sname,%sdescription\n".formatted(prefix, prefix, prefix));
        for (Ontology.Node node: nodes) {
            sb.append(escapeCsvField(node.id())).append(",")
                .append(escapeCsvField(node.label())).append(",")
                .append(escapeCsvField(node.definition())).append("\n");
        }
        return sb.toString();
    }

    private static String escapeCsvField(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    protected static String tag(String tagName, String content) {
        return joinLines("<%s>".formatted(tagName), content, "</%s>".formatted(tagName));
    }

    protected static String joinLines(String... values) {
        return String.join("\n", values);
    }
}
