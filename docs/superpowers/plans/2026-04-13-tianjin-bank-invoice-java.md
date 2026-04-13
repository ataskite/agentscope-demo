# 天津银行发票 Java 技能迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `tianjin_bank_invoice` Python 技能迁移为纯 Java 实现，使用 Apache POI 生成 Excel 和 Word 文件，作为 Agent Tool 注册。

**Architecture:** 创建 `TianjinBankInvoiceTool` 类，使用 `@Tool` 注解标记方法，通过 ToolRegistry 的自动扫描机制注册。从 classpath 加载原技能的模板文件，生成脱敏命名的输出文件。

**Tech Stack:** Java 17, Spring Boot 3.5.13, Apache POI 5.3.0 (XWPF/XSSF), Jackson (JSON 序列化)

---

## 文件结构

**新增文件：**
| 文件 | 职责 |
|------|------|
| `src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java` | 核心工具类，`@Tool` 注解方法，实现 Excel/Word 生成逻辑 |
| `src/main/resources/skills/tianjin_bank_invoice_java/SKILL.md` | Java 技能定义，frontmatter 指向 tool |

**说明：** ToolRegistry 自动扫描 `com.msxf.agentscope.tool` 包中的 `@Tool` 注解，并从 `skills/*/SKILL.md` 读取 frontmatter 创建 skill-to-tool 映射。无需修改 ToolRegistry.java。

---

### Task 1: 创建工具类框架和姓名脱敏方法

**Files:**
- Create: `src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java`

- [ ] **Step 1: 创建工具类框架，包含姓名脱敏方法**

```java
package com.msxf.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
}
```

- [ ] **Step 2: 运行编译确认语法正确**

```bash
mvn compile -q
```

预期输出: 无错误，编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java
git commit -m "feat: add TianjinBankInvoiceTool class skeleton with name desensitization"
```

---

### Task 2: 实现 Excel 生成方法

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java`

- [ ] **Step 1: 在类中添加 Excel 生成私有方法**

在 `generateFileName` 方法后添加：

```java
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
```

- [ ] **Step 2: 运行编译确认导入正确**

```bash
mvn compile -q
```

预期输出: 无错误，编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java
git commit -m "feat: add Excel generation method using Apache POI XSSF"
```

---

### Task 3: 实现 Word 生成方法

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java`

- [ ] **Step 1: 在类中添加 Word 生成私有方法**

在 `setCellValue` 方法后添加：

```java
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
            String outputDir = System.getProperty("java.io.tmpdir") + "/agentscope-uploads/";
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(outputDir));
            String outputPath = outputDir + fileName;

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputPath)) {
                document.write(fos);
            }
            document.close();

            log.info("Word 文件生成成功: {}", outputPath);
            return outputPath;
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
        cell.getParagraphs().get(0).getRuns().clear();
        cell.getParagraphs().get(0).createRun().setText(value != null ? value : "");
    }
```

- [ ] **Step 2: 运行编译确认导入正确**

```bash
mvn compile -q
```

预期输出: 无错误，编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java
git commit -m "feat: add Word generation method using Apache POI XWPF"
```

---

### Task 4: 实现 @Tool 公开方法

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java`

- [ ] **Step 1: 添加 Jackson ObjectMapper 导入和 @Tool 方法**

在类开头 imports 部分添加：

```java
import com.fasterxml.jackson.databind.ObjectMapper;
```

在 `setTableCell` 方法后添加 `@Tool` 方法：

```java
    private static final ObjectMapper objectMapper = new ObjectMapper();

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

            // 构建返回结果
            String excelFileName = excelPath.substring(excelPath.lastIndexOf('/') + 1);
            String wordFileName = wordPath.substring(wordPath.lastIndexOf('/') + 1);

            String result = String.format(
                    "{\"success\":true,\"excelPath\":\"%s\",\"wordPath\":\"%s\",\"excelFileName\":\"%s\",\"wordFileName\":\"%s\"}",
                    excelPath, wordPath, excelFileName, wordFileName
            );

            log.info("天津银行发票生成完成: Excel={}, Word={}", excelPath, wordPath);
            return result;

        } catch (Exception e) {
            log.error("生成天津银行发票失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }
```

- [ ] **Step 2: 运行编译确认完整无误**

```bash
mvn compile -q
```

预期输出: 无错误，编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java
git commit -m "feat: add @Tool method generate_tianjin_bank_invoice with validation"
```

---

### Task 5: 创建 SKILL.md 定义文件

**Files:**
- Create: `src/main/resources/skills/tianjin_bank_invoice_java/SKILL.md`

- [ ] **Step 1: 创建技能定义目录和 SKILL.md 文件**

```bash
mkdir -p src/main/resources/skills/tianjin_bank_invoice_java
```

创建文件内容：

```markdown
---
name: tianjin_bank_invoice_java
description: 根据天津银行发票模板生成 Excel 和 Word 文件（Java 版）
tools:
  - generate_tianjin_bank_invoice
---
```

- [ ] **Step 2: 验证文件创建成功**

```bash
cat src/main/resources/skills/tianjin_bank_invoice_java/SKILL.md
```

预期输出: 上述 YAML frontmatter 内容

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/skills/tianjin_bank_invoice_java/SKILL.md
git commit -m "feat: add tianjin_bank_invoice_java skill definition"
```

---

### Task 6: 运行应用验证 Tool 自动注册

**Files:**
- No file changes

- [ ] **Step 1: 启动应用并查看日志**

```bash
export DASHSCOPE_API_KEY=test_key
mvn spring-boot:run -q 2>&1 | grep -i "tianjin\|toolregistry"
```

预期输出: 包含 "Auto-registered tool class: TianjinBankInvoiceTool" 和 "Registered skill: tianjin_bank_invoice_java"

- [ ] **Step 2: 测试 API 端点确认 tool 可用**

```bash
curl -s http://localhost:8080/api/agents | jq '.[] | .name, .skills'
```

预期输出: agents.json 中配置的技能列表（如果后续添加了 skill 到 agent）

- [ ] **Step 3: 停止应用**

```bash
# Ctrl+C 停止应用
```

- [ ] **Step 4: 提交** (无文件变更，跳过此步)

---

### Task 7: 集成到 Agent 配置（可选）

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: 在 agents.yml 中为新 agent 或现有 agent 添加技能**

例如，为 `task-document-analysis` agent 添加技能：

```yaml
  - agentId: task-document-analysis
    name: Task Agent
    description: 文档分析，支持文件解析和天津银行发票生成
    systemPrompt: |
      你是一个文档分析助手。
      当用户上传文档时，使用相应的技能解析文档内容，然后根据解析结果满足用户的需求。
      支持 .docx、.pdf 和 .xlsx 文件的分析。
      当用户需要生成天津银行发票时，收集所需的12个参数（姓名、身份证号、手机号、邮箱、合同号、借据号、放款日期、贷款总额、银行放款金额、费用类型、发票金额、流水号），然后使用 generate_tianjin_bank_invoice 工具生成文件。
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    skills:
      - docx
      - pdf
      - xlsx
      - tianjin_bank_invoice_java
    userTools:
      - parse_docx
      - parse_pdf
      - parse_xlsx
      - generate_tianjin_bank_invoice
    systemTools:
      - view_text_file
```

- [ ] **Step 2: 验证 YAML 语法正确**

```bash
# 应用启动时会自动验证，暂不手动验证
```

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add tianjin_bank_invoice_java skill to task-document-analysis agent"
```

---

### Task 8: 手动端到端测试

**Files:**
- No file changes

- [ ] **Step 1: 启动应用**

```bash
export DASHSCOPE_API_KEY=your_actual_key
mvn spring-boot:run
```

- [ ] **Step 2: 访问前端并测试**

1. 浏览器打开 http://localhost:8080
2. 选择 `task-document-analysis` agent
3. 输入测试消息：
   ```
   请帮我生成天津银行发票。客户姓名：张三丰，身份证号：123456200001011234，手机：13800138000，邮箱：test@example.com，合同号：HT20240410001，借据号：JD20240410001，放款日期：2024-04-10，贷款总额：100000，银行放款：80000，费用类型：服务费，发票金额：5000，流水号：001
   ```
4. 验证 agent 调用 tool 并生成两个文件
5. 在 Debug 面板查看 tool_call/tool_result 日志

- [ ] **Step 3: 检查生成的文件**

```bash
ls -la /tmp/agentscope-uploads/天津银行_*.xlsx /tmp/agentscope-uploads/天津银行_*.docx
```

预期输出: 列出两个新文件，文件名包含脱敏姓名

- [ ] **Step 4: 手动打开文件验证内容**

双击打开 Excel/Word 文件，确认数据填充正确

- [ ] **Step 5: 提交** (测试步骤无需提交，跳过)

---

## 自检清单

**Spec 覆盖检查:**
- ✅ 姓名脱敏逻辑: Task 1 实现 `desensitizeName()`
- ✅ Excel 生成逻辑: Task 2 实现 `generateExcel()`，列映射按 spec
- ✅ Word 生成逻辑: Task 3 实现 `generateWord()`，表格索引按 spec
- ✅ @Tool 方法: Task 4 实现 `generateInvoice()`，12 个参数按 spec
- ✅ SKILL.md: Task 5 创建，frontmatter 格式正确
- ✅ 模板文件引用: 使用 `getClassLoader().getResourceAsStream()` 从 classpath 加载
- ✅ 输出目录: `{java.io.tmpdir}/agentscope-uploads/`
- ✅ 返回 JSON 格式: 与 spec 一致
- ✅ 错误处理: 参数校验 + try-catch 返回错误消息

**Placeholder 扫描:** 无 TBD/TODO，所有步骤包含完整代码

**类型一致性:** 方法签名、参数名在各 task 中保持一致
