package com.skloda.agentscope.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankInvoiceToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BankInvoiceTool tool = new BankInvoiceTool();

    @Test
    void generateInvoiceRejectsMissingName() {
        String result = tool.generateInvoice("", "110101199003072316", "13800000000", "a@example.com",
                "HT1", "JD1", "2026-04-29", "100000", "80000", "服务费", "5000", "001");

        assertEquals("{\"success\":false,\"error\":\"客户姓名不能为空\"}", result);
    }

    @Test
    void generateInvoiceRejectsMissingIdCard() {
        String result = tool.generateInvoice("张三", "", "13800000000", "a@example.com",
                "HT1", "JD1", "2026-04-29", "100000", "80000", "服务费", "5000", "001");

        assertEquals("{\"success\":false,\"error\":\"身份证号不能为空\"}", result);
    }

    @Test
    void generateInvoiceRejectsMissingSerial() {
        String result = tool.generateInvoice("张三", "110101199003072316", "13800000000", "a@example.com",
                "HT1", "JD1", "2026-04-29", "100000", "80000", "服务费", "5000", " ");

        assertEquals("{\"success\":false,\"error\":\"流水号不能为空\"}", result);
    }

    @Test
    void generateInvoiceCreatesExcelAndWordFromTemplates() throws Exception {
        String result = tool.generateInvoice("张三丰", "110101199003072316", "13800000000", "a@example.com",
                "HT1", "JD1", "2026-04-29", "100000", "80000", "服务费", "5000", "unit-test");

        JsonNode json = MAPPER.readTree(result);

        assertTrue(json.get("success").asBoolean(), result);
        String excelFileName = json.get("excelFileName").asText();
        String wordFileName = json.get("wordFileName").asText();
        Path uploadDir = Path.of(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
        assertTrue(Files.exists(uploadDir.resolve(excelFileName)), "Excel file not found: " + excelFileName);
        assertTrue(Files.exists(uploadDir.resolve(wordFileName)), "Word file not found: " + wordFileName);
        assertTrue(excelFileName.contains("张某某"));
        assertTrue(wordFileName.contains("张某某"));
    }
}
