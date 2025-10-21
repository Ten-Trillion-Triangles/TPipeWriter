# Writer Pipeline Refactor Plan

## Current Issues
- `callWriterPipeline` doesn't use prompt for lorebook key selection
- `selectAndTruncateContext` always uses full context instead of smart selection
- No handling for idea-to-writing workflow
- No differentiation between "continue" vs "finish chapter" requests

## Required Changes

### 1. Prompt Analysis & Context Selection
**Function**: `determineContextSelectionStrategy(prompt: String, previousState: CommandState)`
- **Input**: User prompt + previous command state
- **Logic**:
  - If prompt contains specific request → use prompt for lorebook selection
  - If prompt is "continue" or "finish chapter" → use last 8K tokens for lorebook
  - If previous state was `Idea` → include idea content + use prompt for lorebook
- **Output**: Context selection strategy enum

### 2. Smart Context Extraction
**Function**: `extractRelevantContext(strategy: ContextSelectionStrategy, prompt: String)`
- **Specific Prompt**: Use full prompt text for lorebook key matching
- **Continue/Finish**: Extract last 8K tokens from `contextElements` bottom
- **Post-Idea**: Combine last idea result + current prompt

### 3. Enhanced Lorebook Selection
**Function**: `selectLorebookKeys(contextText: String, lorebookMap: Map<String, String>)`
- Scan context text for lorebook key matches
- Return relevant key-value pairs for injection
- Use fuzzy matching for character names, places, concepts

### 4. Modified selectAndTruncateContext Call
**Current**: Always uses full context + prompt
**New**: Uses smart-selected context based on strategy
- Specific prompt → full context + lorebook keys from prompt
- Continue/finish → last 8K tokens + lorebook keys from those tokens  
- Post-idea → idea content + current context + lorebook keys from both

### 5. Idea State Tracking
**Addition**: Track when last command was `/idea`
- Store idea results in temporary context
- Include in writer pipeline when transitioning from idea to write
- Clear after successful write execution

## Implementation Steps

1. **Add context selection strategy enum**
2. **Create `determineContextSelectionStrategy()` function**
3. **Create `extractRelevantContext()` function** 
4. **Create `selectLorebookKeys()` function**
5. **Modify `callWriterPipeline()` to use new strategy**
6. **Update `selectAndTruncateContext` calls with smart context**
7. **Add idea result storage and retrieval**
8. **Test all three scenarios**

## Key Functions to Modify
- `callWriterPipeline()` - Main entry point
- `callIdeaPipeline()` - Store results for writer pipeline
- Add new helper functions for context strategy

## Expected Behavior
- **Specific prompt**: "Write about the dragon attacking the village" → Uses prompt to find dragon/village lorebook entries
- **Continue**: "continue" → Uses last 8K tokens to find relevant lorebook entries  
- **Post-idea**: After `/idea` then `/write` → Includes generated idea + finds lorebook entries from both idea and prompt