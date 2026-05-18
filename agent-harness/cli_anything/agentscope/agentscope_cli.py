"""cli-anything-agentscope — CLI harness for AgentScope Demo server.

Provides command-line and REPL access to agent chat, session management,
knowledge base, and file upload/download operations.
"""

import json
import os
import sys

import click

from cli_anything.agentscope.utils.repl_skin import ReplSkin

_repl_mode = False
_current_session_id = None
_current_agent_id = "chat-basic"
_skin = ReplSkin("agentscope", version="1.0.0")


def _base_url():
    return os.environ.get("AGENTSCOPE_BASE_URL", "http://localhost:8080").rstrip("/")


def _json_out(data):
    click.echo(json.dumps(data, ensure_ascii=False, indent=2))


def _handle_error(e):
    msg = str(e)
    if "Cannot connect" in msg:
        _skin.error(f"Server unreachable: {_base_url()}")
        _skin.hint("Start with: mvn spring-boot:run")
    else:
        _skin.error(msg)
    if not _repl_mode:
        sys.exit(1)


@click.group(invoke_without_command=True)
@click.option("--json", "use_json", is_flag=True, help="Output as JSON")
@click.option("--server", "server_url", type=str, default=None,
              help="AgentScope server URL (default: http://localhost:8080)")
@click.pass_context
def cli(ctx, use_json, server_url):
    """cli-anything-agentscope — AgentScope Demo CLI harness."""
    ctx.ensure_object(dict)
    ctx.obj["json"] = use_json
    if server_url:
        os.environ["AGENTSCOPE_BASE_URL"] = server_url
    if ctx.invoked_subcommand is None:
        ctx.invoke(repl)


# ── Server commands ────────────────────────────────────────────────────

@cli.group("server")
@click.pass_context
def server(ctx):
    """Server connection and status."""
    pass


@server.command("status")
@click.pass_context
def server_status(ctx):
    """Check server health and connectivity."""
    from cli_anything.agentscope.core import project
    try:
        result = project.status()
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        if result["status"] == "ok":
            _skin.success(f"Server online at {result['url']} ({result['agent_count']} agents)")
        else:
            _skin.error(f"Server {result['status']} at {result['url']}")


@server.command("info")
@click.pass_context
def server_info(ctx):
    """Show detailed server information."""
    from cli_anything.agentscope.core import project
    try:
        result = project.info()
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.section("Server Info")
        _skin.status("URL", result.get("base_url", ""))
        _skin.status("Status", result.get("status", "unknown"))
        _skin.status("Agents", str(result.get("agent_count", 0)))


# ── Agent commands ─────────────────────────────────────────────────────

@cli.group("agent")
@click.pass_context
def agent(ctx):
    """Agent listing and configuration."""
    pass


@agent.command("list")
@click.pass_context
def agent_list(ctx):
    """List all available agents."""
    from cli_anything.agentscope.core import agent as agent_mod
    try:
        agents = agent_mod.list_all()
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(agents)
    else:
        if not agents:
            _skin.info("No agents found.")
            return
        headers = ["ID", "Name", "Type", "Modality"]
        rows = []
        for a in agents:
            rows.append([
                a.get("agentId", ""),
                a.get("name", ""),
                a.get("type", "SINGLE"),
                a.get("modality", "text"),
            ])
        _skin.table(headers, rows)


@agent.command("info")
@click.argument("agent_id")
@click.pass_context
def agent_info(ctx, agent_id):
    """Show detailed agent configuration."""
    from cli_anything.agentscope.core import agent as agent_mod
    try:
        a = agent_mod.info(agent_id)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(a)
    else:
        _skin.section(f"Agent: {a.get('name', agent_id)}")
        _skin.status("ID", a.get("agentId", ""))
        _skin.status("Type", a.get("type", "SINGLE"))
        _skin.status("Model", a.get("modelName", ""))
        _skin.status("Modality", a.get("modality", "text"))
        _skin.status("Streaming", str(a.get("streaming", True)))
        _skin.status("Thinking", str(a.get("enableThinking", True)))
        _skin.status("RAG", str(a.get("ragEnabled", False)))
        _skin.status("Description", a.get("description", ""))
        if a.get("skills"):
            _skin.status("Skills", ", ".join(a["skills"]))
        if a.get("userTools"):
            _skin.status("User Tools", ", ".join(a["userTools"]))
        if a.get("systemTools"):
            _skin.status("System Tools", ", ".join(a["systemTools"]))


@agent.command("messages")
@click.argument("agent_id")
@click.pass_context
def agent_messages(ctx, agent_id):
    """List chat history for an agent."""
    from cli_anything.agentscope.core import agent as agent_mod
    try:
        msgs = agent_mod.messages(agent_id)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(msgs)
    else:
        if not msgs:
            _skin.info("No messages found.")
            return
        for m in msgs:
            role = m.get("role", "unknown")
            content = m.get("content", "")
            preview = content[:100] + "..." if len(content) > 100 else content
            _skin.status(role, preview)


@agent.command("sample-prompt")
@click.argument("agent_id")
@click.argument("index", type=int)
@click.pass_context
def agent_sample_prompt(ctx, agent_id, index):
    """Get a sample prompt for an agent by index."""
    from cli_anything.agentscope.core import agent as agent_mod
    try:
        result = agent_mod.sample_prompt(agent_id, index)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.section(f"Sample Prompt #{index}")
        _skin.status("Agent", result.get("agentId", agent_id))
        click.echo(result.get("prompt", ""))
        if result.get("expectedBehavior"):
            _skin.status("Expected", result["expectedBehavior"])


@agent.command("skill-info")
@click.argument("skill_name")
@click.pass_context
def agent_skill_info(ctx, skill_name):
    """Show skill details."""
    from cli_anything.agentscope.core import agent as agent_mod
    try:
        result = agent_mod.skill_info(skill_name)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.section(f"Skill: {skill_name}")
        for k, v in result.items():
            _skin.status(k, str(v)[:200])


@agent.command("tool-info")
@click.argument("tool_name")
@click.pass_context
def agent_tool_info(ctx, tool_name):
    """Show tool details."""
    from cli_anything.agentscope.core import agent as agent_mod
    try:
        result = agent_mod.tool_info(tool_name)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.section(f"Tool: {tool_name}")
        for k, v in result.items():
            _skin.status(k, str(v)[:200])


# ── Session commands ───────────────────────────────────────────────────

@cli.group("session")
@click.pass_context
def session(ctx):
    """Session management."""
    pass


@session.command("list")
@click.pass_context
def session_list(ctx):
    """List all sessions."""
    from cli_anything.agentscope.core import session as session_mod
    try:
        sessions = session_mod.list_all()
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(sessions)
    else:
        if not sessions:
            _skin.info("No sessions found.")
            return
        headers = ["ID", "Agent", "Messages", "Created"]
        rows = []
        for s in sessions:
            rows.append([
                s.get("sessionId", "")[:12] + "...",
                s.get("agentName", ""),
                str(s.get("messageCount", 0)),
                s.get("createdAt", ""),
            ])
        _skin.table(headers, rows)


@session.command("create")
@click.option("--agent-id", "-a", default="chat-basic", help="Agent ID for the session")
@click.pass_context
def session_create(ctx, agent_id):
    """Create a new session."""
    from cli_anything.agentscope.core import session as session_mod
    try:
        result = session_mod.create(agent_id)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.success(f"Session created: {result.get('sessionId', '')}")


@session.command("delete")
@click.argument("session_id")
@click.pass_context
def session_delete(ctx, session_id):
    """Delete a session."""
    from cli_anything.agentscope.core import session as session_mod
    try:
        result = session_mod.remove(session_id)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.success(f"Session deleted: {session_id}")


@session.command("use")
@click.argument("session_id")
@click.pass_context
def session_use(ctx, session_id):
    """Set active session for REPL chat."""
    global _current_session_id
    _current_session_id = session_id
    if ctx.obj["json"]:
        _json_out({"active_session": session_id})
    else:
        _skin.success(f"Active session: {session_id}")


# ── Chat commands ──────────────────────────────────────────────────────

@cli.group("chat")
@click.pass_context
def chat(ctx):
    """Send messages to agents."""
    pass


@chat.command("send")
@click.argument("message", nargs=-1, required=True)
@click.option("--agent-id", "-a", default=None, help="Agent ID (default: current or chat-basic)")
@click.option("--session-id", "-s", default=None, help="Session ID")
@click.option("--file", "file_path", default=None, help="File path to attach")
@click.option("--stream/--no-stream", default=True, help="Stream output in real time")
@click.pass_context
def chat_send(ctx, message, agent_id, session_id, file_path, stream):
    """Send a message to an agent."""
    from cli_anything.agentscope.core import chat as chat_mod
    msg = " ".join(message)
    aid = agent_id or _current_agent_id
    sid = session_id or _current_session_id
    try:
        if stream:
            events = chat_mod.send(
                message=msg, agent_id=aid, session_id=sid,
                file_path=file_path, streaming=True,
            )
        else:
            events = chat_mod.send(
                message=msg, agent_id=aid, session_id=sid,
                file_path=file_path, streaming=False,
            )
            text = chat_mod.extract_text(events)
            if ctx.obj["json"]:
                _json_out({"text": text, "events": events})
            else:
                if text:
                    click.echo(text)
    except Exception as e:
        _handle_error(e)


@chat.command("metrics")
@click.argument("message", nargs=-1, required=True)
@click.option("--agent-id", "-a", default=None, help="Agent ID")
@click.option("--session-id", "-s", default=None, help="Session ID")
@click.pass_context
def chat_metrics(ctx, message, agent_id, session_id):
    """Send a message and show debug metrics."""
    from cli_anything.agentscope.core import chat as chat_mod
    msg = " ".join(message)
    aid = agent_id or _current_agent_id
    sid = session_id or _current_session_id
    try:
        events = chat_mod.send(message=msg, agent_id=aid, session_id=sid, streaming=False)
        metrics = chat_mod.extract_metrics(events)
        debug = chat_mod.extract_debug(events)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out({"metrics": metrics, "debug_events": debug})
    else:
        _skin.section("Metrics")
        _skin.status("LLM Calls", str(metrics["llm_calls"]))
        _skin.status("Tool Calls", str(metrics["tool_calls"]))
        _skin.status("Input Tokens", str(metrics["input_tokens"]))
        _skin.status("Output Tokens", str(metrics["output_tokens"]))
        if metrics.get("agent_end"):
            ae = metrics["agent_end"]
            if isinstance(ae, dict):
                _skin.status("Duration", f"{ae.get('duration_ms', 0):.0f}ms")


@chat.command("approve")
@click.argument("approval_id")
@click.option("--reject", "approved", is_flag=True, default=False, help="Reject instead of approve")
@click.option("--reason", "-r", default=None, help="Reason for rejection")
@click.option("--session-id", "-s", default=None, help="Session ID")
@click.pass_context
def chat_approve(ctx, approval_id, approved, reason, session_id):
    """Approve or reject a pending HITL approval request."""
    from cli_anything.agentscope.core import chat as chat_mod
    sid = session_id or _current_session_id
    try:
        events = chat_mod.approve(
            approval_id=approval_id,
            approved=not approved,
            reason=reason,
            session_id=sid,
        )
        text = chat_mod.extract_text(events)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out({"text": text, "events": events})
    else:
        action = "rejected" if approved else "approved"
        _skin.success(f"Request {action}: {approval_id}")
        if text:
            click.echo(text)


# ── Knowledge commands ─────────────────────────────────────────────────

@cli.group("knowledge")
@click.pass_context
def knowledge(ctx):
    """Knowledge base management."""
    pass


@knowledge.command("list")
@click.pass_context
def knowledge_list(ctx):
    """List indexed documents."""
    from cli_anything.agentscope.core import knowledge as knowledge_mod
    try:
        docs = knowledge_mod.list_docs()
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(docs)
    else:
        if not docs:
            _skin.info("No documents indexed.")
            return
        for d in docs:
            _skin.info(d)


@knowledge.command("upload")
@click.argument("file_path")
@click.pass_context
def knowledge_upload(ctx, file_path):
    """Upload a document to the knowledge base."""
    from cli_anything.agentscope.core import knowledge as knowledge_mod
    try:
        result = knowledge_mod.upload(file_path)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.success(f"Indexed: {result.get('fileName', file_path)}")


@knowledge.command("remove")
@click.argument("file_name")
@click.pass_context
def knowledge_remove(ctx, file_name):
    """Remove a document from the knowledge base."""
    from cli_anything.agentscope.core import knowledge as knowledge_mod
    try:
        result = knowledge_mod.remove(file_name)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.success(f"Removed: {file_name}")


@knowledge.command("search")
@click.argument("query", nargs=-1, required=True)
@click.option("--limit", "-n", default=3, help="Max results")
@click.option("--threshold", "-t", default=0.5, type=float, help="Score threshold")
@click.pass_context
def knowledge_search(ctx, query, limit, threshold):
    """Search the knowledge base."""
    from cli_anything.agentscope.core import knowledge as knowledge_mod
    q = " ".join(query)
    try:
        result = knowledge_mod.search(q, limit, threshold)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.section(f"Search: {q}")
        _skin.status("Results", str(result.get("count", 0)))
        for i, r in enumerate(result.get("results", []), 1):
            _skin.status(f"  {i}", f"[{r.get('score', 'N/A')}] {r.get('content', '')[:120]}")


@knowledge.command("status")
@click.pass_context
def knowledge_status(ctx):
    """Show knowledge base indexing status."""
    from cli_anything.agentscope.core import knowledge as knowledge_mod
    try:
        result = knowledge_mod.status()
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.section("Knowledge Base Status")
        for k, v in result.items():
            _skin.status(k, str(v))


# ── Upload commands ────────────────────────────────────────────────────

@cli.group("upload")
@click.pass_context
def upload(ctx):
    """File upload and download."""
    pass


@upload.command("file")
@click.argument("file_path")
@click.pass_context
def upload_file_cmd(ctx, file_path):
    """Upload a file to the server."""
    from cli_anything.agentscope.core import upload as upload_mod
    try:
        result = upload_mod.upload(file_path)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out(result)
    else:
        _skin.success(f"Uploaded: {result.get('fileName', '')}")
        _skin.status("File ID", result.get("fileId", ""))
        _skin.status("Type", result.get("fileType", ""))
        _skin.status("Path", result.get("filePath", ""))


@upload.command("download")
@click.argument("file_id")
@click.option("--output", "-o", required=True, help="Output file path")
@click.pass_context
def upload_download(ctx, file_id, output):
    """Download a file from the server."""
    from cli_anything.agentscope.core import upload as upload_mod
    try:
        path = upload_mod.download(file_id, output)
    except Exception as e:
        _handle_error(e)
        return
    if ctx.obj["json"]:
        _json_out({"path": path})
    else:
        _skin.success(f"Downloaded: {path}")


# ── REPL ───────────────────────────────────────────────────────────────

@cli.command("repl")
@click.option("--agent-id", "-a", default="chat-basic", help="Default agent ID")
@click.pass_context
def repl(ctx, agent_id):
    """Interactive REPL mode."""
    global _repl_mode, _current_agent_id, _current_session_id
    _repl_mode = True
    _current_agent_id = agent_id

    _skin.print_banner()

    # Check server
    from cli_anything.agentscope.core import project
    try:
        st = project.status()
        if st["status"] != "ok":
            _skin.error(f"Server offline at {st['url']}")
            _skin.hint("Start with: mvn spring-boot:run")
            return
        _skin.success(f"Connected to {st['url']} ({st['agent_count']} agents)")
    except Exception as e:
        _skin.error(f"Cannot reach server: {e}")
        return

    pt_session = _skin.create_prompt_session()

    while True:
        try:
            ctx_info = f"agent={_current_agent_id}"
            if _current_session_id:
                ctx_info += f" session={_current_session_id[:8]}..."
            line = _skin.get_input(pt_session, context=ctx_info)
        except (EOFError, KeyboardInterrupt):
            _skin.print_goodbye()
            break

        if not line:
            continue

        parts = line.strip().split(None, 1)
        cmd = parts[0].lower()
        arg = parts[1] if len(parts) > 1 else ""

        if cmd in ("quit", "exit", "q"):
            _skin.print_goodbye()
            break
        elif cmd == "help":
            _skin.help({
                "help": "Show this help",
                "quit / exit": "Exit REPL",
                "agent list": "List all agents",
                "agent use <id>": "Switch active agent",
                "agent info <id>": "Show agent details",
                "agent messages <id>": "Show agent chat history",
                "agent skill-info <name>": "Show skill details",
                "agent tool-info <name>": "Show tool details",
                "session list": "List sessions",
                "session create [agent]": "Create new session",
                "session use <id>": "Set active session",
                "session delete <id>": "Delete a session",
                "chat <message>": "Send message (streaming)",
                "chat! <message>": "Send message (batch, with metrics)",
                "chat approve <id> [--reject]": "Approve/reject HITL request",
                "knowledge list": "List indexed docs",
                "knowledge upload <path>": "Upload doc to knowledge base",
                "knowledge search <query>": "Search knowledge base",
                "knowledge status": "Show indexing status",
                "upload <path>": "Upload file to server",
                "server status": "Check server health",
            })
        elif cmd == "agent":
            _repl_agent(arg)
        elif cmd == "session":
            _repl_session(arg)
        elif cmd in ("chat", "chat!"):
            if not arg:
                _skin.warning("Usage: chat <message>")
                continue
            stream = cmd == "chat"
            _repl_chat(arg, stream)
        elif cmd == "approve":
            if not arg:
                _skin.warning("Usage: approve <approval_id> [--reject] [--reason <text>]")
                continue
            _repl_approve(arg)
        elif cmd == "knowledge":
            _repl_knowledge(arg)
        elif cmd == "upload":
            if not arg:
                _skin.warning("Usage: upload <file_path>")
                continue
            _repl_upload(arg)
        elif cmd == "server":
            _repl_server(arg)
        else:
            # Default: treat as chat message
            _repl_chat(line, stream=True)


def _repl_agent(arg):
    from cli_anything.agentscope.core import agent as agent_mod
    parts = arg.split(None, 1)
    if not parts or parts[0] == "list":
        try:
            agents = agent_mod.list_all()
            headers = ["ID", "Name", "Type"]
            rows = [[a.get("agentId", ""), a.get("name", ""), a.get("type", "SINGLE")] for a in agents]
            _skin.table(headers, rows)
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "use" and len(parts) > 1:
        global _current_agent_id
        _current_agent_id = parts[1]
        _skin.success(f"Agent: {_current_agent_id}")
    elif parts[0] == "info" and len(parts) > 1:
        try:
            a = agent_mod.info(parts[1])
            _skin.section(f"Agent: {a.get('name', parts[1])}")
            _skin.status("ID", a.get("agentId", ""))
            _skin.status("Model", a.get("modelName", ""))
            _skin.status("Type", a.get("type", "SINGLE"))
            _skin.status("Modality", a.get("modality", "text"))
            _skin.status("Description", a.get("description", ""))
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "messages" and len(parts) > 1:
        try:
            msgs = agent_mod.messages(parts[1])
            if not msgs:
                _skin.info("No messages found.")
                return
            for m in msgs:
                role = m.get("role", "unknown")
                content = m.get("content", "")
                preview = content[:100] + "..." if len(content) > 100 else content
                _skin.status(role, preview)
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "skill-info" and len(parts) > 1:
        try:
            result = agent_mod.skill_info(parts[1])
            _skin.section(f"Skill: {parts[1]}")
            for k, v in result.items():
                _skin.status(k, str(v)[:200])
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "tool-info" and len(parts) > 1:
        try:
            result = agent_mod.tool_info(parts[1])
            _skin.section(f"Tool: {parts[1]}")
            for k, v in result.items():
                _skin.status(k, str(v)[:200])
        except Exception as e:
            _skin.error(str(e))
    else:
        _skin.warning("Usage: agent [list|use <id>|info <id>|messages <id>|skill-info <name>|tool-info <name>]")


def _repl_session(arg):
    global _current_session_id
    from cli_anything.agentscope.core import session as session_mod
    parts = arg.split(None, 1)
    if not parts or parts[0] == "list":
        try:
            sessions = session_mod.list_all()
            if not sessions:
                _skin.info("No sessions.")
                return
            headers = ["ID", "Agent", "Messages", "Created"]
            rows = [[s.get("sessionId", "")[:12], s.get("agentName", ""),
                     str(s.get("messageCount", 0)), s.get("createdAt", "")] for s in sessions]
            _skin.table(headers, rows)
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "create":
        aid = parts[1] if len(parts) > 1 else _current_agent_id
        try:
            result = session_mod.create(aid)
            _current_session_id = result.get("sessionId", "")
            _skin.success(f"Session: {_current_session_id}")
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "use" and len(parts) > 1:
        _current_session_id = parts[1]
        _skin.success(f"Active session: {_current_session_id}")
    elif parts[0] == "delete" and len(parts) > 1:
        try:
            session_mod.remove(parts[1])
            if _current_session_id == parts[1]:
                _current_session_id = None
            _skin.success(f"Deleted: {parts[1]}")
        except Exception as e:
            _skin.error(str(e))
    else:
        _skin.warning("Usage: session [list|create [agent]|use <id>|delete <id>]")


def _repl_chat(message, stream=True):
    from cli_anything.agentscope.core import chat as chat_mod
    try:
        if stream:
            chat_mod.send(
                message=message,
                agent_id=_current_agent_id,
                session_id=_current_session_id,
                streaming=True,
            )
        else:
            events = chat_mod.send(
                message=message,
                agent_id=_current_agent_id,
                session_id=_current_session_id,
                streaming=False,
            )
            text = chat_mod.extract_text(events)
            if text:
                click.echo(text)
            metrics = chat_mod.extract_metrics(events)
            _skin.section("Metrics")
            _skin.status("LLM Calls", str(metrics["llm_calls"]))
            _skin.status("Tool Calls", str(metrics["tool_calls"]))
            _skin.status("Tokens", f"{metrics['input_tokens']} in / {metrics['output_tokens']} out")
    except Exception as e:
        _skin.error(str(e))


def _repl_knowledge(arg):
    from cli_anything.agentscope.core import knowledge as knowledge_mod
    parts = arg.split(None, 1)
    if not parts or parts[0] == "list":
        try:
            docs = knowledge_mod.list_docs()
            if not docs:
                _skin.info("No documents indexed.")
                return
            for d in docs:
                _skin.info(d)
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "upload" and len(parts) > 1:
        try:
            result = knowledge_mod.upload(parts[1])
            _skin.success(f"Indexed: {result.get('fileName', '')}")
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "search" and len(parts) > 1:
        try:
            result = knowledge_mod.search(parts[1])
            _skin.status("Results", str(result.get("count", 0)))
            for i, r in enumerate(result.get("results", []), 1):
                _skin.status(f"  {i}", f"[{r.get('score', 'N/A')}]")
                click.echo(f"    {r.get('content', '')[:200]}")
        except Exception as e:
            _skin.error(str(e))
    elif parts[0] == "status":
        try:
            result = knowledge_mod.status()
            _skin.section("Knowledge Base Status")
            for k, v in result.items():
                _skin.status(k, str(v))
        except Exception as e:
            _skin.error(str(e))
    else:
        _skin.warning("Usage: knowledge [list|upload <path>|search <query>|status]")


def _repl_upload(arg):
    from cli_anything.agentscope.core import upload as upload_mod
    try:
        result = upload_mod.upload(arg)
        _skin.success(f"Uploaded: {result.get('fileName', '')} (type: {result.get('fileType', '')})")
        _skin.status("Path", result.get("filePath", ""))
    except Exception as e:
        _skin.error(str(e))


def _repl_approve(arg):
    from cli_anything.agentscope.core import chat as chat_mod
    parts = arg.split()
    if not parts:
        _skin.warning("Usage: approve <approval_id> [--reject]")
        return
    approval_id = parts[0]
    reject = "--reject" in parts
    try:
        events = chat_mod.approve(
            approval_id=approval_id,
            approved=not reject,
        )
        text = chat_mod.extract_text(events)
        action = "rejected" if reject else "approved"
        _skin.success(f"Request {action}: {approval_id}")
        if text:
            click.echo(text)
    except Exception as e:
        _skin.error(str(e))


def _repl_server(arg):
    from cli_anything.agentscope.core import project
    parts = arg.split()
    if not parts or parts[0] == "status":
        st = project.status()
        if st["status"] == "ok":
            _skin.success(f"Online ({st['agent_count']} agents)")
        else:
            _skin.error(f"Offline: {st.get('status', 'unknown')}")
    else:
        _skin.warning("Usage: server [status]")


if __name__ == "__main__":
    cli()
