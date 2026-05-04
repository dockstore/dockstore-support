package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import io.dockstore.utils.ai.AIModel.AIResponseInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ThreeStageOntologyHandler implements OntologyHandler {
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
        List<Ontology.Node> matches = classify(nodes, summary);
        return validate(matches, summary);
    }

    private String summarize(String entryType, String trsId, String description, String descriptorFile) {
        String prompt = joinLines(
            createIdentityStatement(),
            createSummarizeInstruction(entryType, trsId, description, descriptorFile)
        );
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 400);
        String summarySlug = createSummarySlug();
        String summary = aiResponseInfo.aiResponse();
        return tag(summarySlug, summary);
    }

    protected abstract String createSummarizeInstruction(String entryType, String trsId, String description, String descriptorFile);

    protected String formatEntryInformation(String entryType, String trsId, String description, String descriptorFile) {
        return joinLines(
            tag("trsId", trsId),
            tag("description", description),
            tag("code", descriptorFile)
        );
    }

    private List<Ontology.Node> classify(List<Ontology.Node> nodes, String summary) {
        String prompt = joinLines(
            createIdentityStatement(),
            createClassifyInstruction(nodes, summary)
        );

        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 300);
        String response = aiResponseInfo.aiResponse();
        List<String> ids = Arrays.asList(response.split("\n"));
        // TODO fix this code to only include a subset of the originally-specified "nodes"
        return ids.stream().map(this::map).filter(Objects::nonNull).toList();
    }

    private Ontology.Node map(String id) {
        Ontology.Node node = ontology.getNodeById(id);
        if (node == null) {
            LOG.info("HALLUCINATED {}", id);
        }
        return node;
    }

    private List<Ontology.Node> validate(List<Ontology.Node> nodes, String summary) {
        return nodes.stream().filter(node -> validate(node, summary)).toList();
    }

    private boolean validate(Ontology.Node node, String summary) {
        String prompt = joinLines(
            createIdentityStatement(),
            createValidateInstruction(node, summary)
        );
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 5);
        String response = aiResponseInfo.aiResponse();
        boolean validated = response.length() > 0 && response.substring(0, 1).toLowerCase().equals("y");
        LOG.info("VALIDATED {} {}", node.id(), validated);
        return validated;
    }

    protected abstract String createValidateInstruction(Ontology.Node node, String summary);

    protected boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return ontology.getAncestors(id).stream().anyMatch(ancestor -> ancestor.id().equals("operation-data-handling"))
            || id.equals("operation-read-mapping")
            || id.equals("operation-read-pre-processing");
    }

    protected String createValidationQuestion(boolean isGeneric) {
        return isGeneric
            ? "Is the following operation the sole purpose of the workflow?\n"
            : "Does the workflow perform the following operation, and is it the purpose or an important capability of the workflow?\n";
    }

    protected String createOntologyTypeSlug() {
        return "operation";
    }

    private String createSummarySlug() {
        return "description";
    }

    protected String createTaggedOntologyCsv(List<Ontology.Node> nodes) {
        String slug = createOntologyTypeSlug();
        String tagName = "%s-csv".formatted(slug);
        String csv = createOntologyCsv(nodes);
        return tag(tagName, csv);
    }

    protected abstract String createClassifyInstruction(List<Ontology.Node> nodes, String summary);

    private String createIdentityStatement() {
        return "You are a scientist and genomics and bioinformatics expert.\n";
    }

    protected static String createOntologyCsv(List<Ontology.Node> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,name,description\n");
        for (Ontology.Node node : nodes) {
            sb.append(escapeCsvField(node.id())).append(",")
                .append(escapeCsvField(node.label())).append(",")
                .append(escapeCsvField(node.definition())).append("\n");
        }
        return sb.toString();
    }

    protected static String escapeCsvField(String value) {
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
