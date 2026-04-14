package io.dockstore.utils.ai;

public interface AIModel {

    AIResponseInfo submitPrompt(String prompt, double temperature, int maxResponseTokens);

    String getModelName();

    double getPricePerInputToken();

    double getPricePerOutputToken();

    int getMaxContextLength();

    default double calculatePrice(long inputTokens, long outputTokens) {
        return (inputTokens * getPricePerInputToken()) + (outputTokens * getPricePerOutputToken());
    }

    default int estimateTokens(String prompt) {
        // AWS Bedrock suggests using 6 characters per token as an estimation
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-customization-prepare.html
        final int estimatedCharactersPerToken = 6;
        return prompt.length() / estimatedCharactersPerToken;
    }

    record AIResponseInfo(String aiResponse, boolean isTruncated, long inputTokens, long outputTokens, double cost, String stopReason) {
    }
}
