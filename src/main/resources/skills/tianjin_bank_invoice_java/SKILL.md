---
name: tianjin_bank_invoice_java
description: Generate Tianjin Bank invoice Excel and Word documents from templates. Use when the user needs to create invoice documents for Tianjin Bank with customer information, loan details, and automatic name desensitization in filenames.
tools: generate_tianjin_bank_invoice
---

# Tianjin Bank Invoice Generator (Java)

Generate Excel and Word invoice documents for Tianjin Bank using predefined templates. The tool automatically handles name desensitization for generated filenames.

## What this skill does

- Generates an Excel file (.xlsx) with complete loan and invoice information
- Generates a Word document (.docx) as a digital invoice application form
- Automatically desensitizes customer names in output filenames (e.g., "张三丰" → "张某某")
- Adds application date (submit date) automatically to the Word document

## File structure

```
tianjin_bank_invoice_java/
├── SKILL.md                              # This file
├── assets/
│   ├── tianjin_bank_template.xlsx        # Excel template
│   └── tianjin_bank_template.docx        # Word template
└── (Java implementation in TianjinBankInvoiceTool.java)
```

## Required parameters

All parameters are required:

| Parameter | Description | Example |
|-----------|-------------|---------|
| `name` | Customer name (will be desensitized in filename) | `张三丰` |
| `idCard` | ID card number | `123456200001011234` |
| `phone` | Contact phone | `13800138000` |
| `email` | Email address | `zhang@example.com` |
| `contract` | Contract number | `HT20240410001` |
| `loan` | Loan/Debit number | `JD20240410001` |
| `date` | Loan disbursement date (YYYY-MM-DD) | `2024-04-10` |
| `amount` | Total loan amount | `100000` |
| `bankAmount` | Bank lending amount | `80000` |
| `feeType` | Fee type description | `服务费` |
| `invoice` | Invoice amount | `5000` |
| `serial` | Serial number for filename | `001` |

## Usage

This skill is invoked through the AI agent. Simply provide the required information:

```
请帮我生成天津银行发票。
客户姓名：张三丰
身份证号：110101199003072316
手机：13802213478
邮箱：test@example.com
合同号：HT20240410001
借据号：JD20240410001
放款日期：2024-04-10
贷款总额：100000
银行放款金额：80000
费用类型：服务费
发票金额：5000
流水号：001
```

The agent will call the `generate_tianjin_bank_invoice` tool with these parameters.

## Output

Two files will be generated:

1. **Excel**: `天津银行_张某某_YYMMDD_流水号.xlsx`
   - Contains: Name, ID card, phone, contract, loan, date, amounts, fee type, invoice amount, email

2. **Word**: `天津银行_张某某_YYMMDD_流水号.docx`
   - Digital invoice application form
   - Auto-filled with: Name, ID card (tax ID), phone, email, invoice amount, fee summary
   - Application date added automatically

Files are saved to: `{java.io.tmpdir}/agentscope-uploads/`

## Name desensitization rules

| Original | Desensitized |
|----------|--------------|
| 张三 | 张某 |
| 张三丰 | 张某某 |
| 张三丰四 | 张某某某 |
| 欧阳小明 | 欧某某某 |

The rule: Keep the first character (surname), replace all others with "某".

## Technical Details

- **Implementation**: Pure Java using Apache POI (XSSFWorkbook for Excel, XWPFDocument for Word)
- **Tool Class**: `TianjinBankInvoiceTool.java`
- **Registration**: Auto-registered via `@Tool` annotation scanning
- **No Python dependencies**: Completely self-contained Java implementation
