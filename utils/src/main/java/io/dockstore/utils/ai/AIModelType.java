package io.dockstore.utils.ai;

public interface AIModelType {
    String getModelId();
    double getPricePerInputToken();
    double getPricePerOutputToken();
    int getMaxContextLength();
}
