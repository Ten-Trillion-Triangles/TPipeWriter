# Reasoning Pipe Tracing Investigation

## Problem Statement
Need to ensure that reasoning pipes' output is treated as `reasoningContent` in trace files instead of regular output.

## Investigation Plan
1. Find how reasoning pipes are implemented in the Pipe class
2. Identify where reasoning pipe output is traced
3. Determine how to mark reasoning pipe output as `reasoningContent`
4. Create plan to modify tracing behavior for reasoning pipes

## Findings

### 1. Reasoning Pipe Implementation
Found in Pipe.kt:
- `reasoningPipe: Pipe?` property (line 610)
- `setReasoningPipe(pipe: Pipe)` method (line 1814)
- `executeReasoningPipe(content: MultimodalContent)` method (line 3102)

### 2. Current Execution Flow
1. Main pipe calls `executeReasoningPipe()` (line 2813)
2. Reasoning pipe is executed via `reasoningPipe?.executeMultimodal(contentCopy)` (line 3282)
3. Result text is added to `contentCopy.modelReasoning` (line 3283)
4. Tracing is enabled for reasoning pipe (line 3193)

### 3. Problem Identified
**Issue**: When reasoning pipe executes, its output is traced as regular pipe output, not as `reasoningContent`.

**Current behavior**: 
- Reasoning pipe traces show normal `API_CALL_SUCCESS` with `resultTextLength`
- Output appears as regular text, not marked as reasoning

**Desired behavior**:
- Reasoning pipe output should be traced with `reasoningContent` metadata
- Should be clearly identified as reasoning output in traces

## Plan to Fix

### Option 1: Modify Reasoning Pipe Tracing
Add special tracing behavior when a pipe is used as a reasoning pipe:
1. Add `isReasoningPipe` flag to identify reasoning pipes
2. Modify trace calls in reasoning pipes to use `reasoningContent` metadata
3. Override trace behavior in `executeReasoningPipe()`

### Option 2: Post-Process Reasoning Pipe Traces
Intercept traces from reasoning pipes and modify metadata:
1. Track when reasoning pipe is executing
2. Modify trace metadata to mark output as `reasoningContent`
3. Add reasoning-specific trace events

## Implementation Plan

### Solution: Custom Trace Metadata for Reasoning Pipes

**Approach**: Override the reasoning pipe's trace behavior to add `reasoningContent` metadata.

**Implementation Steps**:
1. Add a flag to identify when a pipe is being used for reasoning
2. Modify `executeReasoningPipe()` to set this flag before execution
3. Override trace calls in reasoning context to add `reasoningContent` metadata
4. Ensure reasoning pipe output is marked appropriately in traces

### Code Changes Needed

**File**: `/home/cage/Desktop/Workspaces/TPipe/TPipe/src/main/kotlin/Pipe/Pipe.kt`

**Change 1**: Add reasoning context flag
```kotlin
private var isExecutingAsReasoningPipe = false
```

**Change 2**: Modify executeReasoningPipe to set reasoning context
```kotlin
// Before reasoning pipe execution
reasoningPipe?.let { pipe ->
    pipe.isExecutingAsReasoningPipe = true
    val result = pipe.executeMultimodal(contentCopy)
    pipe.isExecutingAsReasoningPipe = false
    // Process result...
}
```

**Change 3**: Override trace method to detect reasoning context
```kotlin
// In trace method, add reasoning metadata when appropriate
if (isExecutingAsReasoningPipe && eventType == TraceEventType.API_CALL_SUCCESS) {
    val enhancedMetadata = metadata.toMutableMap()
    enhancedMetadata["reasoningContent"] = content?.text ?: ""
    enhancedMetadata["isReasoningPipe"] = true
    // Continue with enhanced metadata
}
```

## ✅ IMPLEMENTATION COMPLETE

### Changes Made

**File**: `/home/cage/Desktop/Workspaces/TPipe/TPipe/src/main/kotlin/Pipe/Pipe.kt`

**Change 1**: Added reasoning context flag (after line 610)
```kotlin
@kotlinx.serialization.Transient
private var isExecutingAsReasoningPipe = false
```

**Change 2**: Modified executeReasoningPipe to set context flag (line ~3282)
```kotlin
val result = reasoningPipe?.let { pipe ->
    pipe.isExecutingAsReasoningPipe = true
    val pipeResult = pipe.executeMultimodal(contentCopy)
    pipe.isExecutingAsReasoningPipe = false
    pipeResult
} ?: content
```

**Change 3**: Enhanced buildMetadataForLevel to add reasoning metadata (line ~2032)
```kotlin
// Add reasoning pipe metadata when this pipe is executing as a reasoning pipe
if (isExecutingAsReasoningPipe && eventType == TraceEventType.API_CALL_SUCCESS && content != null) {
    metadata["reasoningContent"] = content.text
    metadata["isReasoningPipe"] = true
    metadata["modelSupportsReasoning"] = true
    metadata["reasoningEnabled"] = true
}
```

### Expected Behavior

When a reasoning pipe executes, its traces will now show:
- `reasoningContent=<actual reasoning output>`
- `isReasoningPipe=true`
- `modelSupportsReasoning=true`
- `reasoningEnabled=true`

This clearly distinguishes reasoning pipe output from regular pipe output in trace files.

### Status: COMPLETE
Reasoning pipe tracing has been enhanced to properly mark output as reasoningContent.
