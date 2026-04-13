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
