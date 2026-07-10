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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ontology handler that implements a three-stage process, consisting of the following steps:
 * <ol>
 *   <li><b>Summarize</b>: Distills the entry's type, TRS ID, descriptor content, and description
 *       into a compact summary using an AI prompt.</li>
 *   <li><b>Classify</b>: Presents the candidate ontology nodes (as a CSV of id, name, description)
 *       and the summary to the AI, which returns a list of matching node IDs.  IDs that do not
 *       correspond to any candidate node are discarded as hallucinations.</li>
 *   <li><b>Verify</b>: For each node returned by the classify step, run a yes/no AI prompt that
 *       asks if the summary matches the node, filtering out any node that does not match.</li>
 * </ol>
 */
public abstract class ThreeStageOntologyHandler implements OntologyHandler {
    /**
     * Suggested maximum number of tokens in the summary generated during the "summarize" step.
     * At 1.5 tokens per word, this would accomodate slightly over 300 words.
     */
    protected static final int MAX_SUMMARIZE_TOKENS = 500;
    /**
     * Suggested maximum number of tokens in the list of IDs produced by the "classify" step.
     * At ten tokens per ID, this would accomodate about 20 IDs.
     */
    protected static final int MAX_CLASSIFY_TOKENS = 200;
    /**
     * Suggested maximum number of tokens in the yes/no answer to the "verify" step.
     */
    protected static final int MAX_VERIFY_TOKENS = 5;

    private static final String BLANK = "";
    private static final Logger LOG = LoggerFactory.getLogger(ThreeStageOntologyHandler.class);

    private final String rootId;

    protected ThreeStageOntologyHandler(String rootId) {
        this.rootId = rootId;
    }

    protected String getRootId() {
        return rootId;
    }

    @Override
    public String getName() {
        return rootId;
    }

    @Override
    public List<Ontology.Node> coverage(Ontology ontology) {
        // TODO: change this to a scan that includes all ontology nodes with an ancestor corresponding to the rootId.
        return ontology.getNodes().stream().filter(node -> rootId.equals(node.id()) || node.id().startsWith(rootId + "-")).toList();
    }

    @Override
    public List<Ontology.Node> categorize(List<Ontology.Node> nodes, EntryData entryData, AIModel aiModel) {
        if (nodes.isEmpty()) {
            return List.of();
        }
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

    protected abstract String sayPluralPhrase(EntryData entryData);

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
                BLANK,
                sayEntry(entryData),
                BLANK
            ))
            .cache()
            .text(lines(
                saySummarizeInstructions(entryData),
                sayWordLimitAndStyle()
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
                BLANK,
                sayCategories(nodes),
                BLANK
            ))
            .cache()
            .text(lines(
                saySummaryIntro(entryData),
                BLANK,
                saySummary(summary),
                BLANK,
                sayClassifyInstructions(entryData),
                sayOneIdPerLine(),
                saySelectIdsFromTheList()
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
                BLANK,
                saySummary(summary),
                BLANK,
                sayVerifyInstructions(entryData, node),
                sayAnswerYesNo(),
                BLANK,
                sayOntologyNode(node)
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }

    protected String saySelectIdsFromTheList() {
        return "Only include %s IDs from the above CSV.".formatted(rootId.replace("-", " "));
    }

    protected String sayOneIdPerLine() {
        return "Output one %s ID per line and no other text.".formatted(rootId.replace("-", " "));
    }

    protected List<String> sayOntologyNode(Ontology.Node node) {
        return List.of(
            "%s: \"%s\"".formatted(sayOntologyType(), node.label()),
            "Description: \"%s\"".formatted(node.definition())
        );
    }

    protected String sayOntologyType() {
        return StringUtils.capitalize(rootId.replace("-", " "));
    }

    protected abstract List<String> saySummaryIntro(EntryData entryData);

    protected List<String> saySummary(String summary) {
        return tag(rootId + "-description", summary);
    }

    protected String sayAnswerYesNo() {
        return "Answer \"yes\" or \"no\" with no other text.";
    }

    protected static String sayIdentity() {
        return "You are a genomics and bioinformatics expert.";
    }

    protected String sayWordLimitAndStyle() {
        return "Respond with about 200 words.  Be terse.  Use scientific terminology.";
    }

    protected String sayEntryIntro(EntryData entryData) {
        return "Summarize the following %s:".formatted(entryData.entryType());
    }

    protected List<String> sayEntry(EntryData entryData) {
        // TODO: limit the length of these fields
        return concatenate(
            tag("type", entryData.entryType()),
            tag("trsId", entryData.trsId()),
            tag("code", entryData.descriptorFileContent()),
            tag("description", entryData.description())
        );
    }

    protected String sayCategoriesIntro(EntryData entryData) {
        return "Classify the %s into the following categories:".formatted(sayPluralPhrase(entryData));
    }

    protected List<String> sayCategories(List<Ontology.Node> nodes) {
        return tag(rootId + "-csv", csv(nodes));
    }

    private List<String> csv(List<Ontology.Node> nodes) {
        List<String> lines = new ArrayList<>();
        lines.add("id,name,description");
        for (Ontology.Node node: nodes) {
            lines.add(node.id() + "," + escapeCsv(node.label()) + "," + escapeCsv(node.definition()));
        }
        return lines;
    }

    private String escapeCsv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
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

    protected static <T> List<T> concatenate(Iterable<T> a, Iterable<T> b, Iterable<T> c, Iterable<T> d) {
        return Stream.of(a, b, c, d)
            .flatMap(iterable -> StreamSupport.stream(iterable.spliterator(), false))
            .toList();
    }

}
