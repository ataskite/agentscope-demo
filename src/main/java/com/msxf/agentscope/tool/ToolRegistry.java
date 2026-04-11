package com.msxf.agentscope.tool;

import com.msxf.agentscope.model.SimpleTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, Supplier<Object>> registry = new ConcurrentHashMap<>();

    public ToolRegistry() {
        // Direct tool registrations (referenced by tools field in config)
        registry.put("SimpleTools", SimpleTools::new);

        // Skill-to-tool mappings (referenced by skills field in config)
        registry.put("docx", DocxParserTool::new);
        registry.put("pdf", PdfParserTool::new);
        registry.put("xlsx", XlsxParserTool::new);
        registry.put("docx-template", DocxParserTool::new);

        log.info("ToolRegistry initialized with {} mappings: {}", registry.size(), registry.keySet());
    }

    public void register(String name, Supplier<Object> supplier) {
        registry.put(name, supplier);
    }

    /**
     * Get a new tool instance by name.
     * @throws IllegalArgumentException if name is not registered
     */
    public Object getTool(String name) {
        Supplier<Object> supplier = registry.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Tool not registered: " + name);
        }
        return supplier.get();
    }

    public boolean hasTool(String name) {
        return registry.containsKey(name);
    }
}
