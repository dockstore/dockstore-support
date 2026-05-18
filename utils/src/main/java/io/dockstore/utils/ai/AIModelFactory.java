package io.dockstore.utils.ai;

public final class AIModelFactory {

    private AIModelFactory() {
    }

    public static AIModel createModel(ClaudeAIModelType type) {
        return new AnthropicClaudeModel(type);
    }
}
