"""Agent listing and info."""

from cli_anything.agentscope.utils.agentscope_backend import list_agents, get_agent, get_skill_info, get_tool_info


def list_all(base_url: str | None = None) -> list[dict]:
    return list_agents(base_url)


def info(agent_id: str, base_url: str | None = None) -> dict:
    return get_agent(agent_id, base_url)


def skill_info(skill_name: str, base_url: str | None = None) -> dict:
    return get_skill_info(skill_name, base_url)


def tool_info(tool_name: str, base_url: str | None = None) -> dict:
    return get_tool_info(tool_name, base_url)
