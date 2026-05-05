package com.skloda.agentscope.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFrontendApprovalLayoutTest {

    @Test
    void approvalResumeReplyIsAnchoredAfterApprovalCard() throws Exception {
        String chatJs = Files.readString(Path.of("src/main/resources/static/scripts/chat.js"));

        assertTrue(chatJs.contains("addAgentBubbleAfter(card)"),
                "approval resume reply should be inserted after its approval card");
    }

    @Test
    void chatPageUsesCurrentStaticAssetVersion() throws Exception {
        String chatHtml = Files.readString(Path.of("src/main/resources/templates/chat.html"));

        assertTrue(chatHtml.contains("/scripts/chat.js?v=2.6"),
                "chat.html should bump the script version so browsers stop using the stale approval UI");
        assertTrue(chatHtml.contains("/styles/chat.css?v=2.6"),
                "chat.html should bump the stylesheet version so browsers stop using stale approval card styles");
        assertTrue(chatHtml.contains("rel=\"icon\" href=\"data:,\""),
                "chat.html should avoid a noisy /favicon.ico 404 in the browser console");
    }

    @Test
    void stateModuleUsesSharedVersionAndIdempotentWindowProperties() throws Exception {
        String chatJs = Files.readString(Path.of("src/main/resources/static/scripts/chat.js"));
        String agentsJs = Files.readString(Path.of("src/main/resources/static/scripts/modules/agents.js"));
        String stateJs = Files.readString(Path.of("src/main/resources/static/scripts/state.js"));

        assertTrue(chatJs.contains("./state.js?v=2.4"),
                "chat.js should import the versioned state module");
        assertTrue(agentsJs.contains("../state.js?v=2.4"),
                "agents.js should import the same versioned state module");
        assertTrue(stateJs.contains("window.__agentScopeState"),
                "state.js should keep duplicate module loads on a shared state object");
        assertTrue(stateJs.contains("defineWindowStateProperty"),
                "state.js should define window properties idempotently");
    }

    @Test
    void approvalCardShowsRequestHeaderAndFinalState() throws Exception {
        String chatJs = Files.readString(Path.of("src/main/resources/static/scripts/chat.js"));
        String chatCss = Files.readString(Path.of("src/main/resources/static/styles/modules/chat.css"));

        assertTrue(chatJs.contains("请求人工审批"),
                "approval card title should read 请求人工审批");
        assertTrue(chatJs.contains("approval-collapsed"),
                "approval card should collapse after a decision");
        assertTrue(chatJs.contains("toggleApprovalCard"),
                "approval card header should toggle collapsed approval details after a decision");
        assertTrue(chatJs.contains("aria-expanded"),
                "approval card should expose expanded state for the clickable header");
        assertTrue(chatJs.contains("已批准，正在继续执行"),
                "approval card should show approved state while resuming");
        assertTrue(chatJs.contains("已批准"),
                "approval card should show a final approved state after resume completes");
        assertTrue(chatCss.contains(".approval-header-text"),
                "approval header should use a dedicated header-style text class");
        assertTrue(chatCss.contains(".approval-card-wrapper.approval-collapsed .approval-toggle"),
                "collapsed approval cards should show a rotated disclosure indicator");
        assertTrue(chatCss.contains(".approval-approved .approval-status"),
                "approved state should be styled visibly");
    }
}
