package io.dockstore.utils.ai;

/**
 * Factory for creating {@link AIModel} instances from a given {@link AIModelType}.
 */
public final class AIModelFactory {

    private AIModelFactory() {
    }

    public static AIModel createModel(AIModelType type) {
        return new BedrockClaudeModel((ClaudeModelType) type);
    }
}
