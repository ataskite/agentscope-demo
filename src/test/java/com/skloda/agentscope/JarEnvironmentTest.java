package com.skloda.agentscope;

import io.agentscope.core.skill.repository.ClasspathSkillRepository;
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
 * 测试在 JAR 环境下的 ClasspathSkillRepository 行为
 *
 * 运行方式：
 * mvn test -Dtest=JarEnvironmentTest
 * 或打包后运行：
 * java -cp agentscope-demo-1.0-SNAPSHOT.jar com.msxf.agentscope.JarEnvironmentTest
 */
public class JarEnvironmentTest {

    private static final Logger log = LoggerFactory.getLogger(JarEnvironmentTest.class);

    @Test
    public void testDocxSkillInJarEnvironment() throws Exception {
        String resourcePath = "skills/docx";
        log.info("=== Testing resource path: {} ===", resourcePath);

        // 检查运行环境
        String protocol = getClass().getResource("JarEnvironmentTest.class").getProtocol();
        log.info("Running from JAR: {}", "jar".equals(protocol));
        log.info("Resource protocol: {}", protocol);

        diagnoseResourcePath(resourcePath);

        try (ClasspathSkillRepository repository = new ClasspathSkillRepository(resourcePath)) {
            log.info("✅ Repository created successfully");

            var skillNames = repository.getAllSkillNames();
            log.info("✅ getAllSkillNames() returned: {}", skillNames);
        }
    }

    private void diagnoseResourcePath(String resourcePath) throws Exception {
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
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Running JarEnvironmentTest ===");
        System.out.println("Make sure to run this from JAR: java -jar agentscope-demo-1.0-SNAPSHOT.jar");

        JarEnvironmentTest test = new JarEnvironmentTest();
        test.testDocxSkillInJarEnvironment();
    }
}
