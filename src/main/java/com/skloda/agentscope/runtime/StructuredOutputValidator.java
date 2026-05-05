package com.skloda.agentscope.runtime;

import com.skloda.agentscope.schema.ContractMetadata;
import com.skloda.agentscope.schema.IDCardData;
import com.skloda.agentscope.schema.InvoiceData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lightweight schema validation for demo structured outputs.
 */
public class StructuredOutputValidator {

    public ValidationResult validate(Object structuredData) {
        if (structuredData == null) {
            return ValidationResult.failed(List.of("structuredData"), "Structured output is empty.");
        }
        if (structuredData instanceof InvoiceData invoice) {
            return validateInvoice(invoice);
        }
        if (structuredData instanceof IDCardData idCard) {
            return validateIdCard(idCard);
        }
        if (structuredData instanceof ContractMetadata contract) {
            return validateContract(contract);
        }
        return ValidationResult.passed();
    }

    private ValidationResult validateInvoice(InvoiceData invoice) {
        List<String> missing = new ArrayList<>();
        requireText(invoice.invoiceNumber, "invoiceNumber", missing);
        requireText(invoice.invoiceDate, "invoiceDate", missing);
        requireNumber(invoice.totalAmount, "totalAmount", missing);
        requireText(invoice.sellerName, "sellerName", missing);
        requireText(invoice.buyerName, "buyerName", missing);
        return result(missing);
    }

    private ValidationResult validateIdCard(IDCardData idCard) {
        List<String> missing = new ArrayList<>();
        requireText(idCard.name, "name", missing);
        requireText(idCard.idNumber, "idNumber", missing);
        return result(missing);
    }

    private ValidationResult validateContract(ContractMetadata contract) {
        List<String> missing = new ArrayList<>();
        requireText(contract.contractTitle, "contractTitle", missing);
        requireText(contract.partyA, "partyA", missing);
        requireText(contract.partyB, "partyB", missing);
        requireCollection(contract.keyClauses, "keyClauses", missing);
        requireCollection(contract.risks, "risks", missing);
        requireText(contract.overallRiskLevel, "overallRiskLevel", missing);
        requireText(contract.summary, "summary", missing);
        return result(missing);
    }

    private ValidationResult result(List<String> missing) {
        if (missing.isEmpty()) {
            return ValidationResult.passed();
        }
        return ValidationResult.failed(missing, "Missing required structured output fields: " + String.join(", ", missing));
    }

    private void requireText(String value, String field, List<String> missing) {
        if (value == null || value.isBlank()) {
            missing.add(field);
        }
    }

    private void requireNumber(Number value, String field, List<String> missing) {
        if (value == null) {
            missing.add(field);
        }
    }

    private void requireCollection(Collection<?> value, String field, List<String> missing) {
        if (value == null || value.isEmpty()) {
            missing.add(field);
        }
    }

    public record ValidationResult(boolean valid, List<String> missingFields, String message) {
        public static ValidationResult passed() {
            return new ValidationResult(true, List.of(), "Structured output validation passed.");
        }

        public static ValidationResult failed(List<String> missingFields, String message) {
            return new ValidationResult(false, List.copyOf(missingFields), message);
        }

        public boolean invalid() {
            return !valid;
        }
    }
}
