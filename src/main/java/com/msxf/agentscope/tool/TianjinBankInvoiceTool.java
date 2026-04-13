package com.msxf.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TianjinBankInvoiceTool {

    private static final Logger log = LoggerFactory.getLogger(TianjinBankInvoiceTool.class);

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
            throw new IllegalStateException("Excel模板文件未找到: skills/tianjin_bank_invoice/assets/tianjin_bank_template.xlsx.xlsx");
        }

        try (templateStream) {
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(templateStream);
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
            String outputDir = System.getProperty("java.io.tmpdir") + "/agentscope-uploads/";
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(outputDir));
            String outputPath = outputDir + fileName;

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
            workbook.close();

            log.info("Excel 文件生成成功: {}", outputPath);
            return outputPath;
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
}
