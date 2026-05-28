package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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

    private List<Ontology.Node> classify(List<Ontology.Node> nodes, String summary, EntryData entryData, AIModel aiModel) {
        AIModel.Response response = aiModel.submitPrompt(createClassifyPrompt(nodes, summary, entryData));
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
        AIModel.Response response = aiModel.submitPrompt(createVerifyPrompt(node, summary, entryData));
        boolean verified = response.text().length() > 0 && response.text().substring(0, 1).toLowerCase().equals("y");
        LOG.info("VERIFIED {} {}", node.id(), verified);
        return verified;
    }

    @Override
    public abstract String getName();

    protected abstract String getRootId();

    protected abstract String getSingularPhrase();

    protected abstract String getPluralPhrase(EntryData entryData);

    protected abstract List<String> saySummarizeInstructions(EntryData entryData);

    protected abstract List<String> sayClassifyInstructions(EntryData entryData);

    protected abstract List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node);

    protected AIModel.Prompt createSummarizePrompt(EntryData entryData) {
        return AIModel.Prompt.builder()
            .system()
            .text(lines(
                 sayIdentity()
            ))
            .user()
            .text(lines(
                 sayEntryIntro(entryData),
                 sayEntry(entryData),
                ""
            ))
            .cache()
            .text(lines(
                 saySummarizeInstructions(entryData)
            ))
            .outputTokens(MAX_SUMMARIZE_TOKENS)
            .build();
    }

    protected AIModel.Prompt createClassifyPrompt(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        return AIModel.Prompt.builder()
            .system()
            .text(lines(
                 sayIdentity()
            ))
            .user()
            .text(lines(
                sayCategoriesIntro(entryData),
                sayCategories(nodes),
                ""
            ))
            .cache()
            .text(lines(
                saySummaryIntro(entryData),
                saySummary(summary),
                "",
                sayClassifyInstructions(entryData),
                sayOneIdPerLine()
            ))
            .outputTokens(MAX_CLASSIFY_TOKENS)
            .build();
    }

    protected AIModel.Prompt createVerifyPrompt(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system()
            .text(lines(
                sayIdentity()
            ))
            .user()
            .text(lines(
                saySummaryIntro(entryData),
                saySummary(summary),
                "",
                sayVerifyInstructions(entryData, node),
                sayAnswerYesNo(),
                "",
                sayOntologyNode(node)
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }

    protected String sayOneIdPerLine() {
        return "Output one %s-id per line and no other text.".formatted(getRootId());
    }

    protected String sayOntologyNode(Ontology.Node node) {
        return "\"" + node.label() + "\": " + node.definition();
    }

    protected abstract List<String> saySummaryIntro(EntryData entryData);

    protected List<String> saySummary(String summary) {
        return tag(getRootId() + "-description", summary);
    }

    protected String sayAnswerYesNo() {
        return "Answer \"yes\" or \"no\" with no other text.";
    }

    protected static String sayIdentity() {
        return "You are a genomics and bioinformatics expert.";
    }

    protected String sayEntryIntro(EntryData entryData) {
        return "Summarize the following %s:".formatted(entryData.entryType());
    }

    protected List<String> sayEntry(EntryData entryData) {
        return Stream.of(
            tag("type", entryData.entryType()),
            tag("trsId", entryData.trsId()),
            tag("code", entryData.descriptorFileContent()),
            tag("description", entryData.description())
        )
        .flatMap(List::stream)
        .toList();
    }

    protected String sayCategoriesIntro(EntryData entryData) {
        return "Classify the %s into the following categories:".formatted(getPluralPhrase(entryData));
    }

    protected List<String> sayCategories(List<Ontology.Node> nodes) {
        return tag(getRootId() + "-csv", csv(nodes));
    }

    private List<String> csv(List<Ontology.Node> nodes) {
        String prefix = getRootId();
        List<String> lines = new ArrayList<>();
        lines.add("%s-id,%s-name,%s-description".formatted(prefix, prefix, prefix));
        for (Ontology.Node node: nodes) {
            lines.add(escapeCsvField(node.id() + "," + escapeCsvField(node.label()) + "," + escapeCsvField(node.definition())));
        }
        return lines;
    }

    private String escapeCsvField(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private List<String> tag(String tagName, String content) {
        return tag(tagName, List.of(content));
    }

    private List<String> tag(String tagName, List<String> content) {
        List<String> lines = new ArrayList<>();
        lines.add("<%s>".formatted(tagName));
        lines.addAll(content);
        lines.add("</%s>".formatted(tagName));
        return lines;
    }

    private String lines(Object... values) {
        return Arrays.stream(values)
            .flatMap(value -> value instanceof Iterable<?> iterable
                ? StreamSupport.stream(iterable.spliterator(), false).map(Object::toString)
                : Stream.of(value.toString()))
            .collect(Collectors.joining("\n")) + "\n";
    }
}
