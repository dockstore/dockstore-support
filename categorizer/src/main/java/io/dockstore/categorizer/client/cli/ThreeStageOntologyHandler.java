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

    @Override
    public List<Ontology.Node> coverage(Ontology ontology) {
        String rootId = getRootId();
        return ontology.getNodes().stream().filter(node -> rootId.equals(node.id()) || node.id().startsWith(rootId + "-")).toList();
    }

    @Override
    public List<Ontology.Node> categorize(List<Ontology.Node> nodes, EntryData entryData, AIModel aiModel) {
        String summary = summarize(entryData, aiModel);
        List<Ontology.Node> matches = classify(nodes, summary, entryData, aiModel);
        return verify(matches, summary, entryData, aiModel);
    }

    private String summarize(EntryData entryData, AIModel aiModel) {
        AIModel.Response response = aiModel.submitPrompt(createSummarizePrompt(entryData));
        return response.text();
    }

    @Override
    public abstract String getName();

    protected abstract String getRootId();

    protected abstract String getSingularPhrase();

    protected abstract String getPluralPhrase(EntryData entryData);

    protected abstract String getSummarizeCommand(EntryData entryData);

    protected abstract String getSummaryDescription(EntryData entryData);

    protected abstract String getClassifyCommand(EntryData entryData);

    protected abstract String getVerifyQuestion(EntryData entryData, Ontology.Node node);

    protected AIModel.Prompt createSummarizePrompt(EntryData entryData) {
        return AIModel.Prompt.builder()
            .system().text(stateIdentity())
            .user().text(presentEntry(entryData)).cache()
            .text(getSummarizeCommand(entryData))
            .outputTokens(MAX_SUMMARIZE_TOKENS)
            .build();
    }

    private List<Ontology.Node> classify(List<Ontology.Node> nodes, String summary, EntryData entryData, AIModel aiModel) {
        AIModel.Response response = aiModel.submitPrompt(createClassifyPrompt(nodes, summary, entryData));
        List<String> ids = Arrays.stream(response.text().split("\n")).map(String::trim).distinct().toList();
        return filterHallucinations(ids, nodes);
    }

    protected AIModel.Prompt createClassifyPrompt(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        return AIModel.Prompt.builder()
            .system().text(stateIdentity())
            .user().text(presentCategories(nodes, getPluralPhrase(entryData))).cache()
            .text(lines(
                "", "",
                getSummaryDescription(entryData),
                tag(getRootId() + "-description", summary),
                "",
                getClassifyCommand(entryData),
                "Output one %s ID per line and no other text.".formatted(getSingularPhrase())
            ))
            .outputTokens(MAX_CLASSIFY_TOKENS)
            .build();
    }

    private List<Ontology.Node> filterHallucinations(List<String> ids, List<Ontology.Node> nodes) {
        Map<String, Ontology.Node> candidateIdToNode = nodes.stream().collect(Collectors.toMap(Ontology.Node::id, node -> node));
        return ids.stream().filter(candidateIdToNode::containsKey).map(candidateIdToNode::get).toList();
    }

    private List<Ontology.Node> verify(List<Ontology.Node> nodes, String summary, EntryData entryData, AIModel aiModel) {
        return nodes.stream().filter(node -> verify(node, summary, entryData, aiModel)).toList();
    }

    private boolean verify(Ontology.Node node, String summary, EntryData entryData, AIModel aiModel) {
        AIModel.Response response = aiModel.submitPrompt(createVerifyPrompt(node, summary, entryData));
        boolean verified = response.text().length() > 0 && response.text().substring(0, 1).toLowerCase().equals("y");
        LOG.info("VERIFIED {} {}", node.id(), verified);
        return verified;
    }

    protected AIModel.Prompt createVerifyPrompt(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(stateIdentity())
            .user().text(lines(
                "Given the following %s description:".formatted(entryType),
                tag("description", summary),
                "",
                getVerifyQuestion(entryData, node),
                "",
                "\"" + node.label() + "\": " + node.definition(),
                "",
                "Answer \"yes\" or \"no\" with no other text."
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }

    protected static String stateIdentity() {
        return "You are a genomics and bioinformatics expert.\n";
    }

    protected String presentEntry(EntryData entryData) {
        return lines(
            "Summarize the following %s:".formatted(entryData.entryType()),
            "",
            tag("type", entryData.entryType()),
            tag("trsId", entryData.trsId()),
            tag("code", entryData.descriptorFileContent()),
            tag("description", entryData.description())
        );
    }

    protected String presentCategories(List<Ontology.Node> nodes, String what) {
        return
            "Classify the %s into the following categories:\n".formatted(what)
            + "\n"
            + "<%s-csv>\n".formatted(getRootId())
            + createOntologyCsv(nodes)
            + "</%s-csv>\n".formatted(getRootId());
    }

    // TODO: investigate 3rd party library
    protected String createOntologyCsv(List<Ontology.Node> nodes) {
        String rootId = getRootId();
        StringBuilder sb = new StringBuilder();
        sb.append("%s-id,%s-name,%s-description\n".formatted(rootId, rootId, rootId));
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
        return lines("<%s>".formatted(tagName), content, "</%s>".formatted(tagName));
    }

    protected String lines(String... values) {
        return String.join("\n", values) + "\n";
    }
}
