package com.msxf.agentscope.tool;

import io.agentscope.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /**
     * Skill metadata parsed from SKILL.md frontmatter.
     */
    public record SkillMetadata(String name, String description, List<String> tools) {}

    /**
     * Maps @Tool function name (or skill name) → className key.
     */
    private final Map<String, String> toolToClass = new ConcurrentHashMap<>();

    /**
     * Maps className key → Supplier that creates tool class instances.
     */
    private final Map<String, Supplier<Object>> classSuppliers = new ConcurrentHashMap<>();

    /**
     * Maps className key → list of @Tool function names it provides.
     */
    private final Map<String, List<String>> classToTools = new ConcurrentHashMap<>();

    /**
     * Skill metadata discovered from SKILL.md files.
     */
    private final Map<String, SkillMetadata> skillMetadataMap = new ConcurrentHashMap<>();

    public ToolRegistry() {
        // Phase A: Auto-discover user tool classes via @Tool annotation scanning
        autoScanToolClasses("com.msxf.agentscope.tool");

        // Phase B: Register AgentScope built-in system tools
        registerSystemTools();

        // Phase C: Dynamic skill-to-tool mappings from SKILL.md frontmatter
        registerSkillMappings();

        log.info("ToolRegistry initialized with {} tool functions from {} classes, {} skills",
                toolToClass.size(), classSuppliers.size(), skillMetadataMap.size());
    }

    // ===== Phase A: Auto-scan @Tool annotations =====

    private void autoScanToolClasses(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        // Accept all concrete classes in the package
        scanner.addIncludeFilter((metadataReader, factory) -> true);

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        for (BeanDefinition bd : candidates) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                // Skip ToolRegistry itself
                if (ToolRegistry.class.equals(clazz)) continue;

                List<String> toolNames = extractToolNames(clazz);
                if (!toolNames.isEmpty()) {
                    String key = clazz.getSimpleName();
                    registerClass(key, () -> newInstance(clazz), toolNames.toArray(String[]::new));
                    log.info("  Auto-registered tool class: {} with functions: {}", key, toolNames);
                }
            } catch (ClassNotFoundException e) {
                log.warn("  Could not load class: {}", bd.getBeanClassName());
            }
        }
    }

    private List<String> extractToolNames(Class<?> clazz) {
        List<String> toolNames = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null && !toolAnnotation.name().isEmpty()) {
                toolNames.add(toolAnnotation.name());
            }
        }
        return toolNames;
    }

    private static Object newInstance(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate tool class: " + clazz.getName(), e);
        }
    }

    // ===== Phase B: Register AgentScope built-in system tools =====

    private void registerSystemTools() {
        // Try auto-scanning framework packages first
        int before = classSuppliers.size();
        autoScanToolClasses("io.agentscope.core.tool.file");
        autoScanToolClasses("io.agentscope.core.tool.coding");

        if (classSuppliers.size() > before) {
            log.info("  Auto-scanned {} system tool classes from framework", classSuppliers.size() - before);
        } else {
            // Fallback: framework jars may not expose classpath for scanning
            log.info("  Framework scan found nothing, using explicit system tool registration");
            try {
                Class<?> readFileTool = Class.forName("io.agentscope.core.tool.file.ReadFileTool");
                Class<?> writeFileTool = Class.forName("io.agentscope.core.tool.file.WriteFileTool");
                Class<?> shellCmdTool = Class.forName("io.agentscope.core.tool.coding.ShellCommandTool");

                registerClass("ReadFileTool", () -> newInstance(readFileTool),
                        extractToolNames(readFileTool).toArray(String[]::new));
                registerClass("WriteFileTool", () -> newInstance(writeFileTool),
                        extractToolNames(writeFileTool).toArray(String[]::new));
                registerClass("ShellCommandTool", () -> newInstance(shellCmdTool),
                        extractToolNames(shellCmdTool).toArray(String[]::new));
            } catch (ClassNotFoundException e) {
                log.warn("  AgentScope system tool classes not found on classpath", e);
            }
        }
    }

    // ===== Phase C: Dynamic skill-to-tool mapping from SKILL.md =====

    private void registerSkillMappings() {
        try {
            Resource[] skillFiles = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:skills/*/SKILL.md");

            for (Resource skillFile : skillFiles) {
                try {
                    Map<String, Object> frontmatter = parseYamlFrontmatter(skillFile);
                    if (frontmatter == null) continue;

                    String skillName = (String) frontmatter.get("name");
                    String description = (String) frontmatter.get("description");
                    List<String> tools = resolveTools(frontmatter.get("tools"));

                    if (skillName == null || tools == null || tools.isEmpty()) {
                        log.warn("  Skipping skill with missing name or tools: {}", skillFile.getURL());
                        continue;
                    }

                    // Find the supplier from the first tool's registered class
                    String firstTool = tools.get(0);
                    String className = toolToClass.get(firstTool);
                    if (className == null) {
                        log.warn("  Skill '{}' references tool '{}' which is not registered", skillName, firstTool);
                        continue;
                    }
                    Supplier<Object> supplier = classSuppliers.get(className);
                    if (supplier == null) {
                        log.warn("  Skill '{}' references class '{}' which has no supplier", skillName, className);
                        continue;
                    }

                    // Register skill name as a mapping key
                    registerClass(skillName, supplier, tools.toArray(String[]::new));

                    // Store metadata for AgentConfigService
                    skillMetadataMap.put(skillName, new SkillMetadata(skillName, description, tools));
                    log.info("  Registered skill: {} → {} (tools: {})", skillName, className, tools);
                } catch (Exception e) {
                    log.warn("  Failed to parse skill file: {}", skillFile, e);
                }
            }
        } catch (Exception e) {
            log.warn("  Failed to scan skill files", e);
        }
    }

    private Map<String, Object> parseYamlFrontmatter(Resource resource) throws Exception {
        StringBuilder yaml = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean inFrontmatter = false;
            int dashes = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("---")) {
                    dashes++;
                    if (dashes == 1) { inFrontmatter = true; continue; }
                    break;
                }
                if (inFrontmatter) yaml.append(line).append("\n");
            }
        }
        if (yaml.isEmpty()) return null;
        Yaml y = new Yaml();
        return y.load(yaml.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveTools(Object toolsObj) {
        if (toolsObj == null) return Collections.emptyList();
        if (toolsObj instanceof List) return (List<String>) toolsObj;
        if (toolsObj instanceof String s) {
            return Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // ===== Core registration =====

    private void registerClass(String className, Supplier<Object> supplier, String... toolNames) {
        classSuppliers.put(className, supplier);
        classToTools.put(className, Arrays.asList(toolNames));
        // Register className itself so hasTool/getTool work with skill names
        toolToClass.put(className, className);
        for (String toolName : toolNames) {
            toolToClass.put(toolName, className);
        }
    }

    // ===== Public API (unchanged signatures) =====

    public boolean hasTool(String name) {
        return toolToClass.containsKey(name);
    }

    public Object getTool(String name) {
        String className = toolToClass.get(name);
        if (className == null) {
            throw new IllegalArgumentException("Tool not registered: " + name);
        }
        Supplier<Object> supplier = classSuppliers.get(className);
        if (supplier == null) {
            throw new IllegalArgumentException("Tool class not found: " + className);
        }
        return supplier.get();
    }

    public String getClassName(String toolName) {
        return toolToClass.get(toolName);
    }

    public List<Object> getDeduplicatedInstances(List<String> toolNames) {
        Map<String, Object> instances = new LinkedHashMap<>();
        for (String name : toolNames) {
            String className = toolToClass.get(name);
            if (className != null && !instances.containsKey(className)) {
                Supplier<Object> supplier = classSuppliers.get(className);
                if (supplier != null) {
                    instances.put(className, supplier.get());
                }
            }
        }
        return new ArrayList<>(instances.values());
    }

    public Set<String> getAllToolNames() {
        return Collections.unmodifiableSet(toolToClass.keySet());
    }

    public List<String> getToolNamesForClass(String className) {
        return classToTools.getOrDefault(className, Collections.emptyList());
    }

    public Map<String, SkillMetadata> getSkillMetadataMap() {
        return Collections.unmodifiableMap(skillMetadataMap);
    }
}
