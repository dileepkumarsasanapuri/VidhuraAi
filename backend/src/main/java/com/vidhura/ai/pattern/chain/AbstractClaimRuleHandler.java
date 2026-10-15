package com.vidhura.ai.pattern.chain;

/**
 * Abstract base class for Claim Rule Handlers in the Chain of Responsibility.
 * Implements next handler chaining and delegation logic.
 */
public abstract class AbstractClaimRuleHandler implements ClaimRuleHandler {

    protected ClaimRuleHandler nextHandler;

    @Override
    public ClaimRuleHandler setNext(ClaimRuleHandler nextHandler) {
        this.nextHandler = nextHandler;
        return nextHandler;
    }

    @Override
    public void handle(ClaimContext context) {
        processRule(context);
        if (nextHandler != null) {
            nextHandler.handle(context);
        }
    }

    /**
     * Subclasses implement their specific actuarial deduction and validation logic here.
     *
     * @param context The shared claim context.
     */
    protected abstract void processRule(ClaimContext context);
}
