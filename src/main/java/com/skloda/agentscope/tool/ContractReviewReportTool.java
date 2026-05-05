package com.skloda.agentscope.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.runtime.StructuredOutputValidator;
import com.skloda.agentscope.schema.ContractMetadata;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates a downloadable Markdown report from structured contract review metadata.
 */
public class ContractReviewReportTool {

    private static final Logger log = LoggerFactory.getLogger(ContractReviewReportTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StructuredOutputValidator validator = new StructuredOutputValidator();

    @Tool(name = "generate_contract_review_report",
            description = "Generate a downloadable Markdown contract review report from flattened contract review fields.")
    public String generateReport(
            @ToolParam(name = "contractTitle", description = "合同名称") String contractTitle,
            @ToolParam(name = "contractNumber", description = "合同编号，可为空") String contractNumber,
            @ToolParam(name = "partyA", description = "甲方名称") String partyA,
            @ToolParam(name = "partyB", description = "乙方名称") String partyB,
            @ToolParam(name = "effectiveDate", description = "生效日期，格式 yyyy-MM-dd，可为空") String effectiveDate,
            @ToolParam(name = "expiryDate", description = "到期日期，格式 yyyy-MM-dd，可为空") String expiryDate,
            @ToolParam(name = "totalAmount", description = "合同金额，数字，可为空") Double totalAmount,
            @ToolParam(name = "currency", description = "币种，如 CNY，可为空") String currency,
            @ToolParam(name = "keyClausesSummary", description = "关键条款摘要，用普通文本描述，不要传 JSON") String keyClausesSummary,
            @ToolParam(name = "risksSummary", description = "风险和建议摘要，用普通文本描述，不要传 JSON") String risksSummary,
            @ToolParam(name = "overallRiskLevel", description = "总体风险等级：LOW、MEDIUM、HIGH 或 CRITICAL") String overallRiskLevel,
            @ToolParam(name = "summary", description = "合同审查摘要") String summary) {
        try {
            ContractMetadata metadata = new ContractMetadata();
            metadata.contractTitle = contractTitle;
            metadata.contractNumber = contractNumber;
            metadata.partyA = partyA;
            metadata.partyB = partyB;
            metadata.effectiveDate = effectiveDate;
            metadata.expiryDate = expiryDate;
            metadata.totalAmount = totalAmount;
            metadata.currency = currency;
            metadata.overallRiskLevel = overallRiskLevel;
            metadata.summary = summary;
            metadata.keyClauses = java.util.List.of(clause("关键条款", keyClausesSummary, "OTHER"));
            metadata.risks = java.util.List.of(risk("合同风险", risksSummary, overallRiskLevel, "请根据审查摘要补充或修订相关条款。"));

            StructuredOutputValidator.ValidationResult validation = validator.validate(metadata);
            if (validation.invalid()) {
                return error(validation.message());
            }

            Path outputDir = Path.of(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            Files.createDirectories(outputDir);

            String fileName = "contract_review_" + LocalDateTime.now().format(FILE_TS) + ".md";
            Path reportPath = outputDir.resolve(fileName);
            Files.writeString(reportPath, renderMarkdown(metadata), StandardCharsets.UTF_8);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("filePath", reportPath.toAbsolutePath().toString());
            result.put("downloadUrl", "/chat/download?fileId=" +
                    URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            result.put("riskLevel", metadata.overallRiskLevel);
            result.put("summary", metadata.summary);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to generate contract review report", e);
            return error("生成合同审查报告失败: " + e.getMessage());
        }
    }

    private ContractMetadata.ClauseItem clause(String title, String summary, String category) {
        ContractMetadata.ClauseItem item = new ContractMetadata.ClauseItem();
        item.title = title;
        item.summary = summary;
        item.category = category;
        return item;
    }

    private ContractMetadata.RiskItem risk(String clause, String description, String severity, String recommendation) {
        ContractMetadata.RiskItem item = new ContractMetadata.RiskItem();
        item.clause = clause;
        item.description = description;
        item.severity = severity;
        item.recommendation = recommendation;
        return item;
    }

    private String renderMarkdown(ContractMetadata metadata) {
        StringBuilder md = new StringBuilder();
        md.append("# 合同审查报告\n\n");
        md.append("## 基本信息\n\n");
        appendLine(md, "- 合同名称: ", metadata.contractTitle);
        appendLine(md, "- 合同编号: ", metadata.contractNumber);
        appendLine(md, "- 甲方: ", metadata.partyA);
        appendLine(md, "- 乙方: ", metadata.partyB);
        appendLine(md, "- 生效日期: ", metadata.effectiveDate);
        appendLine(md, "- 到期日期: ", metadata.expiryDate);
        appendLine(md, "- 合同金额: ", formatAmount(metadata));
        md.append("\n## 总体风险\n\n");
        appendLine(md, "- 风险等级: ", metadata.overallRiskLevel);
        appendLine(md, "- 摘要: ", metadata.summary);

        md.append("\n## 关键条款\n\n");
        for (ContractMetadata.ClauseItem clause : metadata.keyClauses) {
            md.append("- **").append(value(clause.title)).append("**");
            if (clause.category != null && !clause.category.isBlank()) {
                md.append(" [").append(clause.category).append("]");
            }
            md.append(": ").append(value(clause.summary)).append("\n");
        }

        md.append("\n## 风险与建议\n\n");
        for (ContractMetadata.RiskItem risk : metadata.risks) {
            md.append("- **").append(value(risk.severity)).append("** ");
            md.append(value(risk.clause)).append(": ").append(value(risk.description)).append("\n");
            md.append("  - 建议: ").append(value(risk.recommendation)).append("\n");
        }

        return md.toString();
    }

    private void appendLine(StringBuilder md, String label, String value) {
        if (value != null && !value.isBlank()) {
            md.append(label).append(value).append("\n");
        }
    }

    private String formatAmount(ContractMetadata metadata) {
        if (metadata.totalAmount == null) {
            return null;
        }
        String currency = metadata.currency != null && !metadata.currency.isBlank()
                ? metadata.currency
                : "";
        return metadata.totalAmount + " " + currency;
    }

    private String value(String value) {
        return value != null && !value.isBlank() ? value : "未提供";
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("success", false, "error", message));
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + message.replace("\"", "'") + "\"}";
        }
    }
}
