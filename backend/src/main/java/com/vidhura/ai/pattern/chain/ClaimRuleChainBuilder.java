package com.vidhura.ai.pattern.chain;

import org.springframework.stereotype.Component;

/**
 * Builder and Factory Pattern to construct the configured Claim Rule Chain of Responsibility.
 */
@Component
public class ClaimRuleChainBuilder {

    /**
     * Builds and links the full sequence of claim rule handlers.
     *
     * @return The entry head handler of the chain.
     */
    public ClaimRuleHandler buildDefaultChain() {
        ClaimRuleHandler waitingPeriodHandler = new WaitingPeriodRuleHandler();
        ClaimRuleHandler networkHandler = new NetworkHospitalRuleHandler();
        ClaimRuleHandler roomRentHandler = new RoomRentRuleHandler();
        ClaimRuleHandler subLimitHandler = new DiseaseSubLimitRuleHandler();
        ClaimRuleHandler deductibleHandler = new DeductibleRuleHandler();
        ClaimRuleHandler copayHandler = new CopayRuleHandler();
        ClaimRuleHandler finalSettlementHandler = new FinalSettlementCalculatorHandler();

        // Assemble the chain
        waitingPeriodHandler
                .setNext(networkHandler)
                .setNext(roomRentHandler)
                .setNext(subLimitHandler)
                .setNext(deductibleHandler)
                .setNext(copayHandler)
                .setNext(finalSettlementHandler);

        return waitingPeriodHandler;
    }
}
