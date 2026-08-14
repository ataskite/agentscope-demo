package com.skloda.agentscope;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.skloda.agentscope.service.KnowledgeService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgentScopeDemoApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private KnowledgeService knowledgeService;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    void startupListenerIsRegistered() {
        assertTrue(applicationContext.getBeansOfType(AgentScopeDemoApplication.StartupListener.class).size() > 0);
    }
}
