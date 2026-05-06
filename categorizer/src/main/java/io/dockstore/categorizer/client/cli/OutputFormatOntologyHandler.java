package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class OutputFormatOntologyHandler extends ThreeStageOntologyHandler {

    OutputFormatOntologyHandler() {
        super("output-format-");
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Summarize the following %s:".formatted(entryType),
            formatEntryData(entryData),
            "List the format of each output.",
            "Files within archived or compressed output files are also outputs.",
            "Omit formats that are only used internally or for inputs.",
            "Be terse and use scientific terminology."
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Classify the %s's output formats into the following list of categories:".formatted(entryType),
            createTaggedOntologyCsv(nodes, "output-format-"),
            "",
            "The %s produces the following outputs:".formatted(entryType),
            tag("output-description", summary),
            "",
            "List the output formats.".formatted(entryType),
            "Files within archived or compressed output files are also outputs.",
            "Output one output format ID per line and no other text."
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Use the following description to determine the output formats produced by the %s:".formatted(entryType),
            tag("output-description", summary),
            "",
            "Does the %s produce an output in the following format?".formatted(entryType),
            "Files within archived or compressed output files are also outputs.",
            "Answer \"yes\" or \"no\" with no other text.",
            tag("format-name", node.label()),
            tag("format-description", node.definition())
        );
    }
}
