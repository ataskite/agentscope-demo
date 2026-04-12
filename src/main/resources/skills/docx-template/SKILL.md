---
name: docx-template
description: "Use when user uploads a Word document (.docx) template and wants to fill or replace variables/placeholders. Triggers include mentions of \"模板\", \"template\", \"替换变量\", \"fill in\", \"变量填充\", \"编辑\", \"edit\"."
tools: parse_docx, edit_docx
---

# DOCX Template Variable Replacement

## Purpose
Edit Word document templates by replacing placeholder variables with actual values.

## Workflow
1. **Understand the template**: Call `parse_docx` to read the document content and identify placeholder variables (e.g., {{name}}, {{date}}, $variable)

2. **Clarify values**: Ask the user to provide values for each identified placeholder if not already provided

3. **Execute replacement**: Call `edit_docx` with the file path and replacements array in JSON format:
   ```json
   [
     {"placeholder": "{{name}}", "value": "张三"},
     {"placeholder": "{{date}}", "value": "2026年4月11日"}
   ]
   ```

4. **Report result**: Inform the user of the new file location and number of replacements made

## Placeholder Format
Common patterns include:
- Double braces: `{{variable_name}}`
- Dollar format: `$variable_name`
- Custom markers: User may specify any pattern

## Example Interaction
User: "Fill in this contract template"
→ parse_docx to find {{甲方}}, {{乙方}}, {{日期}}
→ Ask for values if not provided
→ edit_docx with replacements
→ Report completion with new file path

## Notes
- The edited file is saved with `_edited` suffix
- Original file is never modified
- Replacements apply to paragraphs and table cells
