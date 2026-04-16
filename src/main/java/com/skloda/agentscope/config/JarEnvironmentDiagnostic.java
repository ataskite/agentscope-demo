package com.skloda.agentscope.config;

import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * JAR 环境诊断工具
 *
 * 激活方式：运行时添加 --spring.profiles.active=jar-diagnostic
 *
 * 示例：
 * java -jar target/agentscope-demo-1.0-SNAPSHOT.jar --spring.profiles.active=jar-diagnostic
 */
@Component
@Profile("jar-diagnostic")
@Order(1)
public class JarEnvironmentDiagnostic implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JarEnvironmentDiagnostic.class);

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("JAR Environment Diagnostic");
        log.info("========================================");

        // 检查运行环境
        URL classUrl = getClass().getResource("JarEnvironmentDiagnostic.class");
        log.info("Class resource URL: {}", classUrl);
        log.info("Class resource protocol: {}", classUrl.getProtocol());
        boolean isJar = "jar".equals(classUrl.getProtocol());
        log.info("Running from JAR: {}", isJar);

        if (!isJar) {
            log.warn("Not running from JAR, skipping JAR-specific tests");
            log.warn("To test JAR environment, run: java -jar target/agentscope-demo-1.0-SNAPSHOT.jar --spring.proides.active=jar-diagnostic");
            return;
        }

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
                testResourcePath(resourcePath);
                testRepositoryCreation(resourcePath);
            } catch (Exception e) {
                log.error("Failed to test resource path: {}", resourcePath, e);
            }
        }

        log.info("\n========================================");
        log.info("Diagnostic Complete");
        log.info("========================================");

        // 退出应用，避免继续启动
        System.exit(0);
    }

    private void testResourcePath(String resourcePath) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
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

            String schemeSpecificPart = uri.getSchemeSpecificPart();
            String extractedPath = schemeSpecificPart.substring(
                schemeSpecificPart.lastIndexOf("!") + 1
            );
            log.info("Extracted path (with leading slash): '{}'", extractedPath);
            log.info("Extracted path length: {}", extractedPath.length());
            log.info("Extracted path starts with /: {}", extractedPath.startsWith("/"));

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
                    log.info("  Path isAbsolute: {}", testPath.isAbsolute());
                    log.info("  Path toString: {}", testPath.toString());

                    boolean exists = Files.exists(testPath);
                    log.info("  Files.exists(): {}", exists);

                    boolean isDirectory = Files.isDirectory(testPath);
                    log.info("  Files.isDirectory(): {}", isDirectory);

                    if (exists && isDirectory) {
                        log.info("  ✅ SUCCESS: This path works!");
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
                        candidate, e.getClass().getSimpleName(), e.getMessage());
                }
            }

            fileSystem.close();
            log.info("FileSystem closed");
        }
    }

    private void testRepositoryCreation(String resourcePath) {
        try {
            log.info("--- Creating ClasspathSkillRepository ---");
            ClasspathSkillRepository repository = new ClasspathSkillRepository(resourcePath);
            log.info("✅ Repository created successfully");

            var skillNames = repository.getAllSkillNames();
            log.info("✅ getAllSkillNames() returned: {}", skillNames);

            repository.close();
            log.info("✅ Repository closed successfully");

        } catch (Exception e) {
            log.error("❌ Failed to create/use repository", e);
        }
    }
}
