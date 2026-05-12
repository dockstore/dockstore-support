package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class OutputDataOntologyHandler extends ThreeStageOntologyHandler {

    OutputDataOntologyHandler() {
        super("output-data");
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Summarize the following %s:".formatted(entryType),
            formatEntryData(entryData),
            "",
            "Describe the information content of the outputs.",
            "Explain each output's meaning or purpose, rather than its concrete representation.",
            "Omit input information.",
            "Be terse."
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Classify the %s's outputs into the following categories:".formatted(entryType),
            createOntologyMarkdownTable(nodes, "Output Data ID", "Output Data Name", "Output Data Description"),
            "",
            "The %s supports the following outputs:".formatted(entryType),
            tag("output-description", summary),
            "",
            "List the data outputs.".formatted(entryType),
            "Output one outputs data ID per line and no other text."
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Use the following description to determine the outputs data accepted by the %s:".formatted(entryType),
            tag("outputs-description", summary),
            "",
            "Does the %s support the following outputs data?".formatted(entryType),
            tag("data-name", node.label()),
            tag("data-description", node.definition()),
            "Answer \"yes\" or \"no\" with no other text."
        );
    }
}
