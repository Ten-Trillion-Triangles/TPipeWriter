# Idea Pipeline Sub-Shell Implementation

## Overview
Successfully implemented a comprehensive sub-shell for the idea pipeline with advanced context management and settings configuration.

## Key Features Implemented

### 1. Idea Pipeline Sub-Shell
- **Entry**: `/idea` command without parameters enters interactive sub-shell
- **Direct Generation**: `/idea <prompt>` generates ideas directly
- **Commands Available**:
  - `settings` - Configure lookback chapters and lorebook budget
  - `forced add/remove/list/clear` - Manage forced chapters
  - `status` - Show current configuration and selected chapters
  - `generate <prompt>` - Generate ideas with current settings
  - `help` - Show available commands
  - `back/exit` - Return to main shell

### 2. Settings Configuration
- **Chapters Lookback**: Number of recent chapters to include (default: 3)
- **Lorebook Token Budget**: Reserved tokens for lorebook content (default: 2000)
- **Token Management**: Available context tokens = 107000 - lorebook budget

### 3. Forced Chapter Support
- **Add Chapters**: Force specific chapters into context regardless of lookback setting
- **Remove Chapters**: Remove chapters from forced list
- **List Management**: View and clear all forced chapters
- **Smart Selection**: Combines forced chapters with recent chapters based on lookback

### 4. Enhanced Context Management
- **Chapter Selection**: Automatically selects forced chapters + recent chapters
- **Token Budgeting**: Reserves lorebook tokens before context truncation
- **Proper Truncation**: Uses `selectAndTruncateContext()` with calculated token limits
- **Context Window**: Creates dedicated context window for idea generation

## Implementation Details

### Data Structures
```kotlin
@kotlinx.serialization.Serializable
data class IdeaSettings(
    val chaptersLookback: Int = 3,
    val lorebookTokenBudget: Int = 2000,
    val forcedChapters: MutableList<Int> = mutableListOf()
)
```

### Core Functions
- `callIdeaPipeline()` - Main pipeline execution with context management
- `manageIdeaPipeline()` - Interactive sub-shell loop
- `configureIdeaSettings()` - Settings configuration interface
- `manageForcedChapters()` - Forced chapter management
- `showIdeaStatus()` - Display current configuration and selected chapters

### Context Selection Logic
1. Add all forced chapters to context
2. Add recent chapters based on lookback setting (excluding already forced)
3. Apply token budget calculation (107K - lorebook budget)
4. Truncate context using TPipe's selectAndTruncateContext()
5. Execute idea pipeline with prepared context

## Usage Examples

### Enter Sub-Shell
```
[Writer]> /idea
=== Idea Pipeline Management ===
idea> settings
Chapters to look back (current: 3): 5
Lorebook token budget (current: 2000): 3000
```

### Configure Forced Chapters
```
idea> forced add 1
Chapter 1 added to forced context
idea> forced add 5
Chapter 5 added to forced context
idea> forced list
Forced chapters: 1, 5
```

### Check Status
```
idea> status
=== Idea Pipeline Status ===
Total chapters available: 10
Chapters lookback: 5
Lorebook token budget: 3000
Available context tokens: 104000
Forced chapters: 1, 5
Chapters that would be used: 1, 5, 6, 7, 8, 9, 10
```

### Generate Ideas
```
idea> generate What should happen in the next chapter?
Thinking...
[Generated ideas based on configured context]
```

## Testing
- Created comprehensive unit tests in `IdeaPipelineTest.kt`
- Tests cover settings management, forced chapters, and token calculations
- All tests pass successfully

## Integration
- Seamlessly integrates with existing shell system
- Maintains compatibility with direct idea generation
- Preserves existing idea banking functionality for writer pipeline
- Updated help system to reflect new capabilities