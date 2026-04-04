# TPipeWriter Feature Guide: Commands List

This document serves as the source of truth for all user-facing commands in the TPipeWriter CLI.

## Main Shell Commands

These commands are available from the primary shell prompt.

| Command | Description | Usage/Parameters |
|---------|-------------|------------------|
| `/write` | Executes the story writing pipeline. | `/write <prompt>` or `/write` (to continue) |
| `/idea` | Generates story ideas or enters Idea Management. | `/idea <prompt>` or `/idea` |
| `/chat` | Starts a general discussion/chat with the AI. | `/chat <prompt>` |
| `/character` | Enters the Character Chat subshell. | `/character <name>` or `/character` |
| `/lorebook` | Executes the lorebook generation pipeline. | `/lorebook <prompt>` |
| `/summary` | Generates a summary of the current story context. | `/summary <prompt>` |
| `/rewrite` | Rewrites a chapter or enters Rewrite Management. | `/rewrite <prompt>` or `/rewrite` |
| `/chapters` | Enters Chapter Management mode. | `/chapters` |
| `/lore` | Enters Lorebook Management mode. | `/lore` |
| `/settings` | Opens the general application settings menu. | `/settings` |
| `/llm-settings` | Enters the LLM/Model configuration subshell. | `/llm-settings` |
| `/guide` | Opens the Guide Management menu (Chapter/Story/Author guides). | `/guide` |
| `/author` | Opens the Author/Reviewer guide menu. | `/author` |
| `/persona` | Enters the Persona Assignment subshell. | `/persona` |
| `/pitch` | Executes the Pitch Slide generation pipeline. | `/pitch <prompt>` |
| `/save` | Saves the current story context to disk. | `/save` |
| `/load` | Loads a previously saved story context. | `/load` |
| `/export` | Exports the story to a text file. | `/export` |
| `/clear` | Clears the current story context. | `/clear` |
| `/import-lorebook`| Imports a lorebook from a JSON file. | `/import-lorebook <filename>` |
| `/import-nai` | Imports a story from NovelAI format. | `/import-nai <filename>` |
| `/clear-chat` | Clears the global chat history. | `/clear-chat` |
| `/style` | Displays the current writing style settings. | `/style` |
| `/help` | Displays the main help menu. | `/help` |
| `/exit` | Exits the TPipeWriter application. | `/exit` |

---

## Subshell Commands

### Writer Subshell (`/write //`)
Advanced controls for the writing pipeline.

| Command | Description |
|---------|-------------|
| `level` | Sets pipeline strength (e.g., `low`, `med`). |
| `context` | Configures advanced context selection (chapter ranges, etc.). |
| `advanced`| Toggles advanced mode on/off. |
| `status` | Shows current writer configuration. |
| `write` | Executes the pipeline with current settings. |
| `back` | Returns to the main shell. |

### Idea Management (`/idea`)
Manage how the AI brainstorms new concepts.

| Command | Description |
|---------|-------------|
| `settings` | Configures lookback depth and lorebook budget. |
| `forced` | Manages "forced" chapters that are always in context. |
| `status` | Shows current idea generation settings. |
| `generate` | Generates ideas using the current configuration. |

### Lorebook Management (`/lore`)
Directly manage the world-building database.

| Command | Description |
|---------|-------------|
| `list` | Lists all lorebook entries and their weights. |
| `view` | Displays the content of a specific entry. |
| `edit` | Edits the content of an entry. |
| `add` | Creates a new lorebook entry. |
| `delete` | Removes an entry. |
| `weight` | Sets the importance/weight of an entry. |
| `link` | Links entries together so they trigger together. |
| `alias` | Adds alternative names (aliases) to an entry. |

### Chapter Management (`/chapters`)
Tools for organizing and editing story structure.

| Command | Description |
|---------|-------------|
| `list` | Lists all chapters with word counts and previews. |
| `show` | Displays the full text of a specific chapter. |
| `edit` | Opens a chapter for editing. |
| `add` | Adds a new blank chapter. |
| `insert` | Inserts a chapter at a specific position. |
| `move` | Reorders chapters. |
| `delete` | Removes a chapter. |
| `search` | Searches for text across all chapters. |
| `stats` | Displays story-wide statistics. |

### LLM Settings (`/llm-settings`)
Configure models, providers, and parameters for every pipeline.

| Command | Description |
|---------|-------------|
| `config` | Configures a specific pipeline's model and parameters. |
| `status` | Shows detailed status of all model assignments. |
| `export` | Exports LLM configurations to a file. |
| `import` | Imports LLM configurations from a file. |
| `reset` | Resets all models to system defaults. |
| `bulk` | Applies settings to all pipelines at once. |

### Character Chat (`/character`)
Roleplay or interview specific characters.

| Command | Description |
|---------|-------------|
| `character` | Selects a character to chat with. |
| `list` | Lists all available character personas. |
| `story` | Enables story context for the character chat. |
| `clear` | Clears the chat history for the active character. |

### Persona Subshell (`/persona`)
Assign specific AI personalities to system roles.

| Role | Description |
|------|-------------|
| `Author` | The primary writing persona. |
| `Editor` | The persona responsible for refining text. |
| `Reviewer` | The persona that critiques and provides feedback. |
| `Control` | Background persona for structural guidance. |
