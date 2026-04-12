package com.msxf.agentscope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentScope Demo Application.
 */
@SpringBootApplication
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
