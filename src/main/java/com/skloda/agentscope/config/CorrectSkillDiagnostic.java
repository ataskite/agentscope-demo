package com.skloda.agentscope.config;

import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 正确的 Skill 加载诊断
 *
 * 激活方式：--spring.profiles.active=correct-skill-diagnostic
 */
@Component
@Profile("correct-skill-diagnostic")
@Order(1)
public class CorrectSkillDiagnostic implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CorrectSkillDiagnostic.class);

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("Correct Skill Loading Diagnostic");
        log.info("========================================");

        // 检查运行环境
        String protocol = getClass().getResource("CorrectSkillDiagnostic.class").getProtocol();
        boolean isJar = "jar".equals(protocol);
        log.info("Running from JAR: {}", isJar);

        // 正确的用法：传入父目录 "skills"
        String resourcePath = "skills";
        log.info("Resource path: '{}'", resourcePath);

        try (ClasspathSkillRepository repository = new ClasspathSkillRepository(resourcePath)) {
            log.info("✅ Repository created successfully");

            // 获取所有技能名称
            var skillNames = repository.getAllSkillNames();
            log.info("✅ getAllSkillNames() returned: {}", skillNames);
            log.info("   Total skills found: {}", skillNames.size());

            // 测试加载每个技能
            for (String skillName : skillNames) {
                log.info("\n--- Testing skill: {} ---", skillName);
                try {
                    var skill = repository.getSkill(skillName);
                    log.info("✅ Loaded skill '{}':", skillName);
                    log.info("   name: {}", skill.getName());
                    log.info("   description: {}", skill.getDescription());
                    log.info("   content length: {} chars", skill.getSkillContent().length());
                } catch (Exception e) {
                    log.error("❌ Failed to load skill '{}':", skillName, e);
                }
            }

            // 测试 getAllSkills
            log.info("\n--- Testing getAllSkills() ---");
            var allSkills = repository.getAllSkills();
            log.info("✅ getAllSkills() returned {} skills", allSkills.size());
            for (var skill : allSkills) {
                log.info("   - {} ({})", skill.getName(), skill.getSource());
            }

        } catch (Exception e) {
            log.error("❌ Repository test failed:", e);
        }

        log.info("\n========================================");
        log.info("Diagnostic Complete");
        log.info("========================================");

        System.exit(0);
    }
}
