package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.List;

public class InputDataOntologyHandler extends ThreeStageOntologyHandler {

    InputDataOntologyHandler() {
        super("input-data");
    }

    @Override
    public String getName() {
        return "Input data";
    }

    @Override
    protected AIModel.Prompt createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(joinLines(
                createGenericEntryPresentation(entryData),
                "Describe the information content of the %s's inputs.".formatted(entryType),
                "Explain each inputs's meaning or purpose, rather than its concrete representation.",
                "Omit output information.",
                "Be terse."
            ))
            .outputTokens(MAX_SUMMARIZE_TOKENS)
            .build();
    }

    @Override
    protected AIModel.Prompt createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(joinLines(
                createGenericCategoriesPresentation(nodes, "%s's inputs".formatted(entryType), "input-data-"),
                "The %s supports the following inputs:".formatted(entryType),
                tag("input-description", summary),
                "",
                "List the inputs that the %s accepts.".formatted(entryType),
                "Output one input data ID per line and no other text."
            ))
            .outputTokens(MAX_CLASSIFY_TOKENS)
            .build();
    }

    @Override
    protected AIModel.Prompt createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(joinLines(
                "Use the following description to determine the input data accepted by the %s:".formatted(entryType),
                tag("input-description", summary),
                "",
                "Does the %s support the following input data?".formatted(entryType),
                tag("data-name", node.label()),
                tag("data-description", node.definition()),
                "Answer \"yes\" or \"no\" with no other text."
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }
}
