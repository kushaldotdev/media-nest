# AgentMemory Guide: Project-Local Memory

This project uses **agentmemory** for persistent across-session context. Memory is stored project-locally in the `.agentmemory/` directory.

---

## 🚀 How to Start the Server

```bash
$env:PATH += ";C:\Users\Kushal\AppData\Local\Programs\agentmemory"; agentmemory serve --path D:/dev/media-nest/.agentmemory --non-interactive
```

The server must be running before agentmemory can work. Press `Ctrl+C` to stop.

## 📊 Dashboard (UI)

Once server is running: **[http://localhost:3113](http://localhost:3113)**

---

## 🧠 How Memory Works (OpenCode Plugin)

OpenCode has **22 hooks** via the `agentmemory-capture.ts` plugin installed at:

- Global: `~/.config/opencode/plugins/agentmemory-capture.ts`
- Project: `.opencode/opencode.jsonc` (plugin registered)

### Automatic Capture (Runs Without Prompting)

| Event | What's Saved |
|---|---|
| Session start/end | Full session metadata |
| File edits | File paths + diff stats |
| Tool executions | Tool name, input, output, duration |
| Errors & retries | Failure context |
| User prompts | Full prompt text |
| Assistant responses | Model, tokens, cost, finish reason |
| Permission prompts | What was requested + response |
| Task/todo updates | Progress tracking |
| Commands executed | Command name + args |

### Automatic Context Injection

Before every LLM call, the plugin injects:
1. **Memory instructions** (teaches the agent how to use memory tools)
2. **Past context** from `/agentmemory/context` (relevant observations from past sessions)
3. **File enrichment** (`/agentmemory/enrich`) for files touched this session

### Slash Commands

- `/recall <query>` — Search past observations
- `/remember <text>` — Save an insight

### MCP Tools (Always Available)

`agentmemory_memory_save`, `agentmemory_memory_recall`, `agentmemory_memory_smart_search`, `agentmemory_memory_sessions`, `agentmemory_memory_audit`, `agentmemory_memory_export`, `agentmemory_memory_governance_delete`

---

## 🤖 Configured Agents

| Agent | Config | Connection |
|---|---|---|
| **OpenCode** | `.opencode/opencode.jsonc` | MCP + Plugin (22 hooks) |
| **Codex CLI** | `.codex/config.toml` | MCP |
| **Anti-Gravity** | `.gemini/config/mcp_config.json` | MCP |
| **pi** | `.pi/mcp_config.json` | MCP |

---

## 🛠️ Configuration

- **LLM Provider:** NVIDIA API (`z-ai/glm-5.1`)
- **Base URL:** `https://integrate.api.nvidia.com/v1`
- **Embeddings:** Local (Xenova)
- **Store Path:** `D:\dev\media-nest\.agentmemory`

## 🛠️ CLI Tools (Manual)

- `agentmemory status --path ./.agentmemory`
- `agentmemory search "query" --path ./.agentmemory`
- `agentmemory sessions --path ./.agentmemory`
