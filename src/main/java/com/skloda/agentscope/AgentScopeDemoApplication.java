package com.skloda.agentscope;

import com.skloda.agentscope.service.KnowledgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AgentScope Demo Application.
 */
@SpringBootApplication
@EnableConfigurationProperties(KnowledgeProperties.class)
public class AgentScopeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentScopeDemoApplication.class, args);
        System.out.println("""

                ================================================
                AgentScope Demo Application Started!

                Open http://localhost:8080 in your browser.
                ================================================
                """);
    }
}
