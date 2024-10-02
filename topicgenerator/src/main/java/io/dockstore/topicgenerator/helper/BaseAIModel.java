package io.dockstore.topicgenerator.helper;

import java.util.Optional;

/**
 * An AI model that generates topics.
 */
public abstract class BaseAIModel implements AIModelInterface {
    // The sum of the number of tokens in the request and response cannot exceed the model's maximum context length.
    public static final int MAX_RESPONSE_TOKENS = 100; // One token is roughly 4 characters. Using 100 tokens because setting it too low might truncate the response
    private final AIModelType aiModelType;

    public BaseAIModel(AIModelType modelType) {
        this.aiModelType = modelType;
    }

    /**
     * Submit a prompt to the AI model.
     *
     * @return
     */
    public abstract Optional<AIResponseInfo> submitPrompt(String prompt);

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

    public record AIResponseInfo(String aiResponse, boolean isTruncated, long inputTokens, long outputTokens, double cost, String stopReason) {
    }
}
