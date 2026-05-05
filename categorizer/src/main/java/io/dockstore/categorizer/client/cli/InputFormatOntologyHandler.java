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
            "List the file format of each user-specified input.",
            "Describe each format in a sentence or less.",
            "Omit formats that are only used internally or for outputs."
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Classify the input formats of a %s into the following list of categories:".formatted(entryType),
            createTaggedOntologyCsv(nodes, "input-format-"),
            "",
            "The %s supports the following inputs:".formatted(entryType),
            tag("input-format-description", summary),
            "",
            "List the file format of each user-specified input.".formatted(entryType),
            "Output one input format ID per line and no other text."
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Use the following description to determine the input formats supported by the %s:".formatted(entryType),
            tag("input-format-description", summary),
            "",
            "Does the %s support user-specified input in the following file format?".formatted(entryType),
            "\"" + node.label() + "\": " + node.definition(),
            "Answer \"yes\" or \"no\" with no other text."
        );
    }
}
