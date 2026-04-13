package io.dockstore.utils.ai;

public interface AIModel {
    String getModelName();
    double getPricePer1kInputTokens();
    double getPricePer1kOutputTokens();
    int getMaxContextLength();
    AIResponseInfo submitPrompt(String prompt);

    public record AIResponseInfo(String aiResponse, boolean isTruncated, long inputTokens, long outputTokens, double cost, String stopReason) {
    }
}
