package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class InputFormatOntologyHandler extends ThreeStageOntologyHandler {

    InputFormatOntologyHandler() {
        super("input-format");
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Summarize the following %s:".formatted(entryType),
            formatEntryData(entryData),
            "",
            "List the input file formats.",
            "Detail the format variants and format versions.",
            "Omit output formats.",
            "Be terse."
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Classify the %s's input formats into the following categories:".formatted(entryType),
            createOntologyMarkdownTable(nodes, "Input Format ID", "Input Format Name", "Input Format Description"),
            "",
            "The %s accepts the following inputs:".formatted(entryType),
            tag("input-description", summary),
            "",
            "List the input formats.",
            "Output one input format ID per line and no other text."
        );
    }

    @Override
    protected String createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Use the following description to determine the input formats accepted by the %s:".formatted(entryType),
            tag("input-description", summary),
            "",
            "Does the %s accept the following input format?".formatted(entryType),
            "Answer \"yes\" or \"no\" with no other text.",
            tag("format-name", node.label()),
            tag("format-description", node.definition())
        );
    }
}
