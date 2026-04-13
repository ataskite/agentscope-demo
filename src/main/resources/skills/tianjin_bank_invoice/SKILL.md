---
name: tianjin_bank_invoice
description: Generate Tianjin Bank invoice Excel and Word documents from templates. Use when the user needs to create invoice documents for Tianjin Bank with customer information, loan details, and automatic name desensitization in filenames.
---

# Tianjin Bank Invoice Generator

Generate Excel and Word invoice documents for Tianjin Bank using predefined templates. The script automatically handles name desensitization for generated filenames.

## What this skill does

- Generates an Excel file (.xlsx) with complete loan and invoice information
- Generates a Word document (.docx) as a digital invoice application form
- Automatically desensitizes customer names in output filenames (e.g., "张三丰" → "张某某")
- Adds application date (submit date) automatically to the Word document

## File structure

```
tianjin_bank_invoice/
├── SKILL.md                    # This file
├── scripts/
│   └── generate_tianjin_bank.py # Python generation script
└── assets/
    ├── tianjin_bank_template.xlsx  # Excel template
    └── tianjin_bank_template.docx  # Word template
```

## Required parameters

All parameters are required:

| Parameter | Description | Example |
|-----------|-------------|---------|
| `--name`, `-n` | Customer name (will be desensitized in filename) | `张三丰` |
| `--id-card`, `-i` | ID card number | `123456200001011234` |
| `--phone`, `-p` | Contact phone | `13800138000` |
| `--email`, `-e` | Email address | `zhang@example.com` |
| `--contract`, `-c` | Contract number | `HT20240410001` |
| `--loan`, `-l` | Loan/Debit number | `JD20240410001` |
| `--date`, `-d` | Loan disbursement date (YYYY-MM-DD or YYYYMMDD) | `2024-04-10` |
| `--amount` | Total loan amount | `100000` |
| `--bank-amount` | Bank lending amount | `80000` |
| `--fee-type` | Fee type description | `服务费` |
| `--invoice` | Invoice amount | `5000` |
| `--serial`, `-s` | Serial number for filename | `001` |

## Optional parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `--template-dir` | Template files directory | Current directory |
| `--file-date` | Date in filename (YYMMDD) | Today |
| `--output`, `-o` | Output directory | Current directory |

## Usage

From the skill directory, run:

```bash
python3 scripts/generate_tianjin_bank.py \
  --name "张三丰" \
  --id-card "123456200001011234" \
  --phone "13800138000" \
  --email "zhangsan@example.com" \
  --contract "HT20240410001" \
  --loan "JD20240410001" \
  --date "2024-04-10" \
  --amount 100000 \
  --bank-amount 80000 \
  --fee-type "服务费" \
  --invoice 5000 \
  --serial "001"
```

## Output

Two files will be generated:

1. **Excel**: `天津银行_张某某_YYMMDD_流水号.xlsx`
   - Contains: Name, ID card, phone, contract, loan, date, amounts, fee type, invoice amount, email

2. **Word**: `天津银行_张某某_YYMMDD_流水号.docx`
   - Digital invoice application form
   - Auto-filled with: Name, ID card (tax ID), phone, email, invoice amount, fee summary
   - Application date added automatically

## Name desensitization rules

| Original | Desensitized |
|----------|--------------|
| 张三 | 张某 |
| 张三丰 | 张某某 |
| 张三丰四 | 张某某某 |
| 欧阳小明 | 欧某某某 |

The rule: Keep the first character (surname), replace all others with "某".

## Dependencies

The script requires:
- Python 3
- `openpyxl` - for Excel file handling
- `python-docx` - for Word file handling

Install dependencies:
```bash
pip install openpyxl python-docx
```
