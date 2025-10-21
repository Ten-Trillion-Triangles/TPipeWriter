# Chapter Rewriting Pipeline Implementation Plan

## Overview
This plan outlines the creation of a comprehensive chapter rewriting pipeline that accepts existing chapters, processes user rewrite requests, fixes lore contradictions, and addresses style issues. The system will integrate with TPipeWriter's existing shell command structure.

## Phase 1: Core Pipeline Architecture

### 1.1 ChapterRewritePipeline Class
Create `Builders/ChapterRewritePipeline.kt` with the following components:

```kotlin
fun buildChapterRewritePipeline(
    temperature: Double = 0.7,
    topP: Double = 0.9,
    maxTokens: Int = 10000,
    contextWindowMax: Int = 105000
): Pipeline
```

### 1.2 Pipeline Stages
The pipeline will consist of four sequential stages:

1. **Analysis Stage** - Analyze the chapter and user request
2. **Lore Validation Stage** - Check for lore contradictions
3. **Rewrite Stage** - Perform the actual rewriting
4. **Style Correction Stage** - Apply style fixes

### 1.3 Data Structures
```kotlin
@Serializable
data class ChapterRewriteRequest(
    val originalChapter: String,
    val userRequest: String,
    val chapterIndex: Int
)

@Serializable
data class RewriteAnalysis(
    val identifiedIssues: List<String>,
    val rewriteStrategy: String,
    val loreReferences: List<String>
)

@Serializable
data class ChapterRewriteResult(
    val rewrittenChapter: String,
    val changesApplied: List<String>,
    val loreIssuesFixed: List<String>,
    val styleImprovements: List<String>
)
```

## Phase 2: Pipeline Implementation

### 2.1 Analysis Pipe
- **Model**: Claude Sonnet 4
- **Purpose**: Understand user request and analyze chapter
- **System Prompt**: Analyze the chapter and user request to identify what needs to be changed
- **Output**: RewriteAnalysis object

### 2.2 Lore Validation Pipe
- **Model**: DeepSeek R1
- **Purpose**: Check for lore contradictions using existing lorebook
- **Input**: Original chapter + lorebook context
- **Output**: LoreCheckOutput (reusing existing structure)
- **Validation Function**: `validateLoreChecker`

### 2.3 Rewrite Pipe
- **Model**: Claude Sonnet 4
- **Purpose**: Perform the actual rewriting based on analysis and lore validation
- **Context**: Full lorebook + recent chapters for consistency
- **System Prompt**: Rewrite the chapter according to the user's request while maintaining lore consistency

### 2.4 Style Correction Pipe
- **Model**: Nova Lite (for efficiency)
- **Purpose**: Apply final style corrections and polish
- **Transformation Function**: `transformStyle` (reusing existing)
- **System Prompt**: Apply style corrections and ensure consistent writing quality

## Phase 3: Shell Command Integration

### 3.1 New Shell Command: `/rewrite`
Add to `Shell/Shell.kt` in the `parseInput()` function:

```kotlin
"rewrite" -> {
    commandState = CommandState.Writer
    if (remainingText.isNotEmpty()) {
        callChapterRewritePipeline(remainingText)
    } else {
        manageChapterRewrite()
    }
}
```

### 3.2 Chapter Selection Interface
Create `manageChapterRewrite()` function:
- List available chapters with indices
- Allow user to select chapter by number
- Prompt for rewrite instructions
- Execute pipeline with selected chapter

### 3.3 Interactive Rewrite Function
Create `callChapterRewritePipeline(prompt: String)` function:
- Parse chapter selection from prompt (e.g., "/rewrite 3 make it more dramatic")
- Extract chapter content using ChapterManager
- Execute rewrite pipeline
- Present results with approval interface

## Phase 4: User Approval System

### 4.1 Result Presentation
After pipeline execution:
1. Display original chapter (first 200 chars)
2. Display rewritten chapter (full content)
3. Show summary of changes applied
4. Present approval options

### 4.2 Approval Interface
```
=== Chapter Rewrite Results ===
Original: "The old content..."
Rewritten: "The new improved content..."

Changes Applied:
- Fixed lore contradiction with character backstory
- Enhanced dramatic tension in dialogue
- Corrected style inconsistencies

Options:
[A]ccept - Replace original chapter with rewritten version
[R]eject - Keep original chapter unchanged
[E]dit - Make manual edits to rewritten version
[V]iew - Show full comparison side-by-side
```

### 4.3 Chapter Replacement Logic
- If accepted: Update ContextWindow and ChapterMetadata
- If rejected: Discard rewrite, keep original
- If edit: Open interactive editor for manual changes
- Update chapter metadata with rewrite timestamp

## Phase 5: Integration Points

### 5.1 Existing System Integration
- Reuse `AdvancedWriterPipeline.kt` validation functions
- Integrate with `ChapterManager` for chapter operations
- Use existing `GlobalChapterManager` for metadata updates
- Leverage current lorebook system for lore validation

### 5.2 Context Management
- Pull chapter content from main ContextBank
- Include relevant lorebook entries based on chapter content
- Maintain context isolation during rewrite process
- Update main context only upon user approval

### 5.3 Error Handling
- Validate chapter indices before processing
- Handle pipeline failures gracefully
- Provide fallback options if rewrite fails
- Maintain data integrity throughout process

## Phase 6: Advanced Features

### 6.1 Batch Rewriting
Support for rewriting multiple chapters:
```
/rewrite 1-3 improve pacing and dialogue
```

### 6.2 Rewrite Templates
Predefined rewrite types:
- `/rewrite 2 --style-fix` - Focus on style corrections only
- `/rewrite 5 --lore-check` - Focus on lore consistency
- `/rewrite 1 --dramatic` - Enhance dramatic elements

### 6.3 Comparison View
Side-by-side comparison of original vs rewritten:
- Highlight specific changes
- Show word count differences
- Display lore references added/removed

## Implementation Timeline

### Week 1: Core Pipeline
- Create ChapterRewritePipeline class
- Implement basic four-stage pipeline
- Add data structures and validation

### Week 2: Shell Integration
- Add `/rewrite` command to shell
- Implement chapter selection interface
- Create approval system

### Week 3: Testing & Refinement
- Test with various chapter types
- Refine prompts and validation
- Add error handling

### Week 4: Advanced Features
- Implement batch rewriting
- Add comparison views
- Create rewrite templates

## File Structure
```
TPipeWriter/src/main/kotlin/
├── Builders/
│   ├── ChapterRewritePipeline.kt (NEW)
│   └── Util/
│       └── ChapterRewriteUtil.kt (NEW)
├── Shell/
│   └── Shell.kt (MODIFIED - add rewrite commands)
├── Chapter/
│   ├── ChapterRewriter.kt (NEW)
│   └── RewriteManager.kt (NEW)
└── Structs/
    └── RewriteData.kt (NEW)
```

## Dependencies
- Existing TPipe framework
- BedrockMultimodalPipe for model interactions
- ChapterManager for chapter operations
- GlobalChapterManager for metadata
- ContextBank for context management

This plan provides a comprehensive approach to implementing chapter rewriting functionality while maintaining integration with existing TPipeWriter systems and following established patterns.