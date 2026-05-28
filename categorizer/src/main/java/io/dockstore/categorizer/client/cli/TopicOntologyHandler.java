package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import io.dockstore.utils.ai.AIModel;
import java.util.List;

public class TopicOntologyHandler extends ThreeStageOntologyHandler {

    TopicOntologyHandler() {
        super("topic");
    }

    @Override
    public String getName() {
        return "Topics";
    }

    @Override
    protected AIModel.Prompt createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(joinLines(
                "Summarize the following %s:".formatted(entryType),
                formatEntryData(entryData),
                "Describe the %s's field of study, area of application, scientific context, and similar.",
                "Omit information about the operations performed by the %s.".formatted(entryType),
                "Be terse and use scientific terminology."
            ))
            .outputTokens(MAX_SUMMARIZE_TOKENS)
            .build();
    }

    @Override
    protected AIModel.Prompt createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(joinLines(
                "Classify the %s into the following categories:".formatted(entryType),
                createTaggedOntologyCsv(nodes, "topic-"),
                "",
                "Use the following description of the %s's topics:".formatted(entryType),
                tag("topic-description", summary),
                "",
                "List the topics that relate to the %s.".formatted(entryType),
                "Be as specific as possible.",
                "List up to seven topics.",
                "Prefer topics that describe the field of study, area of application, scientific context, or similar.", entryType,
                "Output one topic ID per line and no other text."
            ))
            .outputTokens(MAX_CLASSIFY_TOKENS)
            .build();
    }

    @Override
    protected AIModel.Prompt createVerifyInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return AIModel.Prompt.builder()
            .system().text(createIdentityStatement())
            .user().text(joinLines(
                "Use the following %s description:".formatted(entryType),
                tag("topic-description", summary),
                "",
                "Does the following topic accurately describe the %s's field of study, area of application, scientific context, or similar?".formatted(entryType),
                tag("topic-name", node.label()),
                tag("topic-definition", node.definition()),
                "Answer \"yes\" or \"no\" with no other text."
            ))
            .outputTokens(MAX_VERIFY_TOKENS)
            .build();
    }
}
