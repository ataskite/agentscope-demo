"""Server connection and health status."""

from cli_anything.agentscope.utils.agentscope_backend import server_status, get_base_url


def status(base_url: str | None = None) -> dict:
    return server_status(base_url)


def info(base_url: str | None = None) -> dict:
    st = server_status(base_url)
    st["base_url"] = base_url or get_base_url()
    return st
