package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MsgHubConfigTest {

    @Test
    void constructor_createsConfigWithDefaults() {
        MsgHubConfig config = new MsgHubConfig();

        assertNotNull(config);
        assertEquals(3, config.getRounds());
        assertEquals("MODERATOR", config.getSummaryRole());
    }

    @Test
    void builder_createsConfigWithCustomValues() {
        MsgHubConfig config = MsgHubConfig.builder()
                .rounds(5)
                .summaryRole("PARTICIPANT")
                .build();

        assertEquals(5, config.getRounds());
        assertEquals("PARTICIPANT", config.getSummaryRole());
    }

    @Test
    void builder_withDefaultRounds() {
        MsgHubConfig config = MsgHubConfig.builder()
                .rounds(3)
                .summaryRole("MODERATOR")
                .build();

        assertEquals(3, config.getRounds());
        assertEquals("MODERATOR", config.getSummaryRole());
    }

    @Test
    void builder_withDefaultSummaryRole() {
        MsgHubConfig config = MsgHubConfig.builder()
                .rounds(10)
                .summaryRole("MODERATOR")
                .build();

        assertEquals(10, config.getRounds());
        assertEquals("MODERATOR", config.getSummaryRole());
    }

    @Test
    void allArgsConstructor_createsConfigWithAllValues() {
        MsgHubConfig config = new MsgHubConfig(7, "PARTICIPANT");

        assertEquals(7, config.getRounds());
        assertEquals("PARTICIPANT", config.getSummaryRole());
    }

    @Test
    void setter_canModifyRounds() {
        MsgHubConfig config = new MsgHubConfig();
        config.setRounds(15);

        assertEquals(15, config.getRounds());
        assertEquals("MODERATOR", config.getSummaryRole());
    }

    @Test
    void setter_canModifySummaryRole() {
        MsgHubConfig config = new MsgHubConfig();
        config.setSummaryRole("PARTICIPANT");

        assertEquals(3, config.getRounds());
        assertEquals("PARTICIPANT", config.getSummaryRole());
    }

    @Test
    void summaryRole_moderator() {
        MsgHubConfig config = MsgHubConfig.builder()
                .summaryRole("MODERATOR")
                .build();

        assertEquals("MODERATOR", config.getSummaryRole());
    }

    @Test
    void summaryRole_participant() {
        MsgHubConfig config = MsgHubConfig.builder()
                .summaryRole("PARTICIPANT")
                .build();

        assertEquals("PARTICIPANT", config.getSummaryRole());
    }

    @Test
    void rounds_one() {
        MsgHubConfig config = MsgHubConfig.builder()
                .rounds(1)
                .build();

        assertEquals(1, config.getRounds());
    }

    @Test
    void rounds_many() {
        MsgHubConfig config = MsgHubConfig.builder()
                .rounds(10)
                .build();

        assertEquals(10, config.getRounds());
    }
}
