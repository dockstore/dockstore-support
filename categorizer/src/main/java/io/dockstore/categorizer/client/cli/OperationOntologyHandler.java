package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.List;

public class OperationOntologyHandler extends ThreeStageOntologyHandler {

    OperationOntologyHandler(Ontology ontology, AIModel aiModel) {
        super(ontology, "operation-", aiModel);
    }

    @Override
    protected String createSummarizeInstruction(String entryType, String trsId, String description, String descriptorFile) {
        return joinLines(
            "Summarize the purpose and functionality of the following workflow in 200 words or less.",
            "Omit the workflow's name.  Be terse and use scientific terminology.",
            formatEntryInformation(entryType, trsId, description, descriptorFile)
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary) {
        return joinLines(
            "Your goal is to determine the operations performed by the following workflow:",
            summary,
            "From the following list, select the operations that the workflow performs.",
            "Prefer operations that summarize the purpose or functionality of the workflow as a whole.",
            "Prefer operations that differentiate the workflow from other workflows.",
            "Prefer operations that are very specific.",
            "Output one operation ID per line and no other text.",
            createTaggedOntologyCsv(nodes)
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary) {
        boolean isGeneric = isGenericNode(node);
        return joinLines(
            "Given the following workflow description:",
            summary,
            "",
            createValidationQuestion(isGeneric),
            "Answer \"yes\" or \"no\" with no other text.\n",
            "\"" + node.label() + "\": " + node.definition()
        );
    }
}
