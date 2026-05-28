package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.List;

public class OutputDataOntologyHandler extends ThreeStageOntologyHandler {

    OutputDataOntologyHandler() {
        super("output-data");
    }

    @Override
    public String getName() {
        return "Output data";
    }

    @Override
    protected AIModel.Prompt createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(joinLines(
                "Summarize the following %s:".formatted(entryType),
                formatEntryData(entryData),
                "",
                "Describe the information content of the %s's outputs.".formatted(entryType),
                "Explain each output's meaning or purpose, rather than its concrete representation.",
                "Omit input information.",
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
                "Classify the %s's outputs into the following categories:".formatted(entryType),
                // createOntologyMarkdownTable(nodes, "Output Data ID", "Output Data Name", "Output Data Description"),
                createTaggedOntologyCsv(nodes, "output-data-"),
                "",
                "The %s supports the following outputs:".formatted(entryType),
                tag("output-description", summary),
                "",
                "List the data outputs that the %s produces.".formatted(entryType),
                "Output one output data ID per line and no other text."
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
                "Use the following description to determine the outputs data produced by the %s:".formatted(entryType),
                tag("outputs-description", summary),
                "",
                "Does the %s produce the following output data?".formatted(entryType),
                tag("data-name", node.label()),
                tag("data-description", node.definition()),
                "Answer \"yes\" or \"no\" with no other text."
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }
}
