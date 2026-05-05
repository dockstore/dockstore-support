package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class InputDataOntologyHandler extends ThreeStageOntologyHandler {

    InputDataOntologyHandler() {
        super("input-data-");
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Summarize the following %s:".formatted(entryType),
            formatEntryData(entryData),
            "Describe the information content of each user-specified input.",
            "Omit information about the outputs.",
            "Be terse and use scientific terminology."
            /*
            "List the abstract data type of each user-specified input.",
            "Describe each abstract data type in a sentence or less.",
            "Explain the data's meaning or purpose, rather than the concrete representation.",
            */
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Classify the %s's inputs into the following categories:".formatted(entryType),
            createTaggedOntologyCsv(nodes, "input-data-"),
            "",
            "The %s supports the following inputs:".formatted(entryType),
            tag("input-data-description", summary),
            "",
            "List the category that describes each user-specified input.".formatted(entryType),
            "Output one input data ID per line and no other text."
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Use the following %s description:".formatted(entryType),
            tag("input-description", summary),
            "",
            "Does the %s support the following type of user-specified input?".formatted(entryType),
            tag("input-type", node.label()),
            tag("input-type-definition", node.definition()),
            "Answer \"yes\" or \"no\" with no other text."
        );
    }
}
