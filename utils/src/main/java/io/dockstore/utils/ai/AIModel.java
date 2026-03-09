package io.dockstore.utils.ai;

public interface AIModel {
    String getModelName();
    double getPricePer1kInputTokens();
    double getPricePer1kOutputTokens();
    int getMaxContextLength();
}
