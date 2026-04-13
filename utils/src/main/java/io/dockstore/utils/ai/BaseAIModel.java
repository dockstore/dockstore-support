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
}
