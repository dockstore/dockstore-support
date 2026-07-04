package io.dockstore.utils.ai;

/**
 * Decorator that accumulates the total cost and token usage across all invocations, optionally
 * enforcing a cost limit.
 */
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

    public synchronized void checkLimit() {
        if (totalCost > costLimit) {
            throw new LimitExceededException(
                String.format("Cost limit of $%.6f exceeded: total cost is $%.6f", costLimit, totalCost));
        }
    }

    @Override
    public Response submitPrompt(Prompt prompt) {
        checkLimit();
        Response response = super.submitPrompt(prompt);
        synchronized (this) {
            totalCost += response.cost();
            totalInputTokens += response.inputTokens();
            totalOutputTokens += response.outputTokens();
        }
        return response;
    }

    public synchronized double getTotalCost() {
        return totalCost;
    }

    public synchronized long getTotalInputTokens() {
        return totalInputTokens;
    }

    public synchronized long getTotalOutputTokens() {
        return totalOutputTokens;
    }
}
