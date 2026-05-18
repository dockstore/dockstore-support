package io.dockstore.utils.ai;

public final class AIModelFactory {

    private AIModelFactory() {
    }

    public static AIModel createModel(AIModelType type) {
        return new AnthropicClaudeModel((ClaudeAIModelType) type);
    }
}
