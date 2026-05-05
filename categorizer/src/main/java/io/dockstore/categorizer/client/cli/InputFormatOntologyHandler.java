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
            "Given the following description of a %s's inputs:".formatted(entryType),
            tag("input-format-description", summary),
            "From the following list, select the file format of each user-specified input.".formatted(entryType),
            "Output one input format ID per line and no other text.",
            createTaggedOntologyCsv(nodes, "input-format-")
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Given the following description of a %s's inputs:".formatted(entryType),
            tag("input-format-description", summary),
            "",
            "Does the %s support user-specified input in the following file format?".formatted(entryType),
            "\"" + node.label() + "\": " + node.definition(),
            "Answer \"yes\" or \"no\" with no other text."
        );
    }
}
