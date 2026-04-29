package com.skloda.agentscope.controller;

import com.skloda.agentscope.model.KnowledgeFileStatus;
import com.skloda.agentscope.model.KnowledgeIndexStatus;
import com.skloda.agentscope.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerTest {

    @Mock
    private KnowledgeService knowledgeService;

    @InjectMocks
    private KnowledgeController controller;

    @Test
    void statusReturnsCurrentKnowledgeStatus() {
        KnowledgeIndexStatus expected = KnowledgeIndexStatus.builder()
                .state(KnowledgeIndexStatus.State.READY)
                .knowledgePath("/repo/knowledge")
                .startedAt(OffsetDateTime.parse("2026-04-29T13:00:00+08:00"))
                .finishedAt(OffsetDateTime.parse("2026-04-29T13:00:01+08:00"))
                .documents(List.of(KnowledgeFileStatus.builder()
                        .fileName("guide.md")
                        .relativePath("guide.md")
                        .extension("md")
                        .status(KnowledgeFileStatus.Status.INDEXED)
                        .chunkCount(4)
                        .updatedAt(OffsetDateTime.parse("2026-04-29T13:00:01+08:00"))
                        .build()))
                .build();
        when(knowledgeService.getIndexStatus()).thenReturn(expected);

        KnowledgeIndexStatus actual = controller.status();

        assertEquals(expected, actual);
    }
}
