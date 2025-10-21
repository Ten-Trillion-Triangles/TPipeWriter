# Lorebook Management Function Plan

## Overview
Create an interactive lorebook management system within the shell that allows users to view, edit, and manage lorebook entries and their weights.

## Required Features

### 1. List Lorebook Keys
- Display all available lorebook keys
- Show key names with optional weight indicators
- Paginated display for large lorebooks
- Search/filter capability

### 2. View Lorebook Values
- Display the full content of a selected lorebook key
- Show key metadata (weight, creation date, etc.)
- Formatted output for readability

### 3. Edit Lorebook Values
- Interactive text editor for lorebook content
- Save/cancel functionality
- Validation for content changes
- Backup original content before editing

### 4. Adjust Lorebook Weights
- Modify weight values for lorebook keys
- Weight validation (ensure proper range/format)
- Batch weight adjustment capability

## Implementation Plan

### Step 1: Add Lorebook Management Command
**Function**: `manageLorebook()`
- Add `/lore` command to parseInput()
- Route to lorebook management interface
- Display main lorebook menu

### Step 2: Create Lorebook Menu System
**Function**: `showLorebookMenu()`
- Display available actions:
  - `list` - List all keys
  - `view <key>` - View specific key content
  - `edit <key>` - Edit key content
  - `weight <key> <value>` - Set key weight
  - `search <term>` - Search keys
  - `back` - Return to main shell

### Step 3: Implement Key Listing
**Function**: `listLorebookKeys()`
- Get lorebook from ContextBank("main")
- Display keys with weights in formatted table
- Handle pagination for large lists
- Show total key count

### Step 4: Implement Key Viewing
**Function**: `viewLorebookKey(keyName: String)`
- Retrieve key content from lorebook
- Display formatted content with metadata
- Handle non-existent keys gracefully

### Step 5: Implement Key Editing
**Function**: `editLorebookKey(keyName: String)`
- Load current key content
- Present interactive editor interface
- Save changes back to ContextBank
- Update lorebook in main context

### Step 6: Implement Weight Management
**Function**: `adjustLorebookWeight(keyName: String, weight: Double)`
- Validate weight value (0.0 to 1.0 range)
- Update key weight in lorebook
- Confirm weight change to user

### Step 7: Add Search Functionality
**Function**: `searchLorebookKeys(searchTerm: String)`
- Search key names and content
- Display matching results
- Allow selection from search results

## Technical Requirements

### Data Access
- Use `ContextBank.getContextFromBank("main")` to access lorebook
- Access lorebook via `contextWindow.loreBookKeys`
- Use `ContextBank.emplaceWithMutex()` for safe updates

### Lorebook Structure
```kotlin
// From LoreBook class
class LoreBook {
    var key: String = ""
    var value: String = ""
    var weight: Double = 1.0
    // ... other properties
}
```

### Menu Navigation
- Continuous loop until user exits
- Input validation and error handling
- Clear command syntax and help text

## Command Structure

### Main Command
`/lore` - Enter lorebook management mode

### Sub-commands (within lorebook mode)
- `list` - Show all keys
- `view <keyname>` - Display key content
- `edit <keyname>` - Edit key content  
- `weight <keyname> <value>` - Set key weight
- `search <term>` - Search keys/content
- `add <keyname>` - Create new key
- `delete <keyname>` - Remove key
- `help` - Show lorebook commands
- `back` - Return to main shell

## Error Handling
- Invalid key names
- Invalid weight values
- Empty lorebook scenarios
- Concurrent access protection
- Input validation

## User Experience
- Clear prompts and instructions
- Confirmation for destructive operations
- Formatted output for readability
- Intuitive command syntax
- Help text for all operations

## Integration Points
- Update help text in `printHelp()` to include `/lore`
- Add lorebook command to `parseInput()` routing
- Ensure thread safety with ContextBank operations
- Maintain consistency with existing shell patterns