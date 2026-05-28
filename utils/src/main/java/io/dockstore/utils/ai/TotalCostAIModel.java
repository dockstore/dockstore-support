package io.dockstore.utils.ai;

public class TotalCostAIModel extends DelegatingAIModel {

    private final double costLimit;
    private double totalCost = 0.0;
    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;

    public TotalCostAIModel(AIModel delegate) {
        this(delegate, Double.POSITIVE_INFINITY);
    }

    public TotalCostAIModel(AIModel delegate, double costLimit) {
        super(delegate);
        this.costLimit = costLimit;
    }

    @Override
    public Response submitPrompt(Prompt prompt) {
        Response response = super.submitPrompt(prompt);
        totalCost += response.cost();
        totalInputTokens += response.inputTokens();
        totalOutputTokens += response.outputTokens();
        if (totalCost > costLimit) {
            throw new LimitExceededException(
                String.format("Cost limit of $%.6f exceeded: total cost is $%.6f", costLimit, totalCost));
        }
        return response;
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
