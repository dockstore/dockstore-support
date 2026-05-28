package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.List;

public class OutputFormatOntologyHandler extends ThreeStageOntologyHandler {

    OutputFormatOntologyHandler() {
        super("output-format");
    }

    @Override
    public String getName() {
        return "Output formats";
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
                "List the output file formats.",
                "Detail the format variants and format versions.",
                "Omit input formats.",
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
                "Classify the %s's output formats into the following categories:".formatted(entryType),
                // createOntologyMarkdownTable(nodes, "Output Format ID", "Output Format Name", "Output Format Description"),
                createTaggedOntologyCsv(nodes, "output-format-"),
                "",
                "The %s produces the following outputs:".formatted(entryType),
                tag("output-description", summary),
                "",
                "List the output formats that the %s produces.".formatted(entryType),
                "Output one output format ID per line and no other text."
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
                "Use the following description to determine the output formats produced by the %s:".formatted(entryType),
                tag("output-description", summary),
                "",
                "Does the %s produce the following output format?".formatted(entryType),
                "Answer \"yes\" or \"no\" with no other text.",
                tag("format-name", node.label()),
                tag("format-description", node.definition())
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }
}
