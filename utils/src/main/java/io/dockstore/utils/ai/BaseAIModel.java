package io.dockstore.utils.ai;

/**
 * An AI model that generates topics.
 */
public abstract class BaseAIModel implements AIModel {
    private final AIModelType aiModelType;

    protected BaseAIModel(AIModelType modelType) {
        this.aiModelType = modelType;
    }

    @Override
    public String getModelName() {
        return aiModelType.getModelId();
    }

    @Override
    public double getPricePerInputToken() {
        return aiModelType.getPricePerInputToken();
    }

    @Override
    public double getPricePerOutputToken() {
        return aiModelType.getPricePerOutputToken();
    }

    @Override
    public double getPricePerCacheWriteToken() {
        return aiModelType.getPricePerCacheWriteToken();
    }

    @Override
    public double getPricePerCacheReadToken() {
        return aiModelType.getPricePerCacheReadToken();
    }

    @Override
    public int getMaxContextLength() {
        return aiModelType.getMaxContextLength();
    }
}
