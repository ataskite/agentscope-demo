from setuptools import setup, find_namespace_packages

setup(
    name="cli-anything-agentscope",
    version="1.0.0",
    description="CLI harness for AgentScope Demo — agent chat, session, and knowledge management",
    packages=find_namespace_packages(include=["cli_anything.*"]),
    package_data={
        "cli_anything.agentscope": ["skills/*.md"],
    },
    python_requires=">=3.10",
    install_requires=[
        "click>=8.0",
        "requests>=2.28",
        "prompt_toolkit>=3.0",
    ],
    extras_require={
        "dev": ["pytest>=7.0"],
    },
    entry_points={
        "console_scripts": [
            "cli-anything-agentscope=cli_anything.agentscope.agentscope_cli:cli",
        ],
    },
)
