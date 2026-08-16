# Codebase Memory (MCP)

This project uses `codebase-memory-mcp` to provide AI agents with a structural knowledge graph of the codebase. This enables features like call tracing, structural search, and architectural analysis.

## Features
- **Structural Search**: Find functions and classes by behavior/pattern, not just text.
- **Call Tracing**: See who calls a function or what a function depends on.
- **Architecture Analysis**: Get high-level overviews of modules and services.
- **Auto-indexing**: Keeps the graph updated via Git hooks on commit/checkout.

## Project Setup
The configuration is isolated to this project to keep global agent settings clean.
- **OpenCode**: Configured in `.opencode/opencode.jsonc`
- **Claude Code**: Configured in `.claude.json`
- **Antigravity**: Configured in `.gemini/`
- **Codex**: Configured in `.codex/`

## How it Works
1. **Indexing**: The repo is scanned and parsed into a graph database.
2. **Git Hooks**: Scripts in `.git/hooks/` trigger a `mode="fast"` re-index on every commit, pull, or branch switch.
3. **Graph Storage**: The data is stored in the user's home directory (`~/.codebase-memory/`) but scoped to this project's ID.

## User Interface (Visualizer)
The visualizer allows you to explore the knowledge graph in your browser.

### URL
**[http://localhost:9749](http://localhost:9749)**

### Starting Manually
If the UI is not running, you can start it manually from a terminal:
```powershell
codebase-memory-mcp visualize
```
The server will start and provide a link to the browser.

## Manual Indexing
If the graph feels stale, you can force a re-index via any agent:
- "re-index the repository"
- "index the repo in full mode"

Or via CLI:
```powershell
codebase-memory-mcp cli index_repository repo_path="." mode="fast" json
```

## Relevant Files
- `AGENTS.md`: Global instructions for agents on how to use the graph tools.
- `.opencode/opencode.jsonc`: MCP registration for OpenCode.
- `.claude.json`: MCP registration for Claude Code.
