package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class InputDataOntologyHandler extends ThreeStageOntologyHandler {

    InputDataOntologyHandler() {
        super("input-data");
    }

    @Override
    protected String sayPluralPhrase(EntryData entryData) {
        return "%s's inputs".formatted(entryData.entryType());
    }

    @Override
    protected List<String> saySummarizeInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "", "",
            "Describe the information content of the %s's inputs.".formatted(entryType),
            "Explain each inputs's meaning or purpose, rather than its concrete representation.",
            "Omit output information."
        );
    }

    @Override
    protected List<String> saySummaryIntro(EntryData entryData) {
        return List.of("The %s supports the following inputs:".formatted(entryData.entryType()));
    }

    @Override
    protected List<String> sayClassifyInstructions(EntryData entryData) {
        return List.of("List the inputs that the %s accepts.".formatted(entryData.entryType()));
    }

    @Override
    protected List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node) {
        return List.of("Does the %s support the following input data?".formatted(entryData.entryType()));
    }

}
