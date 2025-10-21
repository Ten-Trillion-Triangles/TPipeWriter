# Reasoning Pipe Duplication Fix

## Problem
Reasoning pipes are creating duplicate trace entries with the same `reasoningContent`:
- API_CALL_SUCCESS with reasoningContent
- PIPE_SUCCESS with reasoningContent (duplicate)

## Analysis
The issue is that `reasoningContent` is being added to ALL trace events when `isExecutingAsReasoningPipe=true`, causing duplication across multiple trace event types.

## Solution
Only add `reasoningContent` to specific trace events, not all events during reasoning pipe execution.

## ✅ FIXED

**Change Made**: Added phase check to limit when reasoning metadata is added.

**Before**: 
```kotlin
if (isExecutingAsReasoningPipe && eventType == TraceEventType.API_CALL_SUCCESS && content != null)
```

**After**:
```kotlin  
if (isExecutingAsReasoningPipe && eventType == TraceEventType.API_CALL_SUCCESS && phase == TracePhase.EXECUTION && content != null)
```

**Result**: `reasoningContent` will now only appear in `API_CALL_SUCCESS` events during `EXECUTION` phase, eliminating duplication in `PIPE_SUCCESS` and other events.

**Status**: COMPLETE - Reasoning pipe duplication fixed.
