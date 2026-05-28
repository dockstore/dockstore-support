package io.dockstore.utils.ai;

public enum ClaudeModelType implements AIModelType {
    CLAUDE_3_5_SONNET("us.anthropic.claude-3-5-sonnet-20240620-v1:0", 3.0 / 1_000_000, 15.0 / 1_000_000, 3.75 / 1_000_000, 0.30 / 1_000_000, 200000),
    CLAUDE_3_HAIKU("us.anthropic.claude-3-haiku-20240307-v1:0", 0.25 / 1_000_000, 1.25 / 1_000_000, 0.30 / 1_000_000, 0.03 / 1_000_000, 200000),
    CLAUDE_4_5_HAIKU("us.anthropic.claude-haiku-4-5-20251001-v1:0", 1.0 / 1_000_000, 5.0 / 1_000_000, 1.25 / 1_000_000, 0.10 / 1_000_000, 200000),
    CLAUDE_4_6_SONNET("us.anthropic.claude-sonnet-4-6", 3.0 / 1_000_000, 15.0 / 1_000_000, 3.75 / 1_000_000, 0.30 / 1_000_000, 200000);

    private final String modelId;
    private final double pricePerUncachedInputToken;
    private final double pricePerOutputToken;
    private final double pricePerCacheWriteToken;
    private final double pricePerCacheReadToken;
    private final int maxContextLength;

    ClaudeModelType(String modelId, double pricePerUncachedInputToken, double pricePerOutputToken, double pricePerCacheWriteToken, double pricePerCacheReadToken, int maxContextLength) {
        this.modelId = modelId;
        this.pricePerUncachedInputToken = pricePerUncachedInputToken;
        this.pricePerOutputToken = pricePerOutputToken;
        this.pricePerCacheWriteToken = pricePerCacheWriteToken;
        this.pricePerCacheReadToken = pricePerCacheReadToken;
        this.maxContextLength = maxContextLength;
    }

    @Override
    public String getModelId() {
        return modelId;
    }

    @Override
    public double getPricePerInputToken() {
        return getPricePerUncachedInputToken();
    }

    @Override
    public double getPricePerOutputToken() {
        return pricePerOutputToken;
    }

    @Override
    public int getMaxContextLength() {
        return maxContextLength;
    }

    public double getPricePerUncachedInputToken() {
        return pricePerUncachedInputToken;
    }

    public double getPricePerCacheWriteToken() {
        return pricePerCacheWriteToken;
    }

    public double getPricePerCacheReadToken() {
        return pricePerCacheReadToken;
    }
}
