package io.dockstore.utils.ai;

public class TotalCostAIModel implements AIModel {

    private final AIModel delegate;
    private final double costLimit;
    private double totalCost = 0.0;
    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;

    public TotalCostAIModel(AIModel delegate) {
        this(delegate, Double.POSITIVE_INFINITY);
    }

    public TotalCostAIModel(AIModel delegate, double costLimit) {
        this.delegate = delegate;
        this.costLimit = costLimit;
    }

    @Override
    public AIResponseInfo submitPrompt(Prompt prompt) {
        AIResponseInfo response = delegate.submitPrompt(prompt);
        totalCost += response.cost();
        totalInputTokens += response.inputTokens();
        totalOutputTokens += response.outputTokens();
        if (totalCost > costLimit) {
            throw new LimitExceededException(
                String.format("Cost limit of $%.6f exceeded: total cost is $%.6f", costLimit, totalCost));
        }
        return response;
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }

    @Override
    public double getPricePerInputToken() {
        return delegate.getPricePerInputToken();
    }

    @Override
    public double getPricePerOutputToken() {
        return delegate.getPricePerOutputToken();
    }

    @Override
    public double getPricePerCacheWriteToken() {
        return delegate.getPricePerCacheWriteToken();
    }

    @Override
    public double getPricePerCacheReadToken() {
        return delegate.getPricePerCacheReadToken();
    }

    @Override
    public int getMaxContextLength() {
        return delegate.getMaxContextLength();
    }

    public double getTotalCost() {
        return totalCost;
    }

    public long getTotalInputTokens() {
        return totalInputTokens;
    }

    public long getTotalOutputTokens() {
        return totalOutputTokens;
    }
}
