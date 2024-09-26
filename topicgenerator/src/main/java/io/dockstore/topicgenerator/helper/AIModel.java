package io.dockstore.topicgenerator.helper;

import java.util.Optional;

/**
 * An AI model that generates topics.
 */
public abstract class AIModel {
    // The sum of the number of tokens in the request and response cannot exceed the model's maximum context length.
    public static final int MAX_RESPONSE_TOKENS = 100; // One token is roughly 4 characters. Using 100 tokens because setting it too low might truncate the response
    private final String modelName;
    private final double pricePer1kInputTokens;
    private final double pricePer1kOutputTokens;
    private final int maxContextLength;

    public AIModel(AIModelType modelType) {
        this.modelName = modelType.getModelId();
        this.pricePer1kInputTokens = modelType.getPricePer1kInputTokens();
        this.pricePer1kOutputTokens = modelType.getPricePer1kOutputTokens();
        this.maxContextLength = modelType.getMaxContextLength();
    }

    public String getModelName() {
        return modelName;
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

    @SuppressWarnings("checkstyle:magicnumber")
    public double calculatePrice(long inputTokens, long outputTokens) {
        return (((double)inputTokens / 1000) * pricePer1kInputTokens) + (((double)outputTokens / 1000) * pricePer1kOutputTokens);
    }

    /**
     * Generate an AI topic using the contents of the descriptor file.
     *
     * @return
     */
    public abstract Optional<AIResponseInfo> generateTopic(String prompt);

    public int estimateTokens(String prompt) {
        // AWS Bedrock suggests using 6 characters per token as an estimation
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-customization-prepare.html
        final int estimatedCharactersPerToken = 6;
        return prompt.length() / estimatedCharactersPerToken;
    }

    public record AIResponseInfo(String aiTopic, boolean isTruncated, long inputTokens, long outputTokens, double cost, String stopReason) {
    }
}
