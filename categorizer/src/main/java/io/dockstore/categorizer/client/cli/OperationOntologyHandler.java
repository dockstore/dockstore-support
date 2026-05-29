package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class OperationOntologyHandler extends ThreeStageOntologyHandler {

    OperationOntologyHandler() {
        super("operation");
    }

    @Override
    protected List<String> saySummarizeInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "Describe the %s's purpose, functionality, and the operations it performs.".formatted(entryType),
            "Omit the %s's name.".formatted(entryType)
        );
    }

    @Override
    protected List<String> sayClassifyInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "List the operations that the %s performs.".formatted(entryType),
            "Prefer operations that describe the %s's functionality as a whole.".formatted(entryType),
            "Prefer operations that are more specific.",
            "Include operations you are not sure about."
        );
    }

    @Override
    protected List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node) {
        String entryType = entryData.entryType();
        return List.of(isGenericNode(node)
            ? "Is the following operation the sole purpose of the %s?".formatted(entryType)
            : "Does the %s perform the following operation, and is it the purpose or an important capability of the %s?".formatted(entryType, entryType)
        );
    }

    private boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return node.ontology().getAncestors(id).stream().anyMatch(ancestor -> "operation-data-handling".equals(ancestor.id()))
            || "operation-read-mapping".equals(id)
            || "operation-read-pre-processing".equals(id);
    }

    @Override
    protected List<String> saySummaryIntro(EntryData entryData) {
        return List.of(
            "The %s performs the following operations:".formatted(entryData.entryType())
        );
    }

    @Override
    protected String sayPluralPhrase(EntryData entryData) {
        return "%s's operations".formatted(entryData.entryType());
    }

}
