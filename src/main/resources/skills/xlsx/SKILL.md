---
name: xlsx
description: Use this skill when the user wants to read, analyze, or extract data from Excel spreadsheets (.xlsx files). Triggers include: uploading an .xlsx file and asking to summarize, extract specific data, analyze trends, or perform calculations.
---

# XLSX Spreadsheet Analysis

## Overview

An .xlsx file is a ZIP archive containing XML files representing spreadsheet data. This skill provides a Java tool to parse .xlsx files and extract their content including sheet names, headers, and row data.

## Available Tool

### parse_xlsx

Extracts all content from an .xlsx file.

**Parameters:**
- `filePath` (String, required): Absolute path to the .xlsx file on the server

**Returns:** Spreadsheet content in a readable format:
- Sheet names
- Headers (first row of each sheet)
- Data rows with values separated by ` | `
- Cell types are handled automatically (numbers, dates, formulas, booleans, text)

## How to Use

1. The user uploads an .xlsx file — you will receive the file path in the message
2. Call `parse_xlsx` with the file path
3. Based on the extracted content, perform the user's requested task:
   - Summarize the spreadsheet structure and key data
   - Extract specific rows or columns
   - Identify trends or patterns
   - Compare data across sheets
   - Perform calculations or analysis
   - Export specific data to other formats

## Data Format

The parsed output uses ` | ` as the column delimiter. Empty cells are shown as `(blank)`.
