package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import io.dockstore.utils.ai.AIModel.AIResponseInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ThreeStageOntologyHandler implements OntologyHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ThreeStageOntologyHandler.class);

    private final String prefix;

    ThreeStageOntologyHandler(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public List<Ontology.Node> handlesNodes(Ontology ontology) {
        return ontology.getNodes().stream().filter(node -> node.id().startsWith(prefix)).toList();
    }

    @Override
    public List<Ontology.Node> categorizeIntoNodes(List<Ontology.Node> nodes, EntryData entryData, AIModel aiModel) {
        String summary = summarize(entryData, aiModel);
        List<Ontology.Node> matches = classify(nodes, summary, entryData, aiModel);
        return validate(matches, summary, entryData, aiModel);
    }

    private String summarize(EntryData entryData, AIModel aiModel) {
        String prompt = joinLines(
            createIdentityStatement(),
            createSummarizeInstruction(entryData)
        );
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 500);
        return aiResponseInfo.aiResponse();
    }

    protected abstract String createSummarizeInstruction(EntryData entryData);

    protected String formatEntryInformation(EntryData entryData) {
        return joinLines(
            tag("trsId", entryData.trsId()),
            tag("description", entryData.description()),
            tag("code", entryData.descriptorFileContent())
        );
    }

    private List<Ontology.Node> classify(List<Ontology.Node> nodes, String summary, EntryData entryData, AIModel aiModel) {
        String prompt = joinLines(
            createIdentityStatement(),
            createClassifyInstruction(nodes, summary, entryData)
        );

        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 200);
        String response = aiResponseInfo.aiResponse();
        List<String> ids = Arrays.stream(response.split("\n")).map(String::trim).toList();
        return filterHallucinations(ids, nodes);
    }

    private List<Ontology.Node> filterHallucinations(List<String> ids, List<Ontology.Node> nodes) {
        Map<String, Ontology.Node> candidateIdToNode = nodes.stream().collect(Collectors.toMap(Ontology.Node::id, node -> node));
        return ids.stream().filter(candidateIdToNode::containsKey).map(candidateIdToNode::get).toList();
    }

    private List<Ontology.Node> validate(List<Ontology.Node> nodes, String summary, EntryData entryData, AIModel aiModel) {
        return nodes.stream().filter(node -> validate(node, summary, entryData, aiModel)).toList();
    }

    private boolean validate(Ontology.Node node, String summary, EntryData entryData, AIModel aiModel) {
        String prompt = joinLines(
            createIdentityStatement(),
            createValidateInstruction(node, summary, entryData)
        );
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 5);
        String response = aiResponseInfo.aiResponse();
        boolean validated = response.length() > 0 && response.substring(0, 1).toLowerCase().equals("y");
        LOG.info("VALIDATED {} {}", node.id(), validated);
        return validated;
    }

    protected abstract String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData);

    protected abstract String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData);

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
