package com.vidhura.ai.pattern.chain;

/**
 * Handler interface in the Chain of Responsibility pattern for claim evaluation.
 */
public interface ClaimRuleHandler {

    /**
     * Sets the next handler in the chain.
     *
     * @param nextHandler The next handler to process the context.
     * @return The next handler for fluent builder chaining.
     */
    ClaimRuleHandler setNext(ClaimRuleHandler nextHandler);

    /**
     * Evaluates a specific insurance rule against the claim context, applies any
     * admissible deductions or notes, and delegates to the next handler.
     *
     * @param context The shared claim context.
     */
    void handle(ClaimContext context);
}
