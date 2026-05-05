package com.skloda.agentscope.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractReviewReportToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ContractReviewReportTool tool = new ContractReviewReportTool();

    @Test
    void generateReportRejectsMissingRequiredMetadata() throws Exception {
        String result = tool.generateReport("服务合同", "", "甲方", "",
                "", "", null, "CNY", "", "", "", "");

        JsonNode json = MAPPER.readTree(result);

        assertEquals(false, json.get("success").asBoolean());
        assertTrue(json.get("error").asText().contains("partyB"));
    }

    @Test
    void generateReportWritesMarkdownAndReturnsDownloadUrl() throws Exception {
        String result = tool.generateReport(
                "技术服务合同",
                "HT-2026-001",
                "甲方科技有限公司",
                "乙方服务有限公司",
                "2026-05-01",
                "2027-04-30",
                120000.0,
                "CNY",
                "付款条款：分三期付款",
                "违约责任：违约金上限不明确；建议补充违约金上限",
                "HIGH",
                "该合同存在较高履约风险，建议补充责任边界。");

        JsonNode json = MAPPER.readTree(result);

        assertTrue(json.get("success").asBoolean(), result);
        assertTrue(json.get("downloadUrl").asText().startsWith("/chat/download?fileId="));

        Path reportPath = Path.of(json.get("filePath").asText());
        assertTrue(Files.exists(reportPath), "Report file not found: " + reportPath);
        String markdown = Files.readString(reportPath);
        assertTrue(markdown.contains("# 合同审查报告"));
        assertTrue(markdown.contains("技术服务合同"));
        assertTrue(markdown.contains("违约金上限不明确"));
    }
}
