# TEST.md — cli-anything-agentscope

## Test Inventory Plan

| File | Type | Planned Count |
|------|------|---------------|
| `test_core.py` | Unit tests (synthetic, no server) | ~25 |
| `test_full_e2e.py` | E2E tests (requires running server) | ~15 |

## Unit Test Plan

### `utils/agentscope_backend.py` (mock HTTP)
- `test_get_base_url_default` — default URL from env
- `test_get_base_url_custom` — custom URL from env var
- `test_server_status_ok` — mock 200 response
- `test_server_status_offline` — mock connection error
- `test_list_agents` — mock agent list
- `test_get_agent` — mock single agent
- `test_list_sessions` — mock session list
- `test_create_session` — mock session creation
- `test_delete_session` — mock session deletion
- `test_upload_file` — mock file upload
- `test_list_knowledge_docs` — mock doc list
- `test_upload_knowledge` — mock knowledge upload
- `test_remove_knowledge` — mock knowledge removal
- `test_search_knowledge` — mock knowledge search
- `test_get_skill_info` — mock skill info
- `test_get_tool_info` — mock tool info

### `core/chat.py` (pure functions)
- `test_extract_text_empty` — empty events
- `test_extract_text_basic` — single text event
- `test_extract_text_multiple` — multiple text events
- `test_extract_debug` — filters debug events
- `test_extract_metrics` — aggregates token counts
- `test_extract_metrics_empty` — no events

### `core/project.py` (delegates to backend)
- `test_status` — wraps server_status
- `test_info` — wraps with base_url

## E2E Test Plan

### Server connectivity
- `test_server_status` — real server health check

### Agent operations
- `test_list_agents` — list all agents, verify structure
- `test_get_agent` — get specific agent, verify fields

### Session operations
- `test_session_lifecycle` — create → list → delete
- `test_session_list` — list sessions without error

### Chat operations
- `test_chat_send_text` — send text message, get response
- `test_chat_send_streaming` — stream a message, verify text events
- `test_chat_send_with_session` — create session, send within session

### Knowledge operations
- `test_knowledge_list` — list docs (may be empty)
- `test_knowledge_upload_and_search` — upload doc, search, remove

### File operations
- `test_file_upload` — upload a test file

### CLI subprocess tests
- `test_cli_help` — `--help` returns 0
- `test_cli_server_status_json` — `--json server status`
- `test_cli_agent_list_json` — `--json agent list`

### Realistic Workflow Scenarios

**Workflow 1: Complete chat session**
1. Create session with chat-basic agent
2. Send "Hello" message
3. Send follow-up message in same session
4. List sessions, verify message count
5. Delete session

**Workflow 2: Document analysis pipeline**
1. Upload a test text file
2. Send message referencing the file
3. Verify response contains text events

**Workflow 3: Knowledge base Q&A**
1. Upload a text document to knowledge base
2. Search for content
3. Verify search returns results
4. Remove document
5. Verify removal

---

## Test Results

### Unit Tests (test_core.py)

```
cli_anything/agentscope/tests/test_core.py::TestBaseURL::test_custom_url PASSED
cli_anything/agentscope/tests/test_core.py::TestBaseURL::test_custom_url_strips_trailing_slash PASSED
cli_anything/agentscope/tests/test_core.py::TestBaseURL::test_default_url PASSED
cli_anything/agentscope/tests/test_core.py::TestServerStatus::test_status_offline PASSED
cli_anything/agentscope/tests/test_core.py::TestServerStatus::test_status_ok PASSED
cli_anything/agentscope/tests/test_core.py::TestListAgents::test_list_agents PASSED
cli_anything/agentscope/tests/test_core.py::TestGetAgent::test_get_agent PASSED
cli_anything/agentscope/tests/test_core.py::TestSessions::test_create_session PASSED
cli_anything/agentscope/tests/test_core.py::TestSessions::test_delete_session PASSED
cli_anything/agentscope/tests/test_core.py::TestSessions::test_list_sessions PASSED
cli_anything/agentscope/tests/test_core.py::TestSendMessage::test_send_message_basic PASSED
cli_anything/agentscope/tests/test_core.py::TestUploadFile::test_upload_file PASSED
cli_anything/agentscope/tests/test_core.py::TestKnowledge::test_list_docs PASSED
cli_anything/agentscope/tests/test_core.py::TestKnowledge::test_remove_knowledge PASSED
cli_anything/agentscope/tests/test_core.py::TestKnowledge::test_search_knowledge PASSED
cli_anything/agentscope/tests/test_core.py::TestSkillToolInfo::test_get_skill_info PASSED
cli_anything/agentscope/tests/test_core.py::TestSkillToolInfo::test_get_tool_info PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractText::test_extract_text_empty PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractText::test_extract_text_multiple PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractText::test_extract_text_no_text_events PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractText::test_extract_text_single PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractDebug::test_extract_debug PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractDebug::test_extract_debug_empty PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractMetrics::test_extract_metrics PASSED
cli_anything/agentscope/tests/test_core.py::TestChatExtractMetrics::test_extract_metrics_empty PASSED
cli_anything/agentscope/tests/test_core.py::TestProjectDelegates::test_project_info PASSED
cli_anything/agentscope/tests/test_core.py::TestProjectDelegates::test_project_status PASSED

27 passed in 0.77s
```

### E2E Tests (test_full_e2e.py)

```
cli_anything/agentscope/tests/test_full_e2e.py::TestServerStatus::test_status SKIPPED (server not running)
cli_anything/agentscope/tests/test_full_e2e.py::TestAgentOperations::test_get_agent SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestAgentOperations::test_list_agents SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestSessionLifecycle::test_create_list_delete SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestChatSend::test_send_text_batch SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestChatSend::test_send_text_streaming SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestChatSend::test_send_with_session SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestKnowledgeOps::test_knowledge_upload_search_remove SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestFileUpload::test_upload_file SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestCLISubprocess::test_agent_list_json SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestCLISubprocess::test_chat_send_json SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestCLISubprocess::test_help PASSED
cli_anything/agentscope/tests/test_full_e2e.py::TestCLISubprocess::test_server_status_json SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestCLISubprocess::test_session_lifecycle_json SKIPPED
cli_anything/agentscope/tests/test_full_e2e.py::TestWorkflowChatSession::test_full_session_workflow SKIPPED

1 passed, 14 skipped in 28.41s
```

E2E tests require a running AgentScope Demo server. Start with `mvn spring-boot:run`.

### Summary

| Category | Total | Passed | Skipped |
|----------|-------|--------|---------|
| Unit tests | 27 | 27 | 0 |
| E2E tests | 15 | 1 | 14 (server offline) |
| **Total** | **42** | **28** | **14** |

### Coverage Notes

- All HTTP backend functions have mock-based unit tests
- All chat event extraction functions have pure unit tests
- E2E tests cover full server workflow but require a running server
- CLI subprocess tests verify installed command works via `_resolve_cli()`
