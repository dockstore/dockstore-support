package io.dockstore.utils.ai;

/**
 * Describes the configuration of a specific AI model, including its identifier, pricing, and context length.
 */
public interface AIModelType {
    String getModelId();
    double getPricePerInputToken();
    double getPricePerOutputToken();
    int getMaxContextLength();
}
