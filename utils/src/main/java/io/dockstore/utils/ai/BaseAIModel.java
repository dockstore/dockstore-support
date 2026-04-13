package io.dockstore.utils.ai;

/**
 * An AI model that generates topics.
 */
public abstract class BaseAIModel implements AIModel {
    private final AIModelType aiModelType;

    protected BaseAIModel(AIModelType modelType) {
        this.aiModelType = modelType;
    }

    /**
     * Submit a prompt to the AI model.
     *
     * @return
     */
    @Override
    public abstract AIResponseInfo submitPrompt(String prompt, double temperature, int maxResponseTokens);

    @Override
    public String getModelName() {
        return aiModelType.getModelId();
    }

    @Override
    public double getPricePer1kInputTokens() {
        return aiModelType.getPricePer1kInputTokens();
    }

    @Override
    public double getPricePer1kOutputTokens() {
        return aiModelType.getPricePer1kOutputTokens();
    }

    @Override
    public int getMaxContextLength() {
        return aiModelType.getMaxContextLength();
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public double calculatePrice(long inputTokens, long outputTokens) {
        return (((double)inputTokens / 1000) * getPricePer1kInputTokens()) + (((double)outputTokens / 1000) * getPricePer1kOutputTokens());
    }

    public int estimateTokens(String prompt) {
        // AWS Bedrock suggests using 6 characters per token as an estimation
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-customization-prepare.html
        final int estimatedCharactersPerToken = 6;
        return prompt.length() / estimatedCharactersPerToken;
    }
}
