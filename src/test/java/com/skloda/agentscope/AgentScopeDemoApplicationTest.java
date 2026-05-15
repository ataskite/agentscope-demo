package com.skloda.agentscope;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import com.skloda.agentscope.service.KnowledgeService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgentScopeDemoApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
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
