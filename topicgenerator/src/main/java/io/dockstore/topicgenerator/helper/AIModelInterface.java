package io.dockstore.topicgenerator.helper;

public interface AIModelInterface {
    String getModelName();
    double getPricePer1kInputTokens();
    double getPricePer1kOutputTokens();
    int getMaxContextLength();
}
