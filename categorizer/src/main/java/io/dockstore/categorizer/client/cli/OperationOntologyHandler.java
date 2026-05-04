package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class OperationOntologyHandler extends ThreeStageOntologyHandler {

    OperationOntologyHandler() {
        super("operation-");
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Summarize the purpose and functionality of the following %s in 200 words or less.".formatted(entryType),
            "Omit the %s's name.  Be terse and use scientific terminology.".formatted(entryType),
            formatEntryData(entryData)
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Your goal is to determine the operations performed by the following %s:".formatted(entryType),
            tag("description", summary),
            "From the following list, select the operations that the %s performs.".formatted(entryType),
            "Prefer operations that summarize the purpose or functionality of the %s as a whole.".formatted(entryType),
            "Prefer operations that differentiate the %s from other %ss.".formatted(entryType, entryType),
            "Prefer operations that are very specific.",
            "Output one operation ID per line and no other text.",
            createTaggedOntologyCsv(nodes, "operation-")
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        String question = isGenericNode(node)
            ? "Is the following operation the sole purpose of the %s?".formatted(entryType)
            : "Does the %s perform the following operation, and is it the purpose or an important capability of the %s?\n".formatted(entryType, entryType);

        return joinLines(
            "Given the following %s description:".formatted(entryType),
            tag("description", summary),
            "",
            question,
            "Answer \"yes\" or \"no\" with no other text.\n",
            "\"" + node.label() + "\": " + node.definition()
        );
    }

    private boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return node.ontology().getAncestors(id).stream().anyMatch(ancestor -> ancestor.id().equals("operation-data-handling"))
            || id.equals("operation-read-mapping")
            || id.equals("operation-read-pre-processing");
    }

}
