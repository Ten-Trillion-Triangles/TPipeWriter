# Chapter Rewrite Pipeline Diagram

## Pipeline Overview
```
User Request → Analysis → Lore Validation → Rewrite → Style Check → Style Suggest → Style Fix
```

## Complete Pipeline Flow

### 1. Analysis Pipe
```
┌─────────────────────────────────────────────────────────────────┐
│                        ANALYSIS PIPE                           │
├─────────────────────────────────────────────────────────────────┤
│ Model: deepseek.r1-v1:0 (us-east-2)                           │
│ Function: storeRewritePlan                                      │
├─────────────────────────────────────────────────────────────────┤
│ INPUT:                                                          │
│ • User request (text)                                          │
│ • Context: "rewriteContext, prevChapter"                       │
│ • Global context (lorebook)                                    │
├─────────────────────────────────────────────────────────────────┤
│ PROCESSING:                                                     │
│ • Analyzes user's revision request                             │
│ • Uses story context and previous chapter                      │
│ • Creates concrete rewrite plan                                │
├─────────────────────────────────────────────────────────────────┤
│ OUTPUT:                                                         │
│ • RewriteActions (JSON)                                        │
│   - changesToMake: Map<String, String>                        │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
```

### 2. Lore Validation Pipe
```
┌─────────────────────────────────────────────────────────────────┐
│                    LORE VALIDATION PIPE                        │
├─────────────────────────────────────────────────────────────────┤
│ Model: openai.gpt-oss-20b-1:0 (us-east-2)                    │
│ Function: None (no transformation function)                     │
├─────────────────────────────────────────────────────────────────┤
│ INPUT:                                                          │
│ • RewriteActions (from Analysis Pipe)                          │
│ • Context: "rewriteContext, prevChapter"                       │
│ • Global context (lorebook)                                    │
├─────────────────────────────────────────────────────────────────┤
│ PROCESSING:                                                     │
│ • Validates rewrite plan against existing lore                 │
│ • Ensures conformity to user's request                        │
│ • Checks for contradictions with official story               │
├─────────────────────────────────────────────────────────────────┤
│ OUTPUT:                                                         │
│ • RewriteStyleActions (JSON)                                   │
│   - needsChanges: Boolean                                      │
│   - changesToMake: List<String>                                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
```

### 3. Rewrite Pipe
```
┌─────────────────────────────────────────────────────────────────┐
│                        REWRITE PIPE                            │
├─────────────────────────────────────────────────────────────────┤
│ Model: openai.gpt-oss-20b-1:0 (us-east-2)                    │
│ Function: transformRewriteResult                                │
├─────────────────────────────────────────────────────────────────┤
│ INPUT:                                                          │
│ • RewriteStyleActions (from Lore Validation Pipe)             │
│ • Context: "prevChapter, rewriteContext"                       │
│ • Global context (lorebook)                                    │
├─────────────────────────────────────────────────────────────────┤
│ PROCESSING:                                                     │
│ • Performs actual chapter rewriting                           │
│ • Uses validated plan and context                              │
│ • Returns only rewritten chapter text                         │
├─────────────────────────────────────────────────────────────────┤
│ OUTPUT:                                                         │
│ • Raw text (rewritten chapter)                                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
```

### 4. Style Check Pipe
```
┌─────────────────────────────────────────────────────────────────┐
│                      STYLE CHECK PIPE                          │
├─────────────────────────────────────────────────────────────────┤
│ Model: openai.gpt-oss-20b-1:0 (us-east-2)                    │
│ Function: checkWritingStyle                                     │
├─────────────────────────────────────────────────────────────────┤
│ INPUT:                                                          │
│ • Rewritten chapter text (from Rewrite Pipe)                  │
│ • Style guide parameter                                        │
├─────────────────────────────────────────────────────────────────┤
│ PROCESSING:                                                     │
│ • Evaluates if chapter conforms to style guidelines           │
│ • Can end pipeline early if style is correct                  │
├─────────────────────────────────────────────────────────────────┤
│ OUTPUT:                                                         │
│ • RewriteStyleActions (JSON)                                   │
│   - needsChanges: Boolean                                      │
│   - changesToMake: List<String>                                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
```

### 5. Style Suggest Pipe
```
┌─────────────────────────────────────────────────────────────────┐
│                     STYLE SUGGEST PIPE                         │
├─────────────────────────────────────────────────────────────────┤
│ Model: openai.gpt-oss-20b-1:0 (us-east-2)                    │
│ Function: styleSuggestPreValidate (pre-validation)              │
├─────────────────────────────────────────────────────────────────┤
│ INPUT:                                                          │
│ • RewriteStyleActions (from Style Check Pipe)                 │
│ • Context: "rewrittenChapter, main"                            │
│ • Global context                                               │
├─────────────────────────────────────────────────────────────────┤
│ PROCESSING:                                                     │
│ • Suggests specific fixes for style issues                    │
│ • Identifies what needs changing and how                       │
├─────────────────────────────────────────────────────────────────┤
│ OUTPUT:                                                         │
│ • RewriteActions (JSON)                                        │
│   - changesToMake: Map<String, String>                        │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
```

### 6. Style Fix Pipe
```
┌─────────────────────────────────────────────────────────────────┐
│                       STYLE FIX PIPE                           │
├─────────────────────────────────────────────────────────────────┤
│ Model: openai.gpt-oss-20b-1:0 (us-east-2)                    │
│ Function: transformRewriteStyle                                 │
├─────────────────────────────────────────────────────────────────┤
│ INPUT:                                                          │
│ • RewriteActions (from Style Suggest Pipe)                    │
│ • Context: "rewrittenChapter"                                  │
│ • Global context                                               │
├─────────────────────────────────────────────────────────────────┤
│ PROCESSING:                                                     │
│ • Applies specific style fixes                                 │
│ • Makes only instructed changes                                │
│ • Preserves story content and intent                           │
├─────────────────────────────────────────────────────────────────┤
│ OUTPUT:                                                         │
│ • Final rewritten chapter text                                │
└─────────────────────────────────────────────────────────────────┘
```

## Data Structure Flow

### RewriteActions
```kotlin
data class RewriteActions(
    var changesToMake: Map<String, String>
    // Key: subject to change
    // Value: specific change description
)
```

### RewriteStyleActions
```kotlin
data class RewriteStyleActions(
    var needsChanges: Boolean,
    var changesToMake: List<String>
)
```

### LoreRewriteActions (Defined but unused)
```kotlin
data class LoreRewriteActions(
    var needsChanges: Boolean,
    var changesToMake: Map<String, String>
)
```

## Context Flow by Pipe
```
Analysis Pipe:
  • Global Context + "rewriteContext, prevChapter"

Lore Validation Pipe:
  • Global Context + "rewriteContext, prevChapter"

Rewrite Pipe:
  • Global Context + "prevChapter, rewriteContext"

Style Check Pipe:
  • No specific context keys (uses input text)

Style Suggest Pipe:
  • Global Context + "rewrittenChapter, main"

Style Fix Pipe:
  • Global Context + "rewrittenChapter"
```

## Pipeline Execution Order
1. **Analysis Pipe** → RewriteActions
2. **Lore Validation Pipe** → RewriteStyleActions  
3. **Rewrite Pipe** → Raw chapter text
4. **Style Check Pipe** → RewriteStyleActions
5. **Style Suggest Pipe** → RewriteActions
6. **Style Fix Pipe** → Final chapter text

## Key Features
- **Early termination**: Style Check can end pipeline if no changes needed
- **GPT-OSS safety bypass**: Uses `gptPromptBans` for creative writing
- **Context switching**: Different pipes use different context keys
- **Dual style processing**: Separate check and fix phases for style