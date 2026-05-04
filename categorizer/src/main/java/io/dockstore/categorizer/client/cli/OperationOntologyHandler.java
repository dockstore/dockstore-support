package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.List;

public class OperationOntologyHandler extends ThreeStageOntologyHandler {

    OperationOntologyHandler(Ontology ontology, AIModel aiModel) {
        super(ontology, "operation-", aiModel);
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        return joinLines(
            "Summarize the purpose and functionality of the following %s in 200 words or less.".formatted(entryData.entryType()),
            "Omit the %s's name.  Be terse and use scientific terminology.".formatted(entryData.entryType()),
            formatEntryInformation(entryData)
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        return joinLines(
            "Your goal is to determine the operations performed by the following %s:".formatted(entryData.entryType()),
            summary,
            "From the following list, select the operations that the %s performs.".formatted(entryData.entryType()),
            "Prefer operations that summarize the purpose or functionality of the %s as a whole.".formatted(entryData.entryType()),
            "Prefer operations that differentiate the %s from other %ss.".formatted(entryData.entryType(), entryData.entryType()),
            "Prefer operations that are very specific.",
            "Output one operation ID per line and no other text.",
            createTaggedOntologyCsv(nodes)
        );
    }

    private boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return node.ontology().getAncestors(id).stream().anyMatch(ancestor -> ancestor.id().equals("operation-data-handling"))
            || id.equals("operation-read-mapping")
            || id.equals("operation-read-pre-processing");
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        boolean isGeneric = isGenericNode(node);
        return joinLines(
            "Given the following %s description:".formatted(entryData.entryType()),
            summary,
            "",
            createValidationQuestion(isGeneric),
            "Answer \"yes\" or \"no\" with no other text.\n",
            "\"" + node.label() + "\": " + node.definition()
        );
    }
}
