"""Chat message sending and SSE event processing."""

from cli_anything.agentscope.utils.agentscope_backend import (
    send_message, send_message_streaming,
)


def send(
    message: str,
    agent_id: str = "chat-basic",
    session_id: str | None = None,
    file_path: str | None = None,
    file_name: str | None = None,
    images: list[dict] | None = None,
    audio: dict | None = None,
    base_url: str | None = None,
    streaming: bool = False,
) -> list[dict] | None:
    if streaming:
        return send_message_streaming(
            message=message,
            agent_id=agent_id,
            session_id=session_id,
            file_path=file_path,
            file_name=file_name,
            images=images,
            audio=audio,
            base_url=base_url,
        )
    return send_message(
        message=message,
        agent_id=agent_id,
        session_id=session_id,
        file_path=file_path,
        file_name=file_name,
        images=images,
        audio=audio,
        base_url=base_url,
    )


def extract_text(events: list[dict]) -> str:
    if not events:
        return ""
    return "".join(e.get("content", "") for e in events if e.get("type") == "text")


def extract_debug(events: list[dict]) -> list[dict]:
    debug_types = {
        "agent_start", "agent_end",
        "llm_start", "llm_end", "thinking", "reasoning_text",
        "tool_start", "tool_end",
        "pipeline_start", "pipeline_step_start", "pipeline_step_end", "pipeline_end",
        "routing_decision", "routing_start", "routing_end",
        "handoff_start", "handoff_complete", "handoff_error",
    }
    return [e for e in events if e.get("type") in debug_types]


def extract_metrics(events: list[dict]) -> dict:
    llm_end_events = [e for e in events if e.get("type") == "llm_end"]
    tool_end_events = [e for e in events if e.get("type") == "tool_end"]
    agent_end_events = [e for e in events if e.get("type") == "agent_end"]

    total_input_tokens = sum(e.get("content", {}).get("inputTokens", 0) if isinstance(e.get("content"), dict) else 0 for e in llm_end_events)
    total_output_tokens = sum(e.get("content", {}).get("outputTokens", 0) if isinstance(e.get("content"), dict) else 0 for e in llm_end_events)

    return {
        "llm_calls": len(llm_end_events),
        "tool_calls": len(tool_end_events),
        "input_tokens": total_input_tokens,
        "output_tokens": total_output_tokens,
        "agent_end": agent_end_events[0].get("content") if agent_end_events else None,
    }
