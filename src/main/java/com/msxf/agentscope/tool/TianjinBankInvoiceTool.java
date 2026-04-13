package com.msxf.agentscope.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.LinkedHashMap;

public class TianjinBankInvoiceTool {

    private static final Logger log = LoggerFactory.getLogger(TianjinBankInvoiceTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 姓名脱敏处理
     * 规则: 保留第一个字符（姓），其余字符替换为"某"
     * 例: 张三 -> 张某, 张三丰 -> 张某某
     */
    private String desensitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "某某某";
        }
        name = name.trim();
        if (name.length() == 1) {
            return name + "某";
        }
        return name.charAt(0) + "某".repeat(name.length() - 1);
    }

    /**
     * 生成文件名: 天津银行_{脱敏姓名}_{YYMMDD}_{流水号}.扩展名
     */
    private String generateFileName(String name, String serial, String ext) {
        String desensitized = desensitizeName(name);
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        return "天津银行_" + desensitized + "_" + dateStr + "_" + serial + "." + ext;
    }

    /**
     * 从 classpath 加载模板并生成 Excel 文件
     */
    private String generateExcel(String name, String idCard, String phone, String email,
                                 String contract, String loan, String date, String amount,
                                 String bankAmount, String feeType, String invoice,
                                 String serial) throws Exception {
        // 加载模板
        InputStream templateStream = getClass().getClassLoader()
                .getResourceAsStream("skills/tianjin_bank_invoice/assets/tianjin_bank_template.xlsx");
        if (templateStream == null) {
            throw new IllegalStateException("Excel模板文件未找到: skills/tianjin_bank_invoice/assets/tianjin_bank_template.xlsx");
        }

        try (templateStream;
             org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(templateStream)) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.getSheetAt(0);
            org.apache.poi.xssf.usermodel.XSSFRow row = sheet.getRow(1);

            if (row == null) {
                row = sheet.createRow(1);
            }

            // 按列映射填入数据 (A=0, B=1, ..., K=10)
            setCellValue(row, 0, name);        // A: 客户姓名
            setCellValue(row, 1, idCard);      // B: 身份证号
            setCellValue(row, 2, phone);       // C: 联系电话
            setCellValue(row, 3, contract);    // D: 合同号
            setCellValue(row, 4, loan);        // E: 借据号
            setCellValue(row, 5, date);        // F: 放款日期
            setCellValue(row, 6, amount);      // G: 贷款总额
            setCellValue(row, 7, bankAmount);  // H: 银行放款金额
            setCellValue(row, 8, feeType);     // I: 费用类型
            setCellValue(row, 9, invoice);     // J: 发票金额
            setCellValue(row, 10, email);      // K: 邮箱

            // 生成输出文件
            String fileName = generateFileName(name, serial, "xlsx");
            java.nio.file.Path outputDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            java.nio.file.Files.createDirectories(outputDir);
            java.nio.file.Path outputPath = outputDir.resolve(fileName);

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputPath.toString())) {
                workbook.write(fos);
            }

            log.info("Excel 文件生成成功: {}", outputPath);
            return outputPath.toString();
        }
    }

    /**
     * 安全设置单元格值
     */
    private void setCellValue(org.apache.poi.xssf.usermodel.XSSFRow row, int column, String value) {
        org.apache.poi.xssf.usermodel.XSSFCell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column);
        }
        cell.setCellValue(value != null ? value : "");
    }

    /**
     * 从 classpath 加载模板并生成 Word 文件
     */
    private String generateWord(String name, String idCard, String phone, String email,
                                String invoice, String feeType, String serial) throws Exception {
        // 加载模板
        InputStream templateStream = getClass().getClassLoader()
                .getResourceAsStream("skills/tianjin_bank_invoice/assets/tianjin_bank_template.docx");
        if (templateStream == null) {
            throw new IllegalStateException("Word模板文件未找到: skills/tianjin_bank_invoice/assets/tianjin_bank_template.docx");
        }

        try (templateStream) {
            org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(templateStream);

            // 获取第一个表格
            if (document.getTables().isEmpty()) {
                throw new IllegalStateException("Word模板中未找到表格");
            }
            org.apache.poi.xwpf.usermodel.XWPFTable table = document.getTables().get(0);

            // 填充表格数据 (注意: cell(rowIndex, colIndex) 基于Python版推断，可能需要根据实际模板调整)
            setTableCell(table, 1, 1, name);           // [1][1] 名称（姓名）
            setTableCell(table, 1, 3, idCard);         // [1][3] 纳税人识别号
            setTableCell(table, 4, 1, name + " " + phone);  // [4][1] 联系人及电话
            setTableCell(table, 4, 3, email);          // [4][3] 邮箱
            setTableCell(table, 8, 1, invoice);        // [8][1] 发票金额
            setTableCell(table, 9, 1, feeType);        // [9][1] 费用类型

            // 更新提交日期
            String todayDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph para : document.getParagraphs()) {
                if (para.getText().contains("提交日期：")) {
                    // 清除现有runs并添加新文本
                    for (int i = para.getRuns().size() - 1; i >= 0; i--) {
                        para.removeRun(i);
                    }
                    para.createRun().setText("提交日期：" + todayDate);
                    break;
                }
            }

            // 生成输出文件
            String fileName = generateFileName(name, serial, "docx");
            java.nio.file.Path outputDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            java.nio.file.Files.createDirectories(outputDir);
            java.nio.file.Path outputPath = outputDir.resolve(fileName);

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputPath.toString())) {
                document.write(fos);
            }

            log.info("Word 文件生成成功: {}", outputPath);
            return outputPath.toString();
        }
    }

    /**
     * 安全设置表格单元格值
     */
    private void setTableCell(org.apache.poi.xwpf.usermodel.XWPFTable table, int rowIdx, int colIdx, String value) {
        if (table.getRows().size() <= rowIdx) {
            log.warn("表格行数不足，跳过设置 cell({},{})", rowIdx, colIdx);
            return;
        }
        org.apache.poi.xwpf.usermodel.XWPFTableRow row = table.getRow(rowIdx);
        if (row.getTableCells().size() <= colIdx) {
            log.warn("表格列数不足，跳过设置 cell({},{})", rowIdx, colIdx);
            return;
        }
        org.apache.poi.xwpf.usermodel.XWPFTableCell cell = row.getCell(colIdx);
        // 清除现有段落并添加新文本
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        cell.getParagraphs().get(0).getRuns().clear();
        cell.getParagraphs().get(0).createRun().setText(value != null ? value : "");
    }

    @Tool(name = "generate_tianjin_bank_invoice",
          description = "根据天津银行发票模板生成 Excel 和 Word 文件。"
                  + "需要提供客户姓名、身份证号、手机号、邮箱、合同号、借据号、"
                  + "放款日期、贷款总额、银行放款金额、费用类型、发票金额、流水号。"
                  + "返回生成的文件路径和文件名。")
    public String generateInvoice(
            @ToolParam(name = "name", description = "客户姓名") String name,
            @ToolParam(name = "idCard", description = "身份证号") String idCard,
            @ToolParam(name = "phone", description = "联系电话") String phone,
            @ToolParam(name = "email", description = "邮箱地址") String email,
            @ToolParam(name = "contract", description = "合同号，如 HT20240410001") String contract,
            @ToolParam(name = "loan", description = "借据号，如 JD20240410001") String loan,
            @ToolParam(name = "date", description = "放款日期，格式 yyyy-MM-dd") String date,
            @ToolParam(name = "amount", description = "贷款总额") String amount,
            @ToolParam(name = "bankAmount", description = "银行放款金额") String bankAmount,
            @ToolParam(name = "feeType", description = "费用类型，如 服务费") String feeType,
            @ToolParam(name = "invoice", description = "发票金额") String invoice,
            @ToolParam(name = "serial", description = "流水号，如 001") String serial) {

        // 参数校验
        if (name == null || name.isBlank()) {
            return "{\"success\":false,\"error\":\"客户姓名不能为空\"}";
        }
        if (idCard == null || idCard.isBlank()) {
            return "{\"success\":false,\"error\":\"身份证号不能为空\"}";
        }
        if (serial == null || serial.isBlank()) {
            return "{\"success\":false,\"error\":\"流水号不能为空\"}";
        }

        try {
            // 生成 Excel
            String excelPath = generateExcel(name, idCard, phone, email, contract, loan,
                    date, amount, bankAmount, feeType, invoice, serial);

            // 生成 Word
            String wordPath = generateWord(name, idCard, phone, email, invoice, feeType, serial);

            // 构建返回结果 - 使用平台无关的路径处理
            java.nio.file.Path excelFilePath = java.nio.file.Paths.get(excelPath);
            java.nio.file.Path wordFilePath = java.nio.file.Paths.get(wordPath);
            String excelFileName = excelFilePath.getFileName().toString();
            String wordFileName = wordFilePath.getFileName().toString();

            // 使用 ObjectMapper 进行 JSON 序列化
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("excelPath", excelPath);
            result.put("wordPath", wordPath);
            result.put("excelFileName", excelFileName);
            result.put("wordFileName", wordFileName);

            String jsonResult;
            try {
                jsonResult = objectMapper.writeValueAsString(result);
            } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
                log.error("JSON序列化失败", jsonEx);
                return "{\"success\":false,\"error\":\"JSON序列化失败\"}";
            }

            log.info("天津银行发票生成完成: Excel={}, Word={}", excelPath, wordPath);
            return jsonResult;

        } catch (Exception e) {
            log.error("生成天津银行发票失败", e);
            // 使用 ObjectMapper 进行错误响应序列化
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            try {
                return objectMapper.writeValueAsString(errorResult);
            } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
                log.error("错误响应JSON序列化失败", jsonEx);
                return "{\"success\":false,\"error\":\"生成失败且JSON序列化失败\"}";
            }
        }
    }
}
