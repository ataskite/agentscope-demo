# 天津银行发票 Java 技能迁移设计

## 概述

将 `tianjin_bank_invoice` Python 技能完整迁移为纯 Java 实现（`tianjin_bank_invoice_java`），使用 Apache POI 生成 Excel 和 Word 文件，作为 Agent Tool 注册到 ToolRegistry，不依赖任何 Python 环境。原技能文件不做任何改动。

## 新增文件

| 文件 | 用途 |
|------|------|
| `src/main/java/com/msxf/agentscope/tool/TianjinBankInvoiceTool.java` | 核心工具类，`@Tool` 注解方法 |
| `src/main/resources/skills/tianjin_bank_invoice_java/SKILL.md` | Java 技能定义，tools 字段指向 `generate_tianjin_bank_invoice` |

**模板文件**：直接引用原技能 `tianjin_bank_invoice/assets/` 下的模板，不复制。

## 修改文件

| 文件 | 修改内容 |
|------|----------|
| `src/main/java/com/msxf/agentscope/tool/ToolRegistry.java` | 注册 `generate_tianjin_bank_invoice` → `TianjinBankInvoiceTool::new`，以及 skill 映射 `tianjin_bank_invoice_java` → tool |
| `src/main/resources/config/agents.json` | 如需要，为相关 agent 添加 `tianjin_bank_invoice_java` 技能 |

## Tool 方法设计

```java
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
    @ToolParam(name = "serial", description = "流水号，如 001") String serial
)
```

**返回值**：JSON 字符串
```json
{
  "excelPath": "/tmp/agentscope-uploads/天津银行_张某某_260413_001.xlsx",
  "wordPath": "/tmp/agentscope-uploads/天津银行_张某某_260413_001.docx",
  "excelFileName": "天津银行_张某某_260413_001.xlsx",
  "wordFileName": "天津银行_张某某_260413_001.docx"
}
```

## 核心逻辑

### 姓名脱敏

```
输入: 张三丰 → 输出: 张某某
规则: 保留第一个字符（姓），其余字符替换为"某"
```

与 Python 版 `desensitize_name()` 逻辑一致。

### Excel 生成

1. 从 classpath 加载 `skills/tianjin_bank_invoice/assets/tianjin_bank_template.xlsx`
2. 获取第一个 Sheet，定位到第 2 行（row index 1）
3. 按列映射填入数据：

| 列 | 字段 |
|----|------|
| A (0) | 客户姓名 |
| B (1) | 身份证号 |
| C (2) | 联系电话 |
| D (3) | 合同号 |
| E (4) | 借据号 |
| F (5) | 放款日期 |
| G (6) | 贷款总额 |
| H (7) | 银行放款金额 |
| I (8) | 费用类型 |
| J (9) | 发票金额 |
| K (10) | 邮箱地址 |

4. 生成文件名：`天津银行_{脱敏姓名}_{YYMMDD}_{流水号}.xlsx`
5. 保存到 `{java.io.tmpdir}/agentscope-uploads/`

### Word 生成

1. 从 classpath 加载 `skills/tianjin_bank_invoice/assets/tianjin_bank_template.docx`
2. 获取第一个表格（table），按行列索引填入：

| 单元格 | 字段 |
|--------|------|
| rows[0].cell(1) | 客户姓名 |
| rows[0].cell(3) | 身份证号（作为纳税人识别号） |
| rows[3].cell(1) | 姓名 + 空格 + 电话 |
| rows[3].cell(3) | 邮箱 |
| rows[7].cell(1) | 发票金额 |
| rows[8].cell(1) | 费用类型描述 |

3. 查找包含"提交日期："的段落，替换为 `提交日期：{yyyy年MM月dd日}`
4. 生成文件名：`天津银行_{脱敏姓名}_{YYMMDD}_{流水号}.docx`
5. 保存到 `{java.io.tmpdir}/agentscope-uploads/`

> 注意：Word 模板表格的行列索引需要在实现阶段通过实际打开模板确认，上述索引基于 Python 版代码推断。

## 模板文件引用

通过 ClassLoader 从 classpath 加载模板，路径为 `skills/tianjin_bank_invoice/assets/`。由于 Maven 打包时原技能目录已包含在 classpath 中，无需复制模板文件。

```java
InputStream is = getClass().getClassLoader()
    .getResourceAsStream("skills/tianjin_bank_invoice/assets/tianjin_bank_template.xlsx");
```

## 注册配置

### ToolRegistry

在构造函数中添加：

```java
// Tool 注册
registry.put("generate_tianjin_bank_invoice", TianjinBankInvoiceTool::new);

// Skill 映射
skillToolMap.put("tianjin_bank_invoice_java", List.of("generate_tianjin_bank_invoice"));
```

### SKILL.md

```yaml
---
name: tianjin_bank_invoice_java
description: 根据天津银行发票模板生成 Excel 和 Word 文件（Java 版）
tools:
  - generate_tianjin_bank_invoice
---
```

### agents.json

需要使用此技能的 agent 在 skills 数组中添加 `"tianjin_bank_invoice_java"`。

## 错误处理

- 参数校验：必填参数为空时返回明确的错误提示
- 模板加载失败：返回"模板文件未找到"
- 文件生成失败：捕获异常，返回错误信息字符串

## 约束

- 不修改 `tianjin_bank_invoice/` 目录下的任何文件
- 不引入新的 Maven 依赖（Apache POI 已存在）
- 输出目录与现有上传目录一致：`{java.io.tmpdir}/agentscope-uploads/`
- 所有参数使用 String 类型，内部转换
