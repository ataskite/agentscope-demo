package com.skloda.agentscope.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContractMetadataTest {

    @Test
    void constructor_createsEmptyContractMetadata() {
        ContractMetadata contract = new ContractMetadata();

        assertNotNull(contract);
        assertNull(contract.contractTitle);
        assertNull(contract.contractNumber);
        assertNull(contract.partyA);
        assertNull(contract.partyB);
        assertNull(contract.effectiveDate);
        assertNull(contract.expiryDate);
        assertNull(contract.totalAmount);
        assertNull(contract.currency);
        assertNull(contract.signingDate);
        assertNull(contract.signingLocation);
        assertNull(contract.keyClauses);
        assertNull(contract.risks);
        assertNull(contract.overallRiskLevel);
        assertNull(contract.summary);
    }

    @Test
    void fieldsCanBeSet() {
        ContractMetadata contract = new ContractMetadata();
        ContractMetadata.ClauseItem clause = new ContractMetadata.ClauseItem();
        ContractMetadata.RiskItem risk = new ContractMetadata.RiskItem();

        contract.contractTitle = "测试合同";
        contract.contractNumber = "CONTRACT001";
        contract.partyA = "甲方公司";
        contract.partyB = "乙方公司";
        contract.effectiveDate = "2026-01-01";
        contract.expiryDate = "2027-12-31";
        contract.totalAmount = 100000.0;
        contract.currency = "CNY";
        contract.signingDate = "2025-12-01";
        contract.signingLocation = "北京市";
        contract.keyClauses = List.of(clause);
        contract.risks = List.of(risk);
        contract.overallRiskLevel = "MEDIUM";
        contract.summary = "测试合同摘要";

        assertEquals("测试合同", contract.contractTitle);
        assertEquals("CONTRACT001", contract.contractNumber);
        assertEquals("甲方公司", contract.partyA);
        assertEquals("乙方公司", contract.partyB);
        assertEquals("2026-01-01", contract.effectiveDate);
        assertEquals("2027-12-31", contract.expiryDate);
        assertEquals(100000.0, contract.totalAmount);
        assertEquals("CNY", contract.currency);
        assertEquals("2025-12-01", contract.signingDate);
        assertEquals("北京市", contract.signingLocation);
        assertEquals(1, contract.keyClauses.size());
        assertEquals(1, contract.risks.size());
        assertEquals("MEDIUM", contract.overallRiskLevel);
        assertEquals("测试合同摘要", contract.summary);
    }

    @Test
    void clauseItemConstructor_createsEmptyItem() {
        ContractMetadata.ClauseItem item = new ContractMetadata.ClauseItem();

        assertNotNull(item);
        assertNull(item.title);
        assertNull(item.summary);
        assertNull(item.category);
    }

    @Test
    void clauseItemFieldsCanBeSet() {
        ContractMetadata.ClauseItem item = new ContractMetadata.ClauseItem();

        item.title = "付款条款";
        item.summary = "甲方应在收到发票后30日内付款";
        item.category = "PAYMENT";

        assertEquals("付款条款", item.title);
        assertEquals("甲方应在收到发票后30日内付款", item.summary);
        assertEquals("PAYMENT", item.category);
    }

    @Test
    void riskItemConstructor_createsEmptyItem() {
        ContractMetadata.RiskItem item = new ContractMetadata.RiskItem();

        assertNotNull(item);
        assertNull(item.clause);
        assertNull(item.description);
        assertNull(item.severity);
        assertNull(item.recommendation);
    }

    @Test
    void riskItemFieldsCanBeSet() {
        ContractMetadata.RiskItem item = new ContractMetadata.RiskItem();

        item.clause = "付款条款";
        item.description = "付款期限过长，可能导致现金流压力";
        item.severity = "MEDIUM";
        item.recommendation = "建议缩短付款期限至15日内";

        assertEquals("付款条款", item.clause);
        assertEquals("付款期限过长，可能导致现金流压力", item.description);
        assertEquals("MEDIUM", item.severity);
        assertEquals("建议缩短付款期限至15日内", item.recommendation);
    }

    @Test
    void riskLevels_allLevelsSupported() {
        ContractMetadata contract = new ContractMetadata();

        contract.overallRiskLevel = "LOW";
        assertEquals("LOW", contract.overallRiskLevel);

        contract.overallRiskLevel = "MEDIUM";
        assertEquals("MEDIUM", contract.overallRiskLevel);

        contract.overallRiskLevel = "HIGH";
        assertEquals("HIGH", contract.overallRiskLevel);

        contract.overallRiskLevel = "CRITICAL";
        assertEquals("CRITICAL", contract.overallRiskLevel);
    }

    @Test
    void clauseCategories_allCategoriesSupported() {
        ContractMetadata.ClauseItem item = new ContractMetadata.ClauseItem();

        item.category = "PAYMENT";
        assertEquals("PAYMENT", item.category);

        item.category = "DELIVERY";
        assertEquals("DELIVERY", item.category);

        item.category = "LIABILITY";
        assertEquals("LIABILITY", item.category);

        item.category = "TERMINATION";
        assertEquals("TERMINATION", item.category);

        item.category = "CONFIDENTIALITY";
        assertEquals("CONFIDENTIALITY", item.category);

        item.category = "OTHER";
        assertEquals("OTHER", item.category);
    }
}
