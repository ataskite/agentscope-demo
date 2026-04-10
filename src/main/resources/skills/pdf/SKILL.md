---
name: pdf
description: Use this skill when the user wants to read or extract text from PDF files. If the user uploads a .pdf file or asks to analyze a PDF document, use this skill.
---

# PDF Document Analysis

## Overview

This skill provides a Java tool to parse PDF files and extract their text content from all pages.

## Available Tool

### parse_pdf

Extracts text content from a PDF file.

**Parameters:**
- `filePath` (String, required): Absolute path to the PDF file on the server

**Returns:** Extracted text content from all pages, including:
- Page-separated text
- Document metadata (title, author) if available

## How to Use

1. The user uploads a .pdf file — you will receive the file path in the message
2. Call `parse_pdf` with the file path
3. Based on the extracted content, perform the user's requested task:
   - Summarize the PDF content
   - Extract key information
   - Answer questions about the document
   - Compare multiple documents
