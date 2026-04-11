# Agent Configuration Layer Design

**Date:** 2026-04-11
**Status:** Approved

## Overview

Refactor the Agent creation system to introduce a configuration layer with AgentId-based routing. The frontend will dynamically display available agents and support viewing agent configurations (including mounted skills).

## Requirements

1. All agent configurations stored in a single `agents.json` file
2. AgentId uses namespace format (e.g., `chat.basic`, `task.docx-analyzer`)
3. Configuration includes complete agent settings (basic info + model parameters)
4. Skills and tools referenced by name, auto-discovered from classpath
5. Frontend supports read-only viewing of agent configurations

## Configuration File Structure

**Location:** `src/main/resources/config/agents.json`

```json
{
  "agents": [
    {
      "agentId": "chat.basic",
      "name": "Basic Chat",
      "description": "Simple conversation with AI assistant",
      "systemPrompt": "You are a helpful AI assistant. Be friendly and concise.",
      "modelName": "qwen-plus",
      "streaming": true,
      "enableThinking": true,
      "skills": [],
      "tools": []
    },
    {
      "agentId": "tool.calculator",
      "name": "Tool Calling",
      "description": "AI with time, calculator, weather tools",
      "systemPrompt": "You are a helpful AI assistant with access to various tools. Use the appropriate tools when needed to answer questions accurately. Always explain what you're doing when using tools.",
      "modelName": "qwen-plus",
      "streaming": true,
      "enableThinking": true,
      "skills": [],
      "tools": ["SimpleTools"]
    },
    {
      "agentId": "task.document-analysis",
      "name": "Task Agent",
      "description": "Document analysis with file parsing",
      "systemPrompt": "You are a document analysis assistant. When the user uploads a document, use the appropriate skill to parse it and then fulfill the user's request based on the extracted content. Support .docx, .pdf, and .xlsx file analysis.",
      "modelName": "qwen-plus",
      "streaming": true,
      "enableThinking": true,
      "skills": ["docx", "pdf", "xlsx"],
      "tools": []
    },
    {
      "agentId": "template.docx-editor",
      "name": "Template Editor",
      "description": "Word template variable replacement",
      "systemPrompt": "You are a Word document template editor. When users upload a .docx template, help them fill in variables. First parse the document to identify placeholders, then ask for values if needed, then replace them using edit_docx.",
      "modelName": "qwen-plus",
      "streaming": true,
      "enableThinking": true,
      "skills": ["docx-template"],
      "tools": []
    }
  ]
}
```

## Architecture

### Data Flow

```
agents.json → AgentConfigService → AgentFactory → AgentService → ChatController
                                                   ↓
                                            ReActAgent Instance Cache
```

### Class Structure

```
com.msxf.agentscope.config/
├── AgentConfig.java           # Configuration entity class
├── AgentsConfig.java          # Root configuration wrapper
├── AgentConfigService.java    # Config loading and query service
└── AgentFactory.java          # Agent creation factory

com.msxf.agentscope.service/
└── AgentService.java          # Refactored routing layer (simplified)

com.msxf.agentscope.controller/
└── ChatController.java        # Added agent listing APIs

com.msxf.agentscope.tool/
└── ToolRegistry.java          # Tool name to instance mapping
```

## Component Design

### 1. AgentConfig.java

Configuration entity class representing a single agent definition.

**Fields:**
- `agentId` (String) - Unique identifier in namespace format
- `name` (String) - Display name
- `description` (String) - Agent description
- `systemPrompt` (String) - System prompt for the agent
- `modelName` (String) - Model name (default: "qwen-plus")
- `streaming` (boolean) - Enable streaming (default: true)
- `enableThinking` (boolean) - Enable thinking mode (default: true)
- `skills` (List<String>) - List of skill names to load
- `tools` (List<String>) - List of tool class names to register

### 2. AgentsConfig.java

Root configuration class for JSON deserialization.

**Fields:**
- `agents` (List<AgentConfig>) - List of all agent configurations

### 3. AgentConfigService.java

Service responsible for loading and managing agent configurations.

**Responsibilities:**
- Load `agents.json` from classpath on startup
- Maintain a map of agentId → AgentConfig
- Provide query methods for agent configurations
- Validate configuration completeness

**Methods:**
- `AgentConfig getAgentConfig(String agentId)` - Get config by ID
- `List<AgentConfig> getAllAgents()` - Get all agent configs
- `boolean exists(String agentId)` - Check if agent exists
- `Optional<AgentConfig> findAgentConfig(String agentId)` - Safe lookup

**Initialization:**
```java
public AgentConfigService(@Value("classpath:config/agents.json") Resource configFile) {
    // Load and parse JSON
    // Build configMap for fast lookup
    // Validate required fields
}
```

### 4. AgentFactory.java

Factory class responsible for creating ReActAgent instances based on configuration.

**Responsibilities:**
- Create DashScopeChatModel from config
- Create SkillBox and load skills from ClasspathSkillRepository
- Create Toolkit and register tools from ToolRegistry
- Build and return ReActAgent instance

**Dependencies:**
- `String apiKey` - DashScope API key
- `AgentConfigService` - For accessing configurations
- `ToolRegistry` - For resolving tool instances by name

**Methods:**
- `ReActAgent createAgent(String agentId)` - Create agent by ID

**Creation Logic:**
1. Get AgentConfig from AgentConfigService
2. Build DashScopeChatModel with config parameters
3. Create ReActAgent.Builder with name and systemPrompt
4. Create Toolkit
5. Load skills via SkillBox if skills list is not empty
6. Register tools via ToolRegistry if tools list is not empty
7. Set memory to InMemoryMemory
8. Build and return ReActAgent

### 5. ToolRegistry.java

Registry for managing tool instances by name.

**Responsibilities:**
- Register tool instances with names
- Resolve tool instances by name
- Support lazy initialization

**Methods:**
- `void register(String name, Supplier<Tool> supplier)` - Register tool supplier
- `Tool getTool(String name)` - Get tool instance

**Pre-registered Tools:**
- `SimpleTools` → `new SimpleTools()`

### 6. AgentService.java (Refactored)

Simplified routing layer that delegates to AgentFactory.

**Changes:**
- Remove hardcoded agent creation logic
- Replace `agentType` parameter with `agentId`
- Use AgentFactory for agent creation
- Keep streaming logic unchanged

**Methods:**
- `ReActAgent getAgent(String agentId)` - Get or create agent by ID
- `void streamToEmitter(String agentId, ...)` - Stream using agentId

### 7. ChatController.java (Enhanced)

Add new endpoints for agent configuration access.

**New Endpoints:**
- `GET /api/agents` - Return list of all agent configurations
- `GET /api/agents/{agentId}` - Return single agent configuration

## Frontend Changes

### 1. Dynamic Agent Loading

Remove hardcoded agent definitions and load from API:

```javascript
const agents = {};

async function loadAgents() {
    const response = await fetch('/api/agents');
    const data = await response.json();
    data.forEach(agent => {
        agents[agent.agentId] = {
            name: agent.name,
            desc: agent.description,
            config: agent
        };
        renderAgentCard(agent);
    });
}
```

### 2. Agent Card Rendering

Dynamically render agent cards based on loaded config:

```javascript
function renderAgentCard(agent) {
    const card = document.createElement('div');
    card.className = 'agent-card';
    card.dataset.agentId = agent.agentId;
    card.onclick = () => selectAgent(agent.agentId);

    card.innerHTML = `
        <div class="agent-card-icon">${getIconForAgent(agent.agentId)}</div>
        <div class="agent-card-info">
            <div class="agent-card-name">${agent.name}</div>
            <div class="agent-card-desc">${agent.description}</div>
        </div>
    `;

    document.querySelector('.agent-list').appendChild(card);
}
```

### 3. Configuration Viewer

Add read-only configuration view:

```javascript
async function showAgentConfig(agentId) {
    const response = await fetch(`/api/agents/${agentId}`);
    const config = await response.json();

    // Display configuration in a modal or side panel
    const configPanel = document.getElementById('configPanel');
    configPanel.innerHTML = `
        <h3>${config.name}</h3>
        <div class="config-field">
            <label>Agent ID:</label>
            <span>${config.agentIdId}</span>
        </div>
        <div class="config-field">
            <label>Model:</label>
            <span>${config.modelName}</span>
        </div>
        <div class="config-field">
            <label>System Prompt:</label>
            <pre>${config.systemPrompt}</pre>
        </div>
        <div class="config-field">
            <label>Skills:</label>
            <span>${config.skills.join(', ') || 'None'}</span>
        </div>
        <div class="config-field">
            <label>Tools:</label>
            <span>${config.tools.join(', ') || 'None'}</span>
        </div>
    `;
}
```

## Migration Plan

### Phase 1: Backend Infrastructure
1. Create configuration classes (AgentConfig, AgentsConfig)
2. Create ToolRegistry with pre-registered tools
3. Create AgentConfigService with JSON loading
4. Create AgentFactory with agent creation logic
5. Write unit tests for configuration parsing

### Phase 2: AgentService Refactor
1. Update AgentService to use AgentFactory
2. Replace agentType with agentId in method signatures
3. Update existing streaming logic
4. Test with existing agent types

### Phase 3: API Endpoints
1. Add GET /api/agents endpoint
2. Add GET /api/agents/{agentId} endpoint
3. Add error handling for invalid agentIds
4. Test API endpoints

### Phase 4: Frontend Integration
1. Implement dynamic agent loading
2. Update selectAgent() to use agentId
3. Add configuration viewer UI
4. Test full user flow

### Phase 5: Cleanup
1. Remove hardcoded agent creation from AgentService
2. Update documentation
3. Commit changes

## Error Handling

### Configuration Errors
- Invalid JSON: Log error and fail startup
- Missing required fields: Log warning, skip agent
- Duplicate agentIds: Log warning, use first occurrence
- Invalid skill/tool names: Log error, skip agent

### Runtime Errors
- Agent not found: Return 404 with detailed message
- Tool not registered: Log error, return 500
- Agent creation failed: Log error, return 500

## Testing

### Unit Tests
- AgentConfig JSON parsing
- AgentConfigService query methods
- ToolRegistry registration and lookup
- AgentFactory agent creation with mock dependencies

### Integration Tests
- GET /api/agents returns all configurations
- GET /api/agents/{agentId} returns specific config
- AgentService.getAgent() creates and caches agents
- Streaming works with configured agents

## Future Enhancements

- Hot-reload configuration without restart
- Configuration validation schema
- Environment-specific configuration overrides
- Admin UI for configuration management (not in scope)
