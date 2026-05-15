package com.skloda.agentscope;

import com.skloda.agentscope.service.KnowledgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EnableConfigurationProperties(KnowledgeProperties.class)
public class AgentScopeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentScopeDemoApplication.class, args);
    }

    @Component
    static class StartupListener {

        @EventListener
        public void onServerReady(WebServerInitializedEvent event) {
            int port = event.getWebServer().getPort();
            System.out.printf("""

                    ================================================
                    AgentScope Demo Application Started!

                    Open http://localhost:%d in your browser.
                    ================================================
                    """, port);
        }
    }
}
