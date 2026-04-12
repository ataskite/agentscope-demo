---
name: docx
description: "Use this skill when the user wants to read, analyze, or extract content from Word documents (.docx files). Triggers include: uploading a .docx file and asking to summarize, extract key points, translate, or analyze its content."
tools: parse_docx
---

# DOCX Document Analysis

## Overview

A .docx file is a ZIP archive containing XML files. This skill provides a Java tool to parse .docx files and extract their full text content including headings, paragraphs, tables, and lists.

## Available Tool

### parse_docx

Extracts all text content from a .docx file.

**Parameters:**
- `filePath` (String, required): Absolute path to the .docx file on the server

**Returns:** Full text content of the document with structure preserved:
- Headings are marked with `#` prefix (level 1-6)
- Paragraphs are separated by blank lines
- Table content is formatted as rows
- List items are marked with bullets or numbers

## How to Use

1. The user uploads a .docx file — you will receive the file path in the message
2. Call `parse_docx` with the file path
3. Based on the extracted content, perform the user's requested task:
   - Summarize the document
   - Extract key points or action items
   - Answer questions about the content
   - Translate sections
   - Compare with other documents
