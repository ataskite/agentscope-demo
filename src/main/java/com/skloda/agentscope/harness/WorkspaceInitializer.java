package com.skloda.agentscope.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class WorkspaceInitializer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInitializer.class);
    private static final String TEMPLATES_PREFIX = "harness-templates/";

    public static void initializeFromTemplates(String agentName, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        copyTemplateDir(TEMPLATES_PREFIX + agentName, workspace);
        log.info("Initialized workspace for '{}' at {}", agentName, workspace);
    }

    public static void initializeSubagentWorkspace(String subagentName, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        copyTemplateDir(TEMPLATES_PREFIX + subagentName, workspace);
        log.info("Initialized subagent workspace for '{}' at {}", subagentName, workspace);
    }

    private static void copyTemplateDir(String resourcePath, Path targetDir) throws IOException {
        List<String> templateFiles = listTemplateResources(resourcePath);
        for (String resource : templateFiles) {
            Path relative = Path.of(resource.substring(resourcePath.length() + 1));
            Path target = targetDir.resolve(relative);
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                try (InputStream is = WorkspaceInitializer.class.getClassLoader()
                        .getResourceAsStream(resource)) {
                    if (is != null) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                        log.debug("Created template file: {}", target);
                    } else {
                        // Template not yet on classpath; create empty placeholder
                        // so directory structure is intact. Will be populated when
                        // template resources are added later.
                        Files.createFile(target);
                        log.debug("Created placeholder file: {}", target);
                    }
                }
            }
        }
    }

    private static List<String> listTemplateResources(String resourcePath) {
        return List.of(
                "complaint-reviewer/AGENTS.md",
                "complaint-reviewer/knowledge/complaint-taxonomy.md",
                "complaint-reviewer/knowledge/regulatory-policy.md",
                "complaint-reviewer/knowledge/action-playbook.md",
                "complaint-reviewer/subagents/root-cause-analyst.md",
                "complaint-reviewer/subagents/trend-analyst.md",
                "complaint-reviewer/subagents/strategy-optimizer.md",
                "complaint-reviewer/subagents/roi-calculator.md",
                "root-cause-analyst/AGENTS.md",
                "root-cause-analyst/skills/statistical-analysis/SKILL.md",
                "root-cause-analyst/skills/fact-chain-extraction/SKILL.md",
                "root-cause-analyst/knowledge/root-cause-framework.md",
                "trend-analyst/AGENTS.md",
                "trend-analyst/skills/cross-day-comparison/SKILL.md",
                "trend-analyst/knowledge/trend-framework.md",
                "strategy-optimizer/AGENTS.md",
                "strategy-optimizer/skills/strategy-formulation/SKILL.md",
                "strategy-optimizer/knowledge/strategy-playbook.md",
                "strategy-optimizer/knowledge/industry-benchmarks.md",
                "roi-calculator/AGENTS.md",
                "roi-calculator/skills/roi-simulation/SKILL.md",
                "roi-calculator/knowledge/cost-model.md",
                "roi-calculator/knowledge/benefit-model.md"
        ).stream()
                .map(name -> TEMPLATES_PREFIX + name)
                .filter(name -> name.startsWith(resourcePath + "/") || name.equals(resourcePath))
                .toList();
    }
}
