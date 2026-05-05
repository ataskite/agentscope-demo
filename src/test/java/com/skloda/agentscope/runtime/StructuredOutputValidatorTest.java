package com.skloda.agentscope.runtime;

import com.skloda.agentscope.schema.ContractMetadata;
import com.skloda.agentscope.schema.IDCardData;
import com.skloda.agentscope.schema.InvoiceData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredOutputValidatorTest {

    private final StructuredOutputValidator validator = new StructuredOutputValidator();

    @Test
    void invoiceRequiresCoreFields() {
        InvoiceData invoice = new InvoiceData();
        invoice.invoiceNumber = "INV-001";
        invoice.totalAmount = 128.50;

        StructuredOutputValidator.ValidationResult result = validator.validate(invoice);

        assertTrue(result.invalid());
        assertEquals(List.of("invoiceDate", "sellerName", "buyerName"), result.missingFields());
    }

    @Test
    void idCardRequiresIdentityFields() {
        IDCardData idCard = new IDCardData();
        idCard.name = "张三";

        StructuredOutputValidator.ValidationResult result = validator.validate(idCard);

        assertTrue(result.invalid());
        assertEquals(List.of("idNumber"), result.missingFields());
    }

    @Test
    void contractMetadataRequiresPartiesClausesAndRiskSummary() {
        ContractMetadata metadata = new ContractMetadata();
        metadata.contractTitle = "服务合同";
        metadata.partyA = "甲方科技有限公司";

        StructuredOutputValidator.ValidationResult result = validator.validate(metadata);

        assertTrue(result.invalid());
        assertEquals(List.of("partyB", "keyClauses", "risks", "overallRiskLevel", "summary"),
                result.missingFields());
    }
}
