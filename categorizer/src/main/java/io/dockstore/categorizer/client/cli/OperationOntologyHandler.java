package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
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
    protected AIModel.Prompt createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(createGenericEntryPresentation(entryData)).cache()
            .text(joinLines(
                "", "",
                "In 200 words, describe the %s's purpose, functionality, and the operations it performs.".formatted(entryType, entryType),
                "Omit the %s's name.".formatted(entryType),
                "Be terse.",
                "Use scientific terminology."
            ))
            .outputTokens(MAX_SUMMARIZE_TOKENS)
            .build();
    }

    @Override
    protected AIModel.Prompt createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(createGenericCategoriesPresentation(nodes, "operations performed by the %s".formatted(entryType), "operation-")).cache()
            .text(joinLines(
                "", "",
                "The %s performs the following operations:".formatted(entryType),
                tag("operations-description", summary),
                "",
                "List the operations that the %s performs.".formatted(entryType),
                "Prefer operations that describe an important capability of the %s.".formatted(entryType),
                // "Prefer operations that differentiate the %s from other %ss.".formatted(entryType, entryType),
                "Prefer operations that are more specific.",
                "Output one operation ID per line and no other text."
            ))
            .outputTokens(MAX_CLASSIFY_TOKENS)
            .build();
    }

    @Override
    protected AIModel.Prompt createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        String question = isGenericNode(node)
            ? "Is the following operation the sole purpose of the %s?".formatted(entryType)
            : "Does the %s perform the following operation, and is it the purpose or an important capability of the %s?\n".formatted(entryType, entryType);

        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(joinLines(
                "Given the following %s description:".formatted(entryType),
                tag("description", summary),
                "",
                question,
                "Answer \"yes\" or \"no\" with no other text.",
                "\"" + node.label() + "\": " + node.definition()
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }

    private boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return node.ontology().getAncestors(id).stream().anyMatch(ancestor -> "operation-data-handling".equals(ancestor.id()))
            || "operation-read-mapping".equals(id)
            || "operation-read-pre-processing".equals(id);
    }

}
