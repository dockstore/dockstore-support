package io.dockstore.topicgenerator.helper;

import com.knuddels.jtokkit.api.ModelType;

public enum AIModelType {
    CLAUDE_3_5_SONNET("us.anthropic.claude-3-5-sonnet-20240620-v1:0", 0.003, 0.015, 200000),
    CLAUDE_3_HAIKU("us.anthropic.claude-3-haiku-20240307-v1:0", 0.00025, 0.00125, 200000),
    GPT_4O_MINI(ModelType.GPT_4O_MINI.getName(), 0.000150, 0.000600, ModelType.GPT_4O_MINI.getMaxContextLength());

    private final String modelId;
    private final double pricePer1kInputTokens;
    private final double pricePer1kOutputTokens;
    private final int maxContextLength;

    AIModelType(String modelId, double pricePer1kInputTokens, double pricePer1kOutputTokens, int maxInputTokens) {
        this.modelId = modelId;
        this.pricePer1kInputTokens = pricePer1kInputTokens;
        this.pricePer1kOutputTokens = pricePer1kOutputTokens;
        this.maxContextLength = maxInputTokens;
    }

    public String getModelId() {
        return modelId;
    }

    public double getPricePer1kInputTokens() {
        return pricePer1kInputTokens;
    }

    public double getPricePer1kOutputTokens() {
        return pricePer1kOutputTokens;
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }
}
