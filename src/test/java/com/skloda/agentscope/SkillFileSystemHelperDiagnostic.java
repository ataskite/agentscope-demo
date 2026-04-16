package com.skloda.agentscope;

import io.agentscope.core.skill.util.SkillFileSystemHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * 诊断 SkillFileSystemHelper 在 JAR 环境下的行为
 */
public class SkillFileSystemHelperDiagnostic {

    private static final Logger log = LoggerFactory.getLogger(SkillFileSystemHelperDiagnostic.class);

    @Test
    public void testSkillFileSystemHelperInJar() throws Exception {
        String resourcePath = "skills/docx";

        // 检查运行环境
        URL classUrl = getClass().getResource("SkillFileSystemHelperDiagnostic.class");
        boolean isJar = "jar".equals(classUrl.getProtocol());
        log.info("Running from JAR: {}", isJar);

        ClassLoader classLoader = getClass().getClassLoader();
        URL resourceUrl = classLoader.getResource(resourcePath);

        if (resourceUrl == null) {
            log.error("Resource not found: {}", resourcePath);
            return;
        }

        URI uri = resourceUrl.toURI();
        log.info("Resource URI: {}", uri);

        if ("jar".equals(uri.getScheme())) {
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());

            String schemeSpecificPart = uri.getSchemeSpecificPart();
            String extractedPath = schemeSpecificPart.substring(
                schemeSpecificPart.lastIndexOf("!") + 1
            );

            log.info("Extracted path: '{}'", extractedPath);

            // 测试不同的路径
            Path[] testPaths = {
                fileSystem.getPath(extractedPath),           // /skills/docx
                fileSystem.getPath(extractedPath.substring(1)), // skills/docx
            };

            for (Path basePath : testPaths) {
                log.info("\n========================================");
                log.info("Testing with basePath: {}", basePath);
                log.info("========================================");

                try {
                    // 直接测试 SkillFileSystemHelper
                    log.info("--- Calling SkillFileSystemHelper.getAllSkillNames() ---");
                    var skillNames = SkillFileSystemHelper.getAllSkillNames(basePath);
                    log.info("Result: {}", skillNames);

                    // 测试 skillExists
                    log.info("--- Calling SkillFileSystemHelper.skillExists() ---");
                    boolean exists = SkillFileSystemHelper.skillExists(basePath, "docx");
                    log.info("skillExists('docx'): {}", exists);

                    // 测试 getSkill
                    log.info("--- Calling SkillFileSystemHelper.loadSkill() ---");
                    try {
                        var skill = SkillFileSystemHelper.loadSkill(basePath, "docx", "test-source");
                        log.info("loadSkill('docx') SUCCESS:");
                        log.info("  name: {}", skill.getName());
                        log.info("  description: {}", skill.getDescription());
                        log.info("  content length: {}", skill.getSkillContent().length());
                    } catch (Exception e) {
                        log.error("loadSkill('docx') FAILED:", e);
                    }

                    // 手动检查目录结构
                    log.info("--- Manual directory inspection ---");
                    log.info("basePath exists: {}", Files.exists(basePath));
                    log.info("basePath isDirectory: {}", Files.isDirectory(basePath));

                    if (Files.isDirectory(basePath)) {
                        try (var stream = Files.list(basePath)) {
                            var entries = stream.toList();
                            log.info("Directory entries: {}", entries.size());
                            for (Path entry : entries) {
                                log.info("  - {} (isDir: {}, isFile: {})",
                                    entry.getFileName(),
                                    Files.isDirectory(entry),
                                    Files.isRegularFile(entry));
                            }
                        }
                    }

                } catch (Exception e) {
                    log.error("Failed with basePath: {}", basePath, e);
                }
            }

            fileSystem.close();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Running SkillFileSystemHelperDiagnostic ===");
        System.out.println("Make sure to run from JAR: java -jar agentscope-demo-1.0-SNAPSHOT.jar");

        SkillFileSystemHelperDiagnostic test = new SkillFileSystemHelperDiagnostic();
        test.testSkillFileSystemHelperInJar();
    }
}
