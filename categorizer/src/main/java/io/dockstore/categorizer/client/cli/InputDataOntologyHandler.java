package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class InputDataOntologyHandler extends ThreeStageOntologyHandler {

    InputDataOntologyHandler() {
        super("input-data");
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Summarize the following %s:".formatted(entryType),
            formatEntryData(entryData),
            "",
            "Describe the information content of the %s's inputs.".formatted(entryType),
            "Explain each inputs's meaning or purpose, rather than its concrete representation.",
            "Omit output information.",
            "Be terse."
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Classify the %s's inputs into the following categories:".formatted(entryType),
            // createOntologyMarkdownTable(nodes, "Input Data ID", "Input Data Name", "Input Data Description"),
            createTaggedOntologyCsv(nodes, "input-data-"),
            "",
            "The %s supports the following inputs:".formatted(entryType),
            tag("input-description", summary),
            "",
            "List the inputs that the %s accepts.".formatted(entryType),
            "Output one input data ID per line and no other text."
        );
    }

    @Override
    protected String createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Use the following description to determine the input data accepted by the %s:".formatted(entryType),
            tag("input-description", summary),
            "",
            "Does the %s support the following input data?".formatted(entryType),
            tag("data-name", node.label()),
            tag("data-description", node.definition()),
            "Answer \"yes\" or \"no\" with no other text."
        );
    }
}
