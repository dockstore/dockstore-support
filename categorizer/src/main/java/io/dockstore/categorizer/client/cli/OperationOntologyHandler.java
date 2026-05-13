package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class OperationOntologyHandler extends ThreeStageOntologyHandler {

    OperationOntologyHandler() {
        super("operation");
    }

    @Override
    public String getName() {
        return "Operations";
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
            "Classify the operations performed by the %s into the following categories:".formatted(entryType),
            // createOntologyMarkdownTable(nodes, "Operation ID", "Operation Name", "Operation Description"),
            createTaggedOntologyCsv(nodes, "operation-"),
            "",
            "The %s performs the following operations:".formatted(entryType),
            tag("operations-description", summary),
            "",
            "List the operations that the %s performs.".formatted(entryType),
            "Prefer operations that summarize the purpose or functionality of the %s as a whole.".formatted(entryType),
            "Prefer operations that differentiate the %s from other %ss.".formatted(entryType, entryType),
            "Prefer operations that are very specific.",
            "Output one operation ID per line and no other text."
        );
    }

    @Override
    protected String createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        String question = isGenericNode(node)
            ? "Is the following operation the sole purpose of the %s?".formatted(entryType)
            : "Does the %s perform the following operation, and is it the purpose or an important capability of the %s?\n".formatted(entryType, entryType);

        return joinLines(
            "Given the following %s description:".formatted(entryType),
            tag("description", summary),
            "",
            question,
            "Answer \"yes\" or \"no\" with no other text.",
            "\"" + node.label() + "\": " + node.definition()
        );
    }

    private boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return node.ontology().getAncestors(id).stream().anyMatch(ancestor -> "operation-data-handling".equals(ancestor.id()))
            || "operation-read-mapping".equals(id)
            || "operation-read-pre-processing".equals(id);
    }

}
