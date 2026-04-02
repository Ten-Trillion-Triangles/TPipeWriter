# TPipeWriter

TPipeWriter is a sophisticated creative writing assistant built on the **TPipe** framework. It leverages multi-agent pipelines to orchestrate complex writing workflows, managing long-term story context, lore consistency, and creative brainstorming through AWS Bedrock.

## Core Concepts

TPipeWriter is powered by the TPipe library, which introduces several key architectural patterns:

- **Pipe**: The fundamental unit of execution. A Pipe performs a specific task, such as calling an LLM, transforming text, or updating a database.
- **Pipeline**: An orchestrated sequence of Pipes. Pipelines manage the flow of data (`MultimodalContent`) and context between individual units of work.
- **ContextBank**: A centralized management system for different "pages" of context (e.g., the main story, summaries, or chat history). It allows agents to maintain state across long horizons.

## Features & Demonstrations

### Long Horizon Agent Workflows
TPipeWriter is designed for writing full-length novels. Its pipelines automatically handle:
- **Context Management**: Intelligently truncating and selecting relevant story parts to fit within model context windows (up to 128k+ tokens).
- **Lore Consistency**: A dedicated Lorebook pipe scans new writing to extract and update character details, locations, and plot points, ensuring the agent "remembers" the world it's building.
- **Multi-Stage Processing**: The writer pipeline doesn't just generate text; it cleans up model reasoning, removes unwanted formatting, and updates the global state in a single execution.

### Powerful Agent Steering
The project demonstrates how specific "Author" and "Guide" prompts can drastically alter the voice and behavior of the writing agent. By switching between different author personas (e.g., the empathetic N'zelquin or the eroticizing Xilaron), users can steer the creative direction with high precision.

### Pipeline-Level Refusal Handling
TPipeWriter implements logic to handle common LLM refusals (censorship). If a model refuses a prompt based on safety guidelines, the pipeline can detect the failure and automatically branch to a different model (e.g., DeepSeek) to fulfill the request, ensuring the creative process remains uninterrupted.

## Setup & Build

### Prerequisites
- **TPipe Library**: This project depends on the TPipe framework. Ensure the `TPipe` repository is cloned at `../TPipe` relative to this directory.
- **AWS Credentials**: You must have an AWS account with access to Bedrock models. A `[default]` profile must be configured in `~/.aws/credentials`.

### Configuration
**Important**: You MUST configure your own AWS Bedrock inference profiles or foundation model ARNs.
1. Open `src/main/kotlin/Globals/Env.kt`.
2. Locate the `bedrockEnv.bindInferenceProfile` calls in the `init` function.
3. Replace the placeholder ARNs with your own Bedrock Inference Profile ARNs or specific model ARNs.

### Building
Build the project using Gradle:
```bash
./gradlew shadowJar
```
This will generate a runnable JAR in `build/libs/`.

## Interactive CLI

Run the application to enter the interactive shell. You can use the following slash commands to interact with different agent pipelines:

- `/write [prompt]`: Generate the next part of your story. Use `continue` to let the agent decide the next beat.
- `/idea [prompt]`: Brainstorm new plot points, characters, or world-building elements.
- `/lorebook [instruction]`: Manually update the lorebook or ask the agent to extract lore from the recent text.
- `/chat [question]`: Discuss your story with an agent. Ask about themes, character motivations, or "what-if" scenarios.
- `/summary [options]`: Generate summaries of specific chapters or the entire story to maintain a high-level overview.
- `/settings`: Configure generation parameters like temperature, topP, and writing style.
- `/save`: Save the current story and lorebook context to `~/.TPipeWriter/`.

## Advanced Pipelines

TPipeWriter includes several specialized pipelines for different writing tasks:

- **PlusWriterPipeline**: A more powerful writing pipeline that uses multiple models and reasoning steps to produce higher-quality prose.
- **ChapterRewritePipeline**: Specifically designed to take an existing chapter and rewrite it based on new instructions or style changes.
- **ExpansionPipeline**: Focuses on taking a brief outline or a short segment and expanding it into a full, detailed scene.
- **DialogueConnector**: A specialized component for handling character dialogue with high consistency and voice adherence.
- **PitchSlideWriterPipeline**: An experimental pipeline for generating pitch materials or slide content based on story data.

## Project Structure

- `src/main/kotlin/Builders/`: Contains the logic for constructing complex pipelines.
- `src/main/kotlin/Globals/Env.kt`: The central environment configuration where models and system prompts are defined.
- `src/main/kotlin/Shell/`: Implementation of the interactive CLI and subshells.
- `src/main/kotlin/Structs/`: Data classes for settings, lore, and story metadata.
