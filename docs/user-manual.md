# TPipeWriter User Manual

## Overview

TPipeWriter is an interactive writing CLI built on the TPipe framework. It is designed for long-form story work, character chat, lore management, and model/pipeline tuning.

## Setup

1. Clone `TPipe` as a sibling repository so the composite build at `../TPipe/TPipe` resolves.
2. Make sure your AWS credentials are available in `~/.aws/credentials` if you plan to use Bedrock-backed models.
3. Build the app with:
   ```bash
   ./gradlew shadowJar
   ```
4. Start the app with the launcher script for your platform:
   ```bash
   ./run.sh
   ```

Use `run.sh` on Linux and other Unix-like systems, `run.command` on macOS, and the equivalent `.bat` launcher if you are on Windows.

## Core Workflow

1. Load or create a story.
2. Use `/write`, `/idea`, `/chat`, `/character`, or `/lorebook` to work on the content.
3. Use `/settings`, `/llm-settings`, `/persona`, and `/guide` to tune runtime behavior.
4. Save or export when you want to preserve progress.

## Main Commands

- `/write [prompt]` - Write the next part of the story.
- `/idea [prompt]` - Brainstorm new story material.
- `/chat [question]` - Ask questions about the story.
- `/character [name]` - Open the character chat subshell.
- `/lorebook [instruction]` - Update or extract lore entries.
- `/summary [options]` - Summarize current story context.
- `/save` - Save live context pages to `~/.TPipeWriter/`.
- `/export` - Export story text, metadata, lorebook, and settings.
- `/load` - Load an exported story package back into memory.
- `/clear` - Clear story content and lorebook data.
- `/clear-chat` - Clear chat history only.
- `/settings` - Update general runtime settings.
- `/llm-settings` - Update model settings per pipeline.
- `/guide` - Manage chapter, story, and author guides.
- `/persona` - Assign prompt personas to active roles.
- `/rewrite [chapter] [instructions]` - Rewrite a chapter.
- `/chapters` - Open chapter management.
- `/lore` - Manage lorebook entries.
- `/style` - Show the current writing style.
- `/pitch` - Open the pitch writer.
- `/author` - Open the author guide / Richard Treadwell menu.
- `/test` - Run the development test hook.
- `/help` - Show the main help screen.
- `/exit` - Quit the app.

## Character Chat

Inside the character subshell:

- `character <name>`, `use <name>`, or `set <name>` selects a character.
- `list` shows the available character prompts.
- `story` rebuilds the active character pipeline with story context enabled.
- `clear` clears the selected character’s chat history.
- `help` shows the character subshell commands.
- `back` or `exit` leaves the subshell.

Select a character before using `story`.

## Settings

### General Settings
`/settings` updates:

- writing style
- max tokens
- auto-lorebook behavior
- active persona assignments

### LLM Settings
`/llm-settings` manages the model and sampling settings for each pipeline. It supports:

- inspect current settings
- configure one pipeline
- export/import settings
- reset all pipelines
- bulk copy/apply operations
- backup current settings

### Guides and Personas
`/guide` manages chapter, story, and author guides. Those values are persisted and rehydrated into runtime state.

`/persona` maps saved prompt entries to:

- Author
- Editor
- Richard Treadwell
- Writing Control

## Saving and Loading

There are two persistence paths:

- `/save` writes the current live context pages to `~/.TPipeWriter/`.
- `/export` writes a portable story package to `~/TPipeWriter/`.

Use `/load` to restore an exported package into the active context.

## Pipeline Behavior

Some pipelines are built at startup and kept resident in memory. Others are created on demand when needed, such as character chat and rewrite flows. This is why prompt changes, persona changes, and guide changes are re-applied through runtime settings instead of waiting for a restart.
