package io.dockstore.utils.ai;

/**
 * Decorator that accumulates the total cost and token usage across all invocations.
 */
public class TotalCostAIModel extends DelegatingAIModel {

    private double totalCost = 0.0;
    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;

    public TotalCostAIModel(AIModel delegate) {
        super(delegate);
    }

    @Override
    public Response submitPrompt(Prompt prompt) {
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
