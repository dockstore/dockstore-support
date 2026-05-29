package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class OutputDataOntologyHandler extends ThreeStageOntologyHandler {

    OutputDataOntologyHandler() {
        super("output-data");
    }

    @Override
    protected List<String> saySummarizeInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "Describe the information content of the %s's outputs.".formatted(entryType),
            "Explain each output's meaning or purpose, rather than its concrete representation.",
            "Omit input information."
        );
    }

    @Override
    protected List<String> sayClassifyInstructions(EntryData entryData) {
        return List.of(
            "List the data outputs that the %s produces.".formatted(entryData.entryType())
        );
    }

    @Override
    protected List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node) {
        return List.of(
            "Does the %s produce the following output data?".formatted(entryData.entryType())
        );
    }

    @Override
    protected List<String> saySummaryIntro(EntryData entryData) {
        return List.of(
            "The %s produces the following outputs:".formatted(entryData.entryType())
        );
    }

    @Override
    protected String sayPluralPhrase(EntryData entryData) {
        return "%s's outputs".formatted(entryData.entryType());
    }

}
