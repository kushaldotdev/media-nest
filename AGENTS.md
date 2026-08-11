# Codebase Knowledge Graph (codebase-memory-mcp)

This project uses codebase-memory-mcp to maintain a knowledge graph of the codebase.
ALWAYS prefer MCP graph tools over grep/glob/file-search for code discovery.

## Priority Order

1. `search_graph` — find functions, classes, routes, variables by pattern
2. `trace_path` — trace who calls a function or what it calls
3. `get_code_snippet` — read specific function/class source code
4. `query_graph` — run Cypher queries for complex patterns
5. `get_architecture` — high-level project summary

## When to fall back to grep/glob

- Searching for string literals, error messages, config values
- Searching non-code files (Dockerfiles, shell scripts, configs)
- When MCP tools return insufficient results

## Examples

- Find a handler: `search_graph(name_pattern=".*OrderHandler.*")`
- Who calls it: `trace_path(function_name="OrderHandler", direction="inbound")`
- Read source: `get_code_snippet(qualified_name="pkg/orders.OrderHandler")`

## Build & Run Commands

Windows environment does not have standard `JAVA_HOME` in path. Use bundled build scripts:

- **Build Debug**: `& "D:\dev\media-nest\build-debug.bat"` (or `build-debug.bat clean` for a clean build)
- **Build Release**: `& "D:\dev\media-nest\build-release.bat"` (or `build-release.bat clean` for a clean build)

These scripts set `JAVA_HOME` to Android Studio's JBR (`C:\Program Files\Android\Android Studio\jbr`) and execute `gradlew.bat`.
Do not run `./gradlew` or `gradlew` directly unless `JAVA_HOME` is manually configured.

### Running the build from WSL

From a WSL bash shell, invoke the Windows `.bat` script through `cmd.exe` (backslashes must be doubled inside double quotes):

```bash
cd /mnt/d/dev/media-nest && /mnt/c/Windows/System32/cmd.exe /c "D:\\dev\\media-nest\\build-debug.bat"
```

or with single quotes (no escaping needed):

```bash
cmd.exe /c 'D:\dev\media-nest\build-debug.bat'
```

Pass `clean` as the first argument for a clean build: `cmd.exe /c 'D:\dev\media-nest\build-debug.bat clean'`.
`powershell.exe` is usually not on the WSL PATH; `cmd.exe` is at `/mnt/c/Windows/System32/cmd.exe` (add that dir to PATH or use the full path).

### Build logs

Both build scripts write all Gradle output to a **single** log file: `D:\dev\media-nest\build.log` (overwritten on every run, regardless of debug/release).

On failure the script echoes the full log to the terminal, prints the exit code, and exits non-zero. The full log is always saved to `build.log` (also echoed to the terminal on every run).
When a build fails, **read the log file** to diagnose the actual error instead of relying on the truncated console tail — from WSL: `tail -n 100 D:/dev/media-nest/build.log` or `grep -n "error:\|e: \|FAILED" D:/dev/media-nest/build.log`.
Note: `build.log` is written to the repo root and is git-ignored (see `.gitignore`).
