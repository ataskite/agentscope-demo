package com.msxf.agentscope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentScope Demo Application
 * <p>
 * This is the main Spring Boot application class for AgentScope demo.
 * Run this class to start the application, or run specific example classes:
 * <ul>
 *   <li>{@link BasicChatExample} - Simple chat with ReActAgent</li>
 *   <li>{@link ToolCallingExample} - Agent using custom tools</li>
 * </ul>
 */
@SpringBootApplication
public class AgentScopeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentScopeDemoApplication.class, args);
        System.out.println("""

                ================================================
                AgentScope Demo Application Started!

                Available Examples:
                1. BasicChatExample - Simple chat with ReActAgent
                2. ToolCallingExample - Agent using custom tools

                To run examples, use:
                mvn exec:java -Dexec.mainClass="com.msxf.agentscope.BasicChatExample"
                mvn exec:java -Dexec.mainClass="com.msxf.agentscope.ToolCallingExample"

                Or run the example classes directly from your IDE.
                ================================================
                """);
    }
}
