package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;

public class OperationOntologyHandler extends ThreeStageOntologyHandler {

    OperationOntologyHandler() {
    }

    @Override
    public String getName() {
        return "Operations";
    }

    @Override
    protected String getRootId() {
        return "operation";
    }

    @Override
    protected String getSingularPhrase() {
        return "operation";
    }

    @Override
    protected String getPluralPhrase(EntryData entryData) {
        return "operations performed by the %s".formatted(entryData.entryType());
    }

    @Override
    protected String getSummarizeCommand(EntryData entryData) {
        String entryType = entryData.entryType();
        return lines(
            "", "",
            "In 200 words, describe the %s's purpose, functionality, and the operations it performs.".formatted(entryType),
            "Omit the %s's name.".formatted(entryType),
            "Be terse.",
            "Use scientific terminology."
        );
    }

    @Override
    protected String getSummaryDescription(EntryData entryData) {
        return "The %s performs the following operations:".formatted(entryData.entryType());
    }

    @Override
    protected String getClassifyCommand(EntryData entryData) {
        String entryType = entryData.entryType();
        return lines(
            "List the operations that the %s performs.".formatted(entryType),
            "Prefer operations that describe the %s's functionality as a whole.".formatted(entryType),
            // "Prefer operations that differentiate the %s from other %ss.".formatted(entryType, entryType),
            "Prefer operations that are more specific.",
            "Include operations you are not sure about."
        );
    }

    @Override
    protected String getVerifyQuestion(EntryData entryData, Ontology.Node node) {
        String entryType = entryData.entryType();
        return isGenericNode(node)
            ? "Is the following operation the sole purpose of the %s?".formatted(entryType)
            : "Does the %s perform the following operation, and is it the purpose or an important capability of the %s?".formatted(entryType, entryType);
    }

    private boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return node.ontology().getAncestors(id).stream().anyMatch(ancestor -> "operation-data-handling".equals(ancestor.id()))
            || "operation-read-mapping".equals(id)
            || "operation-read-pre-processing".equals(id);
    }

}
