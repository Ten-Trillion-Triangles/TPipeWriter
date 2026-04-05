# TPipeWriter

TPipeWriter is a creative writing CLI built on the **TPipe** framework. It uses resident and on-demand pipelines to manage long-form story context, lore consistency, character chat, guide prompts, and LLM settings through AWS Bedrock and other supported providers.

Full user manual: [docs/user-manual.md](docs/user-manual.md)

## Quickstart

### Prerequisites
- Clone the `TPipe` repository as a sibling of this project so the composite build at `../TPipe/TPipe` resolves correctly.
- Make sure your AWS credentials are available in `~/.aws/credentials` if you plan to use Bedrock-backed models.
- If you use Bedrock inference profiles, run `./scripts/sync-bedrock-bindings.sh` to refresh the local model binding file.

### Build and run
1. Build the app with Gradle if you need a fresh jar or install image:
   ```bash
   ./gradlew shadowJar
   ```
2. Start the CLI with the provided launcher for your platform:
   ```bash
   ./run.sh
   ```

Use `run.sh` on Linux and other Unix-like systems, `run.command` on macOS, and the equivalent launcher script for your platform if you are on Windows.

### Basic workflow
1. Load or create story content.
2. Use `/write`, `/idea`, `/chat`, or `/lorebook` to work on the story.
3. Use `/settings`, `/llm-settings`, `/persona`, and `/guide` to tune the runtime.
4. Save either the active context or a full export when you want to persist work.

## What TPipeWriter Does

TPipeWriter is built around a few core ideas:

- **Pipes** are the smallest execution units. They call models, transform content, or update context.
- **Pipelines** are ordered sets of pipes that move `MultimodalContent` through a workflow.
- **ContextBank** stores named pages of context, such as `main`, `chat`, `summary`, `chapter guide`, and `story guide`.

Most pipelines are constructed at startup in `Env.init(...)` and stay resident in memory. A few flows, such as character chat and rewrite, rebuild their pipeline on demand so they can incorporate the latest selected character or story context.

## Interactive CLI

The main shell supports these commands:

- `/write [prompt]` - Generate the next story content.
- `/idea [prompt]` - Brainstorm ideas and plot points.
- `/chat [question]` - Discuss the story with the discussion pipeline.
- `/character [name]` - Open the character chat subshell or start it with a character prompt.
- `/tokens` - Open the token counting subshell for chapters, lorebook entries, and custom text.
- `/lorebook [instruction]` - Update lorebook entries or extract lore from recent text.
- `/summary [options]` - Summarize the current story context.
- `/save` - Save the current context pages to `~/.TPipeWriter/`.
- `/export` - Export story text, chapter metadata, lorebook, and settings to `~/TPipeWriter/`.
- `/load` - Load an exported story back into the active context.
- `/clear` - Clear story content and lorebook data.
- `/clear-chat` - Clear chat history only.
- `/settings` - Configure general runtime settings like writing style, max tokens, and auto-lorebook behavior.
- `/llm-settings` - Configure the model, temperature, top-p, and token settings for each pipeline.
- `/guide` - Open the guide menu for chapter, story, and author guides.
- `/persona` - Assign saved prompts to the author/editor/reviewer/control roles.
- `/chapters` - Open chapter management.
- `/rewrite [chapter] [instructions]` - Rewrite an existing chapter.
- `/style` - Show the current writing style.
- `/lore` - Manage lorebook entries.
- `/import-lorebook <file>` - Import lorebook data from JSON.
- `/import-nai <file>` - Import a Novel AI story export.
- `/pitch` - Open the pitch writer.
- `/author` - Open the author guide / Richard Treadwell menu.
- `/test` - Run the development test hook.
- `/help` - Show the current command list.
- `/exit` - Quit the app.

### Character chat workflow

The character subshell has its own commands:

- `character <name>`, `use <name>`, or `set <name>` - Select a character from `Prompts.promptMap`.
- `list` - Show available character names.
- `story` - Rebuild the active character pipeline with story context enabled.
- `clear` - Clear that character’s conversation history.
- `help` - Show the subshell help.
- `back` / `exit` - Leave the subshell.

### Token counting workflow

The token counting subshell helps inspect story size and tokenizer behavior without changing your runtime state.

- `chapter <index>` - Count tokens for a single chapter.
- `chapters <start>-<end>` - Count a chapter range and print per-chapter counts.
- `lorebook [key]` - Count all lorebook entries or one specific key.
- `context [text]` - Count the active story context, or custom text if you provide it.
- `model <name|number>` - Switch the tokenizer model used for counting.
- `models` - Show the available tokenizer models.
- `settings` - Show the current token-counting model and tokenizer settings.

Important: select a character first before using `story`. The command uses the active character prompt and story-aware pipeline builder, so it cannot work without an active character.

## Settings and Guides

### General settings
`/settings` updates the persistent `TPipeSettings` record and immediately rehydrates the runtime. It covers:

- writing style
- max tokens
- auto-lorebook behavior
- active persona assignments

### LLM settings
`/llm-settings` is the per-pipeline model manager. It lets you inspect and update each pipeline’s model, temperature, top-p, and max token values, and it supports export/import/reset/bulk workflows.

### Guides and personas
`/guide` manages chapter guide, story guide, and author guide files. Those guides are persisted under the user home directory and also reloaded into runtime context when applied.

`/persona` maps saved prompt entries onto the active writer roles:

- Author
- Editor
- Richard Treadwell
- Writing Control

## Save, Export, and Load

TPipeWriter has two different persistence paths:

- `/save` writes the live `ContextBank` pages to `~/.TPipeWriter/MainStory.json`, `Summary.json`, and `Chat.json`.
- `/export` writes a story package to `~/TPipeWriter/` containing the story text, story metadata, lorebook JSON, and settings JSON.
- `/load` restores that exported package back into memory.

This split means `/save` is best for preserving the current runtime state, while `/export` and `/load` are better for moving a story between sessions.

## Pipeline Overview

The startup environment builds a set of resident pipelines, including:

- writer
- idea
- discussion/chat
- lorebook
- summarizer
- style
- NCC
- rewrite
- plus writer
- pitch

Some flows are rebuilt on demand instead of staying resident:

- character chat builds a fresh character pipeline when a character is selected
- `story` in the character subshell rebuilds that pipeline with story context enabled
- `/rewrite` can assemble a dedicated rewrite pipeline for the requested chapter

This is why prompt changes, persona changes, and guide changes are re-applied through the runtime settings path instead of waiting for a restart.

## Advanced Pipelines

- **PlusWriterPipeline**: Multi-stage writing pipeline with stronger planning and refinement.
- **ChapterRewritePipeline**: Rewrites existing chapters with a new style or instruction set.
- **ExpansionPipeline**: Expands short inputs into longer scenes.
- **DialogueConnector**: Handles dialogue-heavy interactions with strong voice consistency.
- **PitchSlideWriterPipeline**: Builds pitch-oriented content from story data.

## Project Structure

- `src/main/kotlin/Builders/` - Pipeline construction and helper utilities.
- `src/main/kotlin/Globals/Env.kt` - Shared environment, resident pipeline setup, and prompt/persona state.
- `src/main/kotlin/Shell/` - Interactive CLI entrypoints and subshells.
- `src/main/kotlin/Structs/` - Settings and data structures.
- `run.sh` - Launcher that finds a built jar and starts the CLI.
