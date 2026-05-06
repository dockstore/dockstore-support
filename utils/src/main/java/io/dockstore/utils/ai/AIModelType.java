package io.dockstore.utils.ai;

public enum AIModelType {
    CLAUDE_3_5_SONNET("us.anthropic.claude-3-5-sonnet-20240620-v1:0", 3.0 / 1_000_000, 15.0 / 1_000_000, 200000),
    CLAUDE_3_HAIKU("us.anthropic.claude-3-haiku-20240307-v1:0", 0.25 / 1_000_000, 1.25 / 1_000_000, 200000),
    CLAUDE_4_5_HAIKU("us.anthropic.claude-haiku-4-5-20251001-v1:0", 1.1 / 1_000_000, 5.5 / 1_000_000, 200000);

    private final String modelId;
    private final double pricePerInputToken;
    private final double pricePerOutputToken;
    private final int maxContextLength;

    AIModelType(String modelId, double pricePerInputToken, double pricePerOutputToken, int maxInputTokens) {
        this.modelId = modelId;
        this.pricePerInputToken = pricePerInputToken;
        this.pricePerOutputToken = pricePerOutputToken;
        this.maxContextLength = maxInputTokens;
    }

    public String getModelId() {
        return modelId;
    }

    public double getPricePerInputToken() {
        return pricePerInputToken;
    }

    public double getPricePerOutputToken() {
        return pricePerOutputToken;
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }
}
