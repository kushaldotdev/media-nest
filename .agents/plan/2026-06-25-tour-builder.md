# Implementation Plan

## System / Contract Summary
We need to generate a 5-15 step guided learning tour JSON for the `media-nest` codebase, detailing its architecture and key concepts. 
Input file: `d:\dev\media-nest\.understand-anything\tmp\ua-tour-input.json`
Output file: `d:\dev\media-nest\.understand-anything/intermediate/tour.json`

## Phase Order
- **Phase 1: Graph Topology Script & Execution**: Write and execute a Node.js script to run graph topology metrics and output results to a scratch file.
- **Phase 2: Tour Design & Write**: Read metrics, select entry points and core flows, design the pedagogical steps, verify all referenced node IDs, and write the output JSON.

## Steps

### Phase 1: Graph Topology Script & Execution
- **Step 1.1: Write topology analysis script**
  - **What**: Write `analyze-graph.js` in `<appDataDir>/brain/<conversation-id>/scratch/`.
  - **Where**: `C:\Users\Kushal\.gemini\antigravity\brain\0bf260df-6f7f-4d03-8b6e-94edc451ae5f/scratch/analyze-graph.js`
  - **How**: Read the input JSON, compute:
    1. Fan-in per node (incoming edges count)
    2. Fan-out per node (outgoing edges count)
    3. Entry point candidate scoring (weighted by type and specific key files)
    4. BFS levels from entry points (depth from entries)
    5. Non-code inventory (by grouping nodes by type)
    6. Tightly coupled clusters (Strongly Connected Connected Components (SCC) or mutual dependency pairs)
    7. Layer listing
    8. Lookup index
    Write the computed metrics JSON to a temp result file in the scratch directory.
  - **Why**: Graph topological metrics reveal the most structurally significant files to guide tour creation.
  - **Edge cases**: Malformed JSON input, node IDs with missing properties.
  - **Pitfalls**: Infinite cycles during BFS or SCC search. Use sets of visited nodes.
  - **Validation**: Node script execution completes with exit code 0.

- **Step 1.2: Execute script**
  - **What**: Run the script with `node`.
  - **Where**: Terminal execution using `run_command`.
  - **How**: Propose `node <scratch-dir>/analyze-graph.js <input-path> <output-path>`.
  - **Why**: To produce the metrics needed for Phase 2.
  - **Validation**: Verify exit code 0 and presence/validity of the output metrics JSON.

### Phase 2: Tour Design & Write
- **Step 2.1: Design pedagogical steps**
  - **What**: Analyze output metrics to select 5-15 nodes that represent project overview, build configuration, entry points, core flows, and deployment.
  - **Where**: Memory / plan elaboration.
  - **How**: Construct the tour JSON structure with title, description, nodeIds, order, and optional languageLesson.
  - **Why**: To ensure logical, structured pedagogical flow.
  - **Edge cases**: Referencing node IDs that are not present in the input file.
  - **Validation**: Compare every nodeId referenced in the tour against the input file's node IDs.

- **Step 2.2: Write tour.json**
  - **What**: Write the designed steps to the final path.
  - **Where**: `d:\dev\media-nest\.understand-anything/intermediate/tour.json`
  - **How**: Write the JSON file.
  - **Why**: Fulfill the core request.
  - **Validation**: Parse the final file to verify JSON syntax.

## Beginner Implementation Guide
Use simple, direct logic in the JS script (no complex libraries, just plain JS using fs).

## Final Verification Checklist
- Run a verify script to make sure every nodeId in `tour.json` exists in `ua-tour-input.json`.
- Verify file exists at target destination.

## Stop Conditions
- If the node structure in the input JSON deviates significantly from expectation.
- If target directory cannot be written.

# Plan Gap Check
## Gaps / risks found
- The script must correctly find entries even if they have slightly different naming (e.g. including or excluding `file:` prefix).
- Node ID validation must be strictly checked.

## Changes made
- Added explicit node ID validation step to the final verification.

## Remaining edge cases
- Cyclic dependencies in SCC or BFS. (Handled via visited sets).

## Remaining pitfalls
- Node IDs format (e.g. `file:app/src/main/...`). The script must map edges and nodes using exact IDs.

## Open questions
- None.

