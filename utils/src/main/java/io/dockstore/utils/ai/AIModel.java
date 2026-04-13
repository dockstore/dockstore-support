package io.dockstore.utils.ai;

public interface AIModel {

    String getModelName();

    double getPricePer1kInputTokens();

    double getPricePer1kOutputTokens();

    int getMaxContextLength();

    AIResponseInfo submitPrompt(String prompt, double temperature, int maxResponseTokens);

    @SuppressWarnings("checkstyle:magicnumber")
    default double calculatePrice(long inputTokens, long outputTokens) {
        return (((double)inputTokens / 1000) * getPricePer1kInputTokens()) + (((double)outputTokens / 1000) * getPricePer1kOutputTokens());
    }

    default int estimateTokens(String prompt) {
        // AWS Bedrock suggests using 6 characters per token as an estimation
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-customization-prepare.html
        final int estimatedCharactersPerToken = 6;
        return prompt.length() / estimatedCharactersPerToken;
    }

    public record AIResponseInfo(String aiResponse, boolean isTruncated, long inputTokens, long outputTokens, double cost, String stopReason) {
    }
}
