package com.skloda.agentscope.schema;

import java.util.List;

/**
 * Structured output schema for contract metadata extraction.
 */
public class ContractMetadata {

    public String contractTitle;
    public String contractNumber;
    public String partyA;
    public String partyB;
    public String effectiveDate;
    public String expiryDate;
    public Double totalAmount;
    public String currency;
    public String signingDate;
    public String signingLocation;
    public List<ClauseItem> keyClauses;
    public List<RiskItem> risks;
    public String overallRiskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    public String summary;

    public ContractMetadata() {}

    public static class ClauseItem {
        public String title;
        public String summary;
        public String category; // PAYMENT, DELIVERY, LIABILITY, TERMINATION, CONFIDENTIALITY, OTHER

        public ClauseItem() {}
    }

    public static class RiskItem {
        public String clause;
        public String description;
        public String severity; // LOW, MEDIUM, HIGH, CRITICAL
        public String recommendation;

        public RiskItem() {}
    }
}
