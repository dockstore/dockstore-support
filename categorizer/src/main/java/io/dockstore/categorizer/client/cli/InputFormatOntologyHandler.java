package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class InputFormatOntologyHandler extends ThreeStageOntologyHandler {

    InputFormatOntologyHandler() {
        super("input-format-");
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Summarize the following %s:".formatted(entryType),
            formatEntryData(entryData),
            "",
            "List the input file formats.",
            "Files within archived or compressed input files are also inputs.",
            "Omit output formats and exclusively internal formats.",
            "Be terse and use scientific terminology."
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Classify the %s's input formats into the following list of categories:".formatted(entryType),
            createTaggedOntologyCsv(nodes, "input-format-"),
            "",
            "The %s supports the following inputs:".formatted(entryType),
            tag("input-description", summary),
            "",
            "List the input formats.",
            "Files within archived or compressed input files are also inputs.",
            "Output one input format ID per line and no other text."
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Use the following description to determine the input formats supported by the %s:".formatted(entryType),
            tag("input-description", summary),
            "",
            "Does the %s accept an input in the following file format?".formatted(entryType),
            "Files within archived or compressed input files are also inputs.",
            "Answer \"yes\" or \"no\" with no other text.",
            tag("format-name", node.label()),
            tag("format-description", node.definition())
        );
    }
}
