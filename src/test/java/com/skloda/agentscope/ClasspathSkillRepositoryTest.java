package com.skloda.agentscope;

import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * 测试 ClasspathSkillRepository 在 JAR 环境下的行为
 */
public class ClasspathSkillRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSkillRepositoryTest.class);

    public static void main(String[] args) {
        log.info("=== Testing ClasspathSkillRepository in JAR environment ===");
        log.info("Current working directory: {}", System.getProperty("user.dir"));

        // 检查是否在 JAR 中运行
        String protocol = ClasspathSkillRepositoryTest.class.getResource("ClasspathSkillRepositoryTest.class").getProtocol();
        log.info("Running from JAR: {}", "jar".equals(protocol));
        log.info("Resource protocol: {}", protocol);

        // 测试不同的 resourcePath
        String[] testPaths = {
            "skills/docx",
            "skills/pdf",
            "skills/xlsx"
        };

        for (String resourcePath : testPaths) {
            log.info("\n========================================");
            log.info("Testing resource path: '{}'", resourcePath);
            log.info("========================================");

            try {
                diagnoseResourcePath(resourcePath);
                testRepositoryCreation(resourcePath);
            } catch (Exception e) {
                log.error("Failed to test resource path: {}", resourcePath, e);
            }
        }
    }

    private static void diagnoseResourcePath(String resourcePath) throws Exception {
        ClassLoader classLoader = ClasspathSkillRepositoryTest.class.getClassLoader();
        URL resourceUrl = classLoader.getResource(resourcePath);

        log.info("Resource URL: {}", resourceUrl);

        if (resourceUrl == null) {
            log.error("Resource not found: {}", resourcePath);
            return;
        }

        URI uri = resourceUrl.toURI();
        log.info("Resource URI: {}", uri);
        log.info("URI Scheme: {}", uri.getScheme());
        log.info("URI Scheme-specific part: {}", uri.getSchemeSpecificPart());

        if ("jar".equals(uri.getScheme())) {
            log.info("--- JAR Environment Detected ---");

            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
            log.info("FileSystem created: {}", fileSystem);
            log.info("FileSystem separator: {}", fileSystem.getSeparator());
            log.info("FileSystem isOpen: {}", fileSystem.isOpen());

            String schemeSpecificPart = uri.getSchemeSpecificPart();
            String extractedPath = schemeSpecificPart.substring(
                schemeSpecificPart.lastIndexOf("!") + 1
            );
            log.info("Extracted path (with leading slash): '{}'", extractedPath);
            log.info("Extracted path length: {}", extractedPath.length());

            // 尝试不同的路径格式
            String[] pathCandidates = {
                extractedPath,                           // /skills/docx
                extractedPath.startsWith("/") ? extractedPath.substring(1) : "/" + extractedPath,  // skills/docx
                resourcePath                             // skills/docx
            };

            for (String candidate : pathCandidates) {
                log.info("--- Testing path candidate: '{}' ---", candidate);
                try {
                    Path testPath = fileSystem.getPath(candidate);
                    log.info("  Created Path: {}", testPath);
                    log.info("  Path class: {}", testPath.getClass().getName());
                    log.info("  Path isAbsolute: {}", testPath.isAbsolute());
                    log.info("  Path toString: {}", testPath.toString());
                    log.info("  Path toAbsolutePath: {}", testPath.toAbsolutePath());

                    boolean exists = Files.exists(testPath);
                    log.info("  Files.exists(): {}", exists);

                    boolean isDirectory = Files.isDirectory(testPath);
                    log.info("  Files.isDirectory(): {}", isDirectory);

                    if (exists && isDirectory) {
                        log.info("  ✅ SUCCESS: This path works!");
                        // 列出目录内容
                        try (var stream = Files.list(testPath)) {
                            var contents = stream.map(p -> p.getFileName().toString()).toList();
                            log.info("  Directory contents: {}", contents);
                        }
                    } else if (exists) {
                        log.warn("  ⚠️ Path exists but is NOT a directory");
                        Path parent = testPath.getParent();
                        if (parent != null) {
                            log.info("  Parent path: {}", parent);
                            log.info("  Parent exists: {}", Files.exists(parent));
                            log.info("  Parent isDirectory: {}", Files.isDirectory(parent));
                        }
                    } else {
                        log.error("  ❌ Path does NOT exist");
                    }

                } catch (Exception e) {
                    log.error("  ❌ Exception with candidate '{}': {} - {}",
                        candidate, e.getClass().getSimpleName(), e.getMessage(), e);
                }
            }

            fileSystem.close();
            log.info("FileSystem closed");
        }
    }

    private static void testRepositoryCreation(String resourcePath) {
        try {
            log.info("--- Creating ClasspathSkillRepository ---");
            ClasspathSkillRepository repository = new ClasspathSkillRepository(resourcePath);
            log.info("✅ Repository created successfully");

            // 获取内部字段进行诊断
            Field isJarField = ClasspathSkillRepository.class.getDeclaredField("isJar");
            isJarField.setAccessible(true);
            boolean isJar = isJarField.getBoolean(repository);
            log.info("Repository isJar: {}", isJar);

            Field skillBasePathField = ClasspathSkillRepository.class.getDeclaredField("skillBasePath");
            skillBasePathField.setAccessible(true);
            Path skillBasePath = (Path) skillBasePathField.get(repository);
            log.info("Repository skillBasePath: {}", skillBasePath);

            // 测试获取技能名称
            var skillNames = repository.getAllSkillNames();
            log.info("✅ getAllSkillNames() returned: {}", skillNames);

            repository.close();
            log.info("✅ Repository closed successfully");

        } catch (Exception e) {
            log.error("❌ Failed to create/use repository", e);
        }
    }
}
