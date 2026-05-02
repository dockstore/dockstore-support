package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import io.dockstore.utils.ai.AIModel.AIResponseInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreeStageOntologyHandler implements OntologyHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ThreeStageOntologyHandler.class);

    private final Ontology ontology;
    private final String prefix;
    private final AIModel aiModel;

    ThreeStageOntologyHandler(Ontology ontology, String prefix, AIModel aiModel) {
        this.ontology = ontology;
        this.prefix = prefix;
        this.aiModel = aiModel;
    }

    @Override
    public List<Ontology.Node> handlesNodes() {
        return ontology.getNodes().stream().filter(node -> node.id().startsWith(prefix)).toList();
    }

    @Override
    public List<Ontology.Node> categorizeIntoNodes(List<Ontology.Node> nodes, String entryType, String trsId, String description, String descriptorFileContent) {
        String summary = summarize(entryType, trsId, description, descriptorFileContent);
        Set<String> nodeIds = nodes.stream().map(Ontology.Node::id).collect(Collectors.toSet());
        return classify(nodes, summary).stream()
            .filter(id -> gate(id, nodeIds))
            .filter(id -> validate(id, summary, aiModel))
            .map(id -> ontology.getNodeById(id))
            .toList();
    }

    private String summarize(String entryType, String trsId, String description, String descriptorFile) {
        String prompt = joinLines(
            createIdentityStatement(),
            createSummarizeInstruction(),
            tagged("trsId", trsId),
            tagged("description", description),
            tagged("code", descriptorFile)
        );
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 400);
        String summarySlug = createSummarySlug();
        String summary = aiResponseInfo.aiResponse();
        return tagged(summarySlug, summary);
    }

    private List<String> classify(List<Ontology.Node> nodes, String summary) {
        String prompt = createClassificationPrompt(nodes, summary);
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 300);
        String response = aiResponseInfo.aiResponse();
        return Arrays.asList(response.split("\n"));
    }

    private boolean gate(String id, Set<String> nodeIds) {
        if (!nodeIds.contains(id)) {
            LOG.info("HALLUCINATED {}", id);
            return false;
        }
        return true;
    }

    private boolean validate(String id, String summary, AIModel aiModel) {
        Ontology.Node node = ontology.getNodeById(id);
        boolean isGeneric = isGenericNode(node);
        String prompt = joinLines(
            createIdentityStatement(),
            "Given the following workflow description:",
            summary,
            "",
            createValidationQuestion(isGeneric),
            "Answer \"yes\" or \"no\" with no other text.\n",
            "\"" + node.label() + "\": " + node.definition()
        );
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 5);
        String response = aiResponseInfo.aiResponse();
        boolean validated = response.length() > 0 && response.substring(0, 1).toLowerCase().equals("y");
        LOG.info("VALIDATED {} {}", id, validated);
        return validated;
    }

    private boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return ontology.getAncestors(id).stream().anyMatch(ancestor -> ancestor.id().equals("operation-data-handling"))
            || id.equals("operation-read-mapping")
            || id.equals("operation-read-pre-processing");
    }

    private String createValidationQuestion(boolean isGeneric) {
        return isGeneric
            ? "Is the following operation the sole purpose of the workflow?\n"
            : "Does the workflow perform the following operation, and is it the purpose or an important capability of the workflow?\n";
    }

    private String createClassificationPrompt(List<Ontology.Node> nodes, String summary) {
        String slug = createOntologyTypeSlug();
        String prompt = joinLines(
            createIdentityStatement(),
            createClassifyGoal(),
            summary,
            "",
            createClassifySelectionCriteria(),
            "<%s-csv>".formatted(slug),
            createOntologyCsv(nodes),
            "</%s-csv>".formatted(slug)
        );
        return prompt;
    }

    private String createOntologyTypeSlug() {
        return "operation";
    }

    private String createSummarySlug() {
        return "description";
    }

    private String createSummarizeInstruction() {
        return "Summarize the purpose and functionality of the following workflow in 200 words or less."
            + "  Omit the workflow's name.  Be terse and use scientific terminology.";
    }

    private String createClassifyGoal() {
        return "Your goal is to determine the operations performed by the following workflow:\n";
    }

    private String createClassifySelectionCriteria() {
        return "From the following list, select the operations that the workflow performs.\n"
            + "Prefer operations that summarize the purpose or functionality of the workflow as a whole.\n"
            + "Prefer operations that differentiate the workflow from other workflows.\n"
            + "Prefer operations that are very specific.\n"
            + "Output one operation ID per line and no other text.\n";
    }

    private String createIdentityStatement() {
        return "You are a scientist and genomics and bioinformatics expert.\n";
    }

    private static String createOntologyCsv(List<Ontology.Node> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,name,description\n");
        for (Ontology.Node node : nodes) {
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

    private static String tagged(String tagName, String content) {
        return joinLines("<" + tagName + ">", content, "</ " + tagName + ">");
    }

    private static String joinLines(String... values) {
        return String.join("\n", values);
    }
}
