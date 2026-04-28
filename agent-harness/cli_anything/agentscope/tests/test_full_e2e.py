"""E2E tests for cli-anything-agentscope.

Requires a running AgentScope Demo server at http://localhost:8080.
Start with: mvn spring-boot:run
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import unittest

BASE_URL = os.environ.get("AGENTSCOPE_BASE_URL", "http://localhost:8080")


def _resolve_cli(name):
    """Resolve installed CLI command; falls back to python -m for dev."""
    force = os.environ.get("CLI_ANYTHING_FORCE_INSTALLED", "").strip() == "1"
    path = shutil.which(name)
    if path:
        print(f"[_resolve_cli] Using installed command: {path}")
        return [path]
    if force:
        raise RuntimeError(f"{name} not found in PATH. Install with: pip install -e .")
    module = name.replace("cli-anything-", "cli_anything.") + "." + name.split("-")[-1] + "_cli"
    print(f"[_resolve_cli] Falling back to: {sys.executable} -m {module}")
    return [sys.executable, "-m", module]


def _server_available():
    """Check if the server is reachable."""
    try:
        import requests
        r = requests.get(f"{BASE_URL}/api/agents", timeout=3)
        return r.status_code == 200
    except Exception:
        return False


@unittest.skipUnless(_server_available(), "AgentScope server not running")
class TestServerStatus(unittest.TestCase):
    def test_status(self):
        from cli_anything.agentscope.utils.agentscope_backend import server_status
        result = server_status(BASE_URL)
        self.assertEqual(result["status"], "ok")
        self.assertGreater(result["agent_count"], 0)
        print(f"\n  Server: {result['url']} ({result['agent_count']} agents)")


@unittest.skipUnless(_server_available(), "AgentScope server not running")
class TestAgentOperations(unittest.TestCase):
    def test_list_agents(self):
        from cli_anything.agentscope.utils.agentscope_backend import list_agents
        agents = list_agents(BASE_URL)
        self.assertIsInstance(agents, list)
        self.assertGreater(len(agents), 0)
        first = agents[0]
        self.assertIn("agentId", first)
        self.assertIn("name", first)
        print(f"\n  Agents: {len(agents)}")

    def test_get_agent(self):
        from cli_anything.agentscope.utils.agentscope_backend import get_agent
        a = get_agent("chat-basic", BASE_URL)
        self.assertEqual(a["agentId"], "chat-basic")
        self.assertIn("modelName", a)
        print(f"\n  Agent: {a['name']} (model: {a['modelName']})")


@unittest.skipUnless(_server_available(), "AgentScope server not running")
class TestSessionLifecycle(unittest.TestCase):
    def test_create_list_delete(self):
        from cli_anything.agentscope.utils.agentscope_backend import (
            create_session, list_sessions, delete_session,
        )
        created = create_session("chat-basic", BASE_URL)
        sid = created["sessionId"]
        self.assertTrue(sid)
        print(f"\n  Created session: {sid}")

        sessions = list_sessions(BASE_URL)
        ids = [s["sessionId"] for s in sessions]
        self.assertIn(sid, ids)

        result = delete_session(sid, BASE_URL)
        self.assertTrue(result.get("deleted", False))
        print(f"  Deleted session: {sid}")


@unittest.skipUnless(_server_available(), "AgentScope server not running")
class TestChatSend(unittest.TestCase):
    def test_send_text_batch(self):
        from cli_anything.agentscope.utils.agentscope_backend import send_message
        from cli_anything.agentscope.core.chat import extract_text
        events = send_message("Say hello in one word.", agent_id="chat-basic", base_url=BASE_URL)
        text = extract_text(events)
        self.assertTrue(len(text) > 0)
        print(f"\n  Response: {text[:100]}")

    def test_send_text_streaming(self):
        collected = []

        def callback(event):
            if event.get("type") == "text":
                collected.append(event.get("content", ""))

        from cli_anything.agentscope.utils.agentscope_backend import send_message
        send_message(
            "Say hi.", agent_id="chat-basic", base_url=BASE_URL, callback=callback,
        )
        text = "".join(collected)
        self.assertTrue(len(text) > 0)
        print(f"\n  Streamed: {text[:100]}")

    def test_send_with_session(self):
        from cli_anything.agentscope.utils.agentscope_backend import (
            send_message, create_session, delete_session,
        )
        from cli_anything.agentscope.core.chat import extract_text

        sess = create_session("chat-basic", BASE_URL)
        sid = sess["sessionId"]
        try:
            events = send_message(
                "What is 2+2?", agent_id="chat-basic", session_id=sid, base_url=BASE_URL,
            )
            text = extract_text(events)
            self.assertTrue(len(text) > 0)
            print(f"\n  Session chat: {text[:100]}")
        finally:
            delete_session(sid, BASE_URL)


@unittest.skipUnless(_server_available(), "AgentScope server not running")
class TestKnowledgeOps(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp_dir = tempfile.mkdtemp()
        cls.test_doc = os.path.join(cls.tmp_dir, "test_doc.txt")
        with open(cls.test_doc, "w") as f:
            f.write("Python is a programming language. Python was created by Guido van Rossum. "
                     "Python supports multiple programming paradigms.")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp_dir, ignore_errors=True)

    def test_knowledge_upload_search_remove(self):
        from cli_anything.agentscope.utils.agentscope_backend import (
            upload_knowledge, list_knowledge_docs, search_knowledge, remove_knowledge,
        )
        result = upload_knowledge(self.test_doc, BASE_URL)
        self.assertEqual(result.get("status"), "indexed")
        print(f"\n  Uploaded: {result.get('fileName')}")

        docs = list_knowledge_docs(BASE_URL)
        self.assertIn("test_doc.txt", docs)

        search_result = search_knowledge("Guido van Rossum", limit=3, base_url=BASE_URL)
        self.assertGreater(search_result.get("count", 0), 0)
        print(f"  Search results: {search_result.get('count')}")

        remove_result = remove_knowledge("test_doc.txt", BASE_URL)
        self.assertTrue(remove_result.get("removed", False))
        print("  Removed: test_doc.txt")


@unittest.skipUnless(_server_available(), "AgentScope server not running")
class TestFileUpload(unittest.TestCase):
    def test_upload_file(self):
        from cli_anything.agentscope.utils.agentscope_backend import upload_file
        with tempfile.NamedTemporaryFile(suffix=".txt", delete=False, mode="w") as f:
            f.write("test upload content")
            f.flush()
            path = f.name
        try:
            result = upload_file(path, BASE_URL)
            self.assertIn("fileId", result)
            self.assertEqual(result.get("fileType"), "document")
            print(f"\n  Uploaded: {result['fileName']} (type: {result['fileType']})")
        finally:
            os.unlink(path)


class TestCLISubprocess(unittest.TestCase):
    CLI_BASE = _resolve_cli("cli-anything-agentscope")

    def _run(self, args, check=True):
        env = os.environ.copy()
        env["AGENTSCOPE_BASE_URL"] = BASE_URL
        return subprocess.run(
            self.CLI_BASE + args,
            capture_output=True, text=True, check=check, env=env,
        )

    def test_help(self):
        result = self._run(["--help"])
        self.assertEqual(result.returncode, 0)
        self.assertIn("cli-anything-agentscope", result.stdout)

    def test_server_status_json(self):
        if not _server_available():
            self.skipTest("Server not running")
        result = self._run(["--json", "server", "status"])
        self.assertEqual(result.returncode, 0)
        data = json.loads(result.stdout)
        self.assertEqual(data["status"], "ok")

    def test_agent_list_json(self):
        if not _server_available():
            self.skipTest("Server not running")
        result = self._run(["--json", "agent", "list"])
        self.assertEqual(result.returncode, 0)
        data = json.loads(result.stdout)
        self.assertIsInstance(data, list)
        self.assertGreater(len(data), 0)

    def test_session_lifecycle_json(self):
        if not _server_available():
            self.skipTest("Server not running")
        result = self._run(["--json", "session", "create", "-a", "chat-basic"])
        self.assertEqual(result.returncode, 0)
        data = json.loads(result.stdout)
        sid = data["sessionId"]
        print(f"\n  CLI session: {sid}")

        result = self._run(["--json", "session", "delete", sid])
        self.assertEqual(result.returncode, 0)

    def test_chat_send_json(self):
        if not _server_available():
            self.skipTest("Server not running")
        result = self._run(["--json", "chat", "send", "--no-stream", "Say OK"])
        self.assertEqual(result.returncode, 0)
        data = json.loads(result.stdout)
        self.assertIn("text", data)
        print(f"\n  CLI chat: {data['text'][:80]}")


@unittest.skipUnless(_server_available(), "AgentScope server not running")
class TestWorkflowChatSession(unittest.TestCase):
    """Workflow: complete chat session lifecycle."""

    def test_full_session_workflow(self):
        from cli_anything.agentscope.utils.agentscope_backend import (
            create_session, send_message, list_sessions, delete_session,
        )
        from cli_anything.agentscope.core.chat import extract_text

        sess = create_session("chat-basic", BASE_URL)
        sid = sess["sessionId"]
        print(f"\n  Workflow session: {sid}")

        try:
            events1 = send_message("Remember the number 42.", agent_id="chat-basic", session_id=sid, base_url=BASE_URL)
            text1 = extract_text(events1)
            self.assertTrue(len(text1) > 0)

            events2 = send_message("What number did I ask you to remember?", agent_id="chat-basic", session_id=sid, base_url=BASE_URL)
            text2 = extract_text(events2)
            self.assertTrue(len(text2) > 0)
            print(f"  Follow-up response: {text2[:100]}")

            sessions = list_sessions(BASE_URL)
            target = [s for s in sessions if s["sessionId"] == sid]
            self.assertTrue(len(target) > 0)
            self.assertGreater(target[0]["messageCount"], 0)
        finally:
            delete_session(sid, BASE_URL)
            print(f"  Cleaned up session: {sid}")


if __name__ == "__main__":
    unittest.main()
