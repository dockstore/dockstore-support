package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.List;

public class InputFormatOntologyHandler extends ThreeStageOntologyHandler {

    InputFormatOntologyHandler() {
        super("input-format");
    }

    @Override
    public String getName() {
        return "Input formats";
    }

    @Override
    protected AIModel.Prompt createSummarizeInstruction(EntryData entryData) {
        return AIModel.Prompt.builder()
            .system().text(stateIdentity())
            .user().text(presentEntry(entryData)).cache()
            .text(lines(
                "", "",
                "List the input file formats.",
                "Detail the format variants and format versions.",
                "Omit output formats.",
                "Be terse."
            ))
            .outputTokens(MAX_SUMMARIZE_TOKENS)
            .build();
    }

    @Override
    protected AIModel.Prompt createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(stateIdentity())
            .user().text(presentCategories(nodes, "%s's input formats".formatted(entryType))).cache()
            .text(lines(
                "", "",
                "The %s accepts the following inputs:".formatted(entryType),
                tag("input-description", summary),
                "",
                "List the input formats that the %s accepts.".formatted(entryType),
                "Output one input format ID per line and no other text."
            ))
            .outputTokens(MAX_CLASSIFY_TOKENS)
            .build();
    }

    @Override
    protected AIModel.Prompt createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(stateIdentity())
            .user().text(lines(
                "Use the following description to determine the input formats accepted by the %s:".formatted(entryType),
                tag("input-description", summary),
                "",
                "Does the %s accept the following input format?".formatted(entryType),
                "Answer \"yes\" or \"no\" with no other text.",
                tag("format-name", node.label()),
                tag("format-description", node.definition())
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }
}
