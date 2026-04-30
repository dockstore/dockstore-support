package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import io.dockstore.utils.ai.AIModel.AIResponseInfo;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreeStageOntologyHandler implements OntologyHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ThreeStageOntologyHandler.class);

    private final Ontology ontology;

    ThreeStageOntologyHandler(Ontology ontology) {
        this.ontology = ontology;
    }

    @Override
    public List<String> categorize(AIModel aiModel, String entryType, String trsId, String description, String descriptorFileContent) {
        String summary = summarize(aiModel, entryType, trsId, description, descriptorFileContent);
        return classify(aiModel, summary).stream().filter(this::gate).filter(id -> validate(id, summary, aiModel)).toList();
    }

    private String summarize(AIModel aiModel, String entryType, String trsId, String description, String descriptorFile) {
        String prompt = "";
        prompt += "You are a scientist and genomics and bioinformatics expert.  Summarize the purpose and functionality of the following workflow in 200 words or less.  Omit the workflow's name.  Be terse and use scientific terminology.";
        prompt += "\n<trsId>\n";
        prompt += trsId;
        prompt += "\n</trsId>\n";
        prompt += "\n<description>\n";
        prompt += description;
        prompt += "\n</description>\n";
        prompt += "\n<code>\n";
        prompt += descriptorFile;
        prompt += "\n</code>\n";
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 400);
        return "<description>\n" + aiResponseInfo.aiResponse() + "\n</description>";
    }

    private List<String> classify(AIModel aiModel, String summary) {
        String prompt = createPrompt(summary);
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 300);
        String response = aiResponseInfo.aiResponse();
        return Arrays.asList(response.split("\n"));
    }

    private boolean gate(String id) {
        Ontology.Node node = ontology.getNodeById(id);
        if (node == null) {
            LOG.info("HALLUCINATED {}", id);
            return false;
        }
        if (!node.recommendedForAnnotation()) {
            LOG.info("NON-CATEGORICAL {}", id);
            return false;
        }
        return true;
    }

    private boolean validate(String id, String summary, AIModel aiModel) {
        Ontology.Node node = ontology.getNodeById(id);
        String prompt = "You are a scientist and genomics and bioinformatics expert.\n";
        boolean isGeneric = isGenericOperation(node.id());
        prompt += "Given the following workflow description:\n";
        prompt += summary;
        prompt += "\n\n";
        prompt += createValidationQuestion(isGeneric);
        prompt += "Answer \"yes\" or \"no\" with no other text.\n";
        prompt += "\"" + node.label() + "\": " + node.definition();
        prompt += "\n";
        AIResponseInfo aiResponseInfo = aiModel.submitPrompt(prompt, 0.0, 5);
        String response = aiResponseInfo.aiResponse();
        boolean validated = response.length() > 0 && response.substring(0, 1).toLowerCase().equals("y");
        LOG.info("VALIDATED {} {}", id, validated);
        return validated;
    }

    private boolean isGenericOperation(String id) {
        return ontology.getAncestors(id).stream().anyMatch(ancestor -> ancestor.id().equals("operation-data-handling"))
            || id.equals("operation-read-mapping")
            || id.equals("operation-read-pre-processing");
    }

    private String createValidationQuestion(boolean isGeneric) {
        return isGeneric
            ? "Is the following operation the sole purpose of the workflow?\n"
            : "Does the workflow perform the following operation, and is it the purpose or an important capability of the workflow?\n";
    }

    private String createPrompt(String summary) {
        String prompt = "";
        prompt += "You are a scientist and genomics and bioinformatics expert.\n";
        prompt += createClassifyGoal();
        prompt += "\n";
        prompt += summary;
        prompt += "\n\n";
        prompt += createClassifySelectionCriteria();
        prompt += createOntologyListXml();
        return prompt;
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

    private String createOntologyListXml() {
        return "<operation-csv>\n" + CategorizerClient.createOntologyCsv(ontology) + "</operation-csv>\n";
    }
}
