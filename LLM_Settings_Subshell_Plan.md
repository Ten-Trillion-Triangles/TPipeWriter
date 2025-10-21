# LLM Settings Subshell Implementation Plan

## Overview
Create a new subshell to manage LLM settings for all pipelines in TPipeWriter, allowing users to configure model settings per pipeline, export/import configurations, and refactor existing settings management.

## Requirements Analysis
- Use ModelSettings data class and associated functions from WriterSettings.kt
- Support all pipeline types: writer, idea, lorebook, rewrite, and chat
- Enable settings export/import functionality
- Refactor main settings command to move LLM-specific settings to subshell
- Follow project design standards and formatting rules
- Main implementation in SettingsSubshell.kt

## Files to be Modified

### 1. `/main/kotlin/Shell/SettingsSubshell.kt` (MAJOR CHANGES)
**Current State**: Nearly empty file with placeholder function
**Changes Required**:
- Implement complete LLM settings subshell
- Add pipeline selection menu
- Add model configuration functions
- Add export/import functionality
- Follow project formatting standards

### 2. `/main/kotlin/Shell/Shell.kt` (MODERATE CHANGES)
**Current State**: Contains configureSettings() function with all settings
**Changes Required**:
- Refactor configureSettings() to remove LLM-specific settings
- Add call to new LLM settings subshell
- Keep non-LLM settings (writing style, auto-lorebook, etc.)
- Update help text and command routing

### 3. `/main/kotlin/Structs/WriterSettings.kt` (MINOR CHANGES)
**Current State**: Contains ModelSettings data class and utility functions
**Changes Required**:
- Add export/import functions for ModelSettings lists
- Add pipeline name mapping functions
- Potentially add validation functions

## New Functionality to Implement

### Core Subshell Features
1. **Pipeline Selection Menu**
   - List all available pipelines (writer, idea, lorebook, rewrite, chat)
   - Show current model settings for each pipeline
   - Allow selection of specific pipeline for configuration

2. **Model Configuration**
   - Provider selection (AWS, Ollama, etc.)
   - Model name configuration with validation
   - Temperature and TopP adjustment
   - Region auto-setting based on model

3. **Settings Management**
   - Export current settings to JSON file
   - Import settings from JSON file
   - Reset to default settings
   - Validate settings before applying

4. **Pipeline Integration**
   - Extract current settings from active pipelines
   - Apply new settings to pipelines with re-initialization
   - Handle different provider types appropriately

## Implementation Structure

### SettingsSubshell.kt Functions
```kotlin
// Main entry point
fun manageLLMSettings()

// Menu and navigation
fun showLLMSettingsMenu()
fun selectPipeline(): String?

// Configuration functions
fun configurePipelineSettings(pipelineName: String)
fun showCurrentSettings(pipelineName: String)
fun updateModelSettings(pipelineName: String, settings: ModelSettings)

// Export/Import functions
fun exportLLMSettings()
fun importLLMSettings()
fun saveSettingsToFile(settings: Map<String, ModelSettings>, filename: String)
fun loadSettingsFromFile(filename: String): Map<String, ModelSettings>?

// Utility functions
fun getPipelineByName(name: String): Pipeline?
fun validateModelSettings(settings: ModelSettings): Boolean
fun applySettingsToAllPipelines(settingsMap: Map<String, ModelSettings>)
```

### Shell.kt Refactoring
```kotlin
// Updated configureSettings() - remove LLM settings
fun configureSettings() {
    // Keep only: writing style, auto-lorebook, general app settings
    // Add option to access LLM settings subshell
}

// Add new command routing
when(extractedSlashCommand) {
    // ... existing commands ...
    "llm-settings" -> manageLLMSettings()
    // ... rest of commands ...
}
```

## Data Flow

### Settings Extraction
1. User enters LLM settings subshell
2. Extract current settings from all pipelines using `constructModelSettingsList()`
3. Display current configuration in organized menu

### Settings Modification
1. User selects pipeline to configure
2. Show current ModelSettings for that pipeline
3. Allow modification of provider, model, temperature, topP
4. Validate settings using model name functions
5. Apply settings using `updatePipeWithModelSettings()`

### Export/Import Flow
1. **Export**: Collect settings from all pipelines → Serialize to JSON → Save to file
2. **Import**: Load JSON file → Deserialize to ModelSettings → Validate → Apply to pipelines

## User Interface Design

### Main LLM Settings Menu
```
=== LLM Settings Management ===
Current Pipeline Settings:
  1. Writer Pipeline    - deepseek.r1-v1:0 (temp: 0.7, topP: 0.7)
  2. Idea Pipeline      - deepseek.r1-v1:0 (temp: 0.7, topP: 0.7)
  3. Lorebook Pipeline  - deepseek.r1-v1:0 (temp: 0.7, topP: 0.7)
  4. Rewrite Pipeline   - anthropic.claude-sonnet-4 (temp: 0.5, topP: 0.9)
  5. Chat Pipeline      - deepseek.r1-v1:0 (temp: 0.8, topP: 0.8)

Commands:
  config <number>  - Configure specific pipeline
  export          - Export all settings to file
  import          - Import settings from file
  reset           - Reset all to defaults
  status          - Show detailed current settings
  back            - Return to main shell
```

### Pipeline Configuration Menu
```
=== Configuring Writer Pipeline ===
Current Settings:
  Provider: AWS
  Model: deepseek.r1-v1:0
  Region: us-east-2 (auto-set)
  Temperature: 0.7
  Top P: 0.7

Available Models:
  1. deepseek.r1-v1:0 (DeepSeek R1)
  2. anthropic.claude-sonnet-4-20250514-v1:0 (Claude Sonnet 4)
  3. amazon.nova-pro-v1:0 (Nova Pro)
  4. amazon.nova-lite-v1:0 (Nova Lite)
  5. openai.gpt-oss-120b-1:0 (GPT OSS 120B)

Commands:
  model <number>     - Select model
  temp <value>       - Set temperature (>= 0.0)
  topp <value>       - Set top P (0.0-1.0)
  apply             - Apply changes and reinitialize
  cancel            - Cancel changes
  back              - Return to main menu
```

## Error Handling

### Validation Requirements
- Temperature: >= 0.0 (no upper limit, some models support values > 2.0)
- TopP: 0.0 - 1.0 range
- Model names: Must match available models
- Provider compatibility: Ensure model works with provider
- Region validation: Auto-set based on model requirements

### Error Recovery
- Invalid settings: Show error, keep current settings
- File I/O errors: Graceful failure with user notification
- Pipeline initialization failures: Rollback to previous settings
- JSON parsing errors: Clear error messages with examples

## Integration Points

### Existing System Integration
- Use `Env.writerPipeline`, `Env.ideaPipeline`, etc. for pipeline access
- Integrate with existing `constructModelSettingsList()` and `updatePipeWithModelSettings()`
- Maintain compatibility with current save/load story functionality
- Preserve existing settings file format where possible

### Command Integration
- Add `/llm-settings` command to main shell
- Update help text to include new command
- Ensure subshell follows same input patterns as other subshells

## Testing Strategy

### Manual Testing Scenarios
1. **Basic Configuration**: Change model for each pipeline type
2. **Export/Import**: Export settings, modify, import, verify changes
3. **Error Handling**: Invalid inputs, file errors, model incompatibilities
4. **Integration**: Ensure pipelines work correctly after settings changes
5. **Persistence**: Settings survive application restart

### Edge Cases
- Empty/corrupted settings files
- Unsupported model names
- Network issues during pipeline initialization
- Concurrent access to settings (if applicable)

## Implementation Priority

### Phase 1: Core Functionality
1. Basic subshell structure and navigation
2. Pipeline selection and current settings display
3. Model configuration for individual pipelines
4. Settings validation and application

### Phase 2: Advanced Features
1. Export/import functionality
2. Bulk operations (reset all, apply to all)
3. Enhanced error handling and recovery
4. Settings backup and versioning

### Phase 3: Polish and Integration
1. Help text and documentation
2. Command shortcuts and aliases
3. Integration testing with all pipeline types
4. Performance optimization

## File Structure Summary

```
Files to Modify:
├── main/kotlin/Shell/SettingsSubshell.kt (MAJOR - complete implementation)
├── main/kotlin/Shell/Shell.kt (MODERATE - refactor configureSettings)
└── main/kotlin/Structs/WriterSettings.kt (MINOR - add export/import helpers)

New Functionality:
├── LLM Settings Subshell (complete interactive interface)
├── Pipeline-specific model configuration
├── Settings export/import system
└── Enhanced validation and error handling
```

This plan provides a comprehensive roadmap for implementing the LLM settings subshell while maintaining compatibility with existing systems and following project design standards.