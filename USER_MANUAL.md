# TPipeWriter User Manual

Welcome to the **TPipeWriter** User Manual. This guide is designed to help you master the art of AI-assisted storytelling using our powerful, pipeline-driven creative writing environment. Whether you are a novelist, a world-builder, or a hobbyist, TPipeWriter provides the tools to bring your vision to life.

---

## Table of Contents

1.  [Getting Started](#getting-started)
    *   [System Requirements](#1-system-requirements)
    *   [AWS Bedrock Setup](#2-aws-bedrock-setup)
    *   [Launching the Application](#3-launching-the-application)
2.  [Core Concepts](#core-concepts)
    *   [The Subshell Architecture](#1-the-subshell-architecture)
    *   [Advanced Mode (The "//" Prefix)](#2-advanced-mode-the--prefix)
3.  [Feature Guide](#feature-guide)
    *   [The Writer Subshell (`/write`)](#1-the-writer-subshell-write)
    *   [The Idea Lab (`/idea`)](#2-the-idea-lab-idea)
    *   [The Lorebook Subshell (`/lore`)](#3-the-lorebook-subshell-lore)
    *   [Character Interaction (`/character`)](#4-character-interaction-character)
    *   [The Pitch Writer (`/pitch`)](#5-the-pitch-writer-pitch)
    *   [Chapter Management (`/chapters`)](#6-chapter-management-chapters)
    *   [Summarization (`/summary`)](#7-summarization-summary)
    *   [The Chat & Discussion Subshell (`/chat`)](#8-the-chat--discussion-subshell-chat)
    *   [Personas and Roles (`/persona`)](#9-personas-and-roles-persona)
    *   [Settings (`/settings` & `/llm-settings`)](#10-the-settings-subshell-settings--llm-settings)
4.  [Troubleshooting](#troubleshooting)
5.  [Common Questions](#common-questions)

---

## Getting Started

Welcome to TPipeWriter! This section will guide you through the initial setup required to start your AI-assisted writing journey.

### 1. System Requirements
To run TPipeWriter, you need the **Java Runtime Environment (JRE) version 17 or higher**. 
*   **How to check:** Open your terminal or command prompt and type `java -version`.
*   **How to install:** If you don't have it, we recommend downloading the "LTS" (Long Term Support) version from [Adoptium (OpenJDK)](https://adoptium.net/).

### 2. AWS Bedrock Setup
TPipeWriter uses **AWS Bedrock** to provide its "brain." Unlike other tools that use a simple API key, Bedrock requires an AWS account and specific permissions to ensure high-quality, secure AI generation.

#### Step A: Enable Model Invocation
You do not need to request individual access or wait for approval. You simply need to enable the models in your AWS account.
1.  Log in to your [AWS Management Console](https://console.aws.amazon.com/).
2.  Search for **"Amazon Bedrock"** in the top search bar.
3.  In the left-hand menu, scroll down to **"Model access"**.
4.  Click **"Manage model access"** and check the boxes for the models you wish to use. We recommend enabling:
    *   **DeepSeek R1** (Excellent for complex reasoning and high-quality prose)
    *   **Amazon Nova Pro** (Fast and reliable for general writing and brainstorming)
5.  Click **"Save changes"**. The models are now enabled for your account.

#### Step B: Create IAM Credentials
You need an "Access Key" and "Secret Key" to allow TPipeWriter to communicate with AWS.
1.  Go to the **IAM (Identity and Access Management)** service in the AWS Console.
2.  Create a new user (e.g., "TPipeUser") and attach the `AmazonBedrockFullAccess` policy.
3.  In the "Security credentials" tab for that user, create an **Access Key**.
4.  Save the **Access Key ID** and **Secret Access Key** safely. **Do not share these keys.**

#### Step C: Configure Your Computer
Create a folder named `.aws` in your user home directory (e.g., `C:\Users\YourName\.aws` or `~/.aws/`) and create a file inside it named `credentials` (with no file extension). Paste the following, replacing the placeholders with your keys:

```text
[default]
aws_access_key_id = YOUR_ACCESS_KEY_ID
aws_secret_access_key = YOUR_SECRET_ACCESS_KEY
region = us-east-1
```
*(Note: You can also use `us-west-2` or `us-east-2` depending on where you enabled your models.)*

### 3. Launching the Application
Once your Java and AWS settings are ready, you can start TPipeWriter:
1.  Open your terminal or command prompt.
2.  Navigate to the folder where you installed TPipeWriter.
3.  Run the launch command:
    *   **Mac/Linux:** `./run.sh`
    *   **Windows:** `./gradlew run`

---

## Core Concepts

Understanding how TPipeWriter "thinks" will help you get the most out of the tool.

### 1. The Subshell Architecture
TPipeWriter isn't just one big chat window. It is organized into **Subshells**—specialized environments designed for specific tasks. Think of them like different rooms in a writer's studio:

*   **Writer Subshell (`/write`):** This is where you generate the actual prose of your story.
*   **Idea Lab (`/idea`):** A creative space for brainstorming plot twists, character backgrounds, and world-building without cluttering your main story.
*   **Lorebook Subshell (`/lore`):** Your story's encyclopedia. It stores facts about your world so the AI doesn't forget that your protagonist has blue eyes or that the magic system requires silver.
*   **Character Chat (`/character`):** A space to converse directly with specific personas or characters.
*   **Chapter Management (`/chapters`):** A dedicated area to organize, edit, and review your story's structure.

You can switch between these rooms at any time using slash commands. For example, type `/idea` to start brainstorming, then `/write` to go back to your story.

### 2. Advanced Mode (The "//" Prefix)
For most tasks, the standard Writer is perfect. However, sometimes you need "Deeper Control." By prefixing your command with two slashes (`//`), you enter the **Advanced Writer**.

*   **How to enter:** Type `//` or `/write //` in the shell.
*   **What it does:** The Advanced Writer gives you granular control over the AI's "memory." You can manually select which previous chapters the AI should read before writing, adjust how much context is "truncated" (cut off), and use more powerful multi-model pipelines that reason through the scene before writing a single word.

Use Advanced Mode when you are at a critical turning point in your story and need the AI to be extra precise.

---

## Feature Guide

### 1. The Writer Subshell (`/write`)

The Writer subshell is the heart of TPipeWriter. It uses a sophisticated pipeline to generate prose that respects your style and story context.

*   **Commands:** 
    *   `/write <prompt>`: Start a new scene.
    *   `/write continue`: Have the AI pick up exactly where it left off.
*   **Pipeline Strength (`level`):** Within the subshell, use the `level [low|med]` command to adjust the pipeline's strength. `low` is faster for simple continuations, while `med` (the default) uses deeper reasoning for complex scenes.
*   **Advanced Context Selection (`context`):** When in the Advanced Writer Subshell, the `context` command opens a menu to precisely control what the AI "remembers":
    *   **Chapter Strategies:**
        1.  **Last 8K tokens:** Automatically selects the most recent text up to 8,000 tokens.
        2.  **Range of chapters:** Specify a start and end chapter (e.g., chapters 3 to 7).
        3.  **Specific chapters:** Select individual chapters by number (e.g., 1, 4, 9).
        4.  **All available context:** Loads the entire story (useful for shorter works or models with massive context windows).
    *   **Lorebook Strategies:**
        1.  **Match by prompt:** Only pulls lore entries relevant to your current request.
        2.  **Match by chapters:** Only pulls lore entries relevant to the selected chapters.
        3.  **Match by prompt and chapters:** Combines both methods for maximum relevance (Default).
        4.  **Individual selection:** Manually type the names of the lore keys you want to include.

### 2. The Idea Lab (`/idea`)

The Idea Lab is your creative partner for brainstorming plots, characters, and world-building. Ideas generated here are "banked" and can be automatically applied to your next `/write` command.

*   **Entering the Lab:** Type `/idea` to open the management menu, or `/idea <prompt>` to brainstorm immediately.
*   **Settings (`settings`):** Configure how the Idea Lab thinks:
    *   **Chapters Lookback:** Determines how many recent chapters the AI reads before brainstorming.
    *   **Lorebook Token Budget:** Allocates a specific amount of memory for lore entries during ideation.
*   **Forced Context (`forced`):** Ensure the AI remembers specific earlier chapters during brainstorming, even if they are outside the lookback range.
    *   `forced add <chapter_number>`: Add a chapter to the forced context list.
    *   `forced remove <chapter_number>`: Remove a chapter.
    *   `forced list`: View currently forced chapters.
    *   `forced clear`: Empty the forced context list.

### 3. The Lorebook Subshell (`/lore`)

The Lorebook is your story's encyclopedia, ensuring the AI maintains consistency across names, locations, and lore.

*   **Entering the Subshell:** Type `/lore` to access the management menu.
*   **Core Commands:**
    *   `list`: Show all lorebook keys and their weights.
    *   `view <keyname>`: Display the content of a specific entry.
    *   `edit <keyname>`: Modify an existing entry.
    *   `add <keyname>`: Create a new entry.
    *   `delete <keyname>`: Remove an entry.
    *   `search <term>`: Find specific text within your lorebook.
*   **Advanced Commands:**
    *   `weight <keyname> <value>`: Set the importance of an entry from 0 to 100. Higher weights influence the AI to pay more attention to this fact when generating text.
    *   `link <key> <linked_keys...>`: Connect related entries so that when one is recalled, the others are too.
    *   `unlink <key> <keys...>`: Break connections between entries.
    *   `alias <key> <aliases...>`: Add alternative names for an entry (e.g., alias "The Dark Lord" to "Sauron").
    *   `unalias <key> <aliases...>`: Remove alternative names.

### 4. Character Interaction (`/character`)

The Character subshell allows you to converse directly with predefined AI personas or characters from your story.

*   **Entering the Subshell:** Type `/character` to open the chat interface.
*   **Commands:**
    *   `list`: Show all available character personas.
    *   `character <name>` (or `use <name>`, `set <name>`): Select a persona to chat with.
    *   `story`: Enable story context for the active character, allowing them to reference your current manuscript.
    *   `clear`: Erase the conversation history for the active character.
*   **Example Personas:** TPipeWriter includes a diverse cast of built-in personas, such as:
    *   **Bigwang:** A smug, overconfident tech startup CEO.
    *   **Talya:** The last daughter of a premodern dynasty, writing with empathetic, archaic prose.
    *   **nzg:** An empathetic, cnidarian-like alien who despises oligarchs and politicians.
    *   **ivd (Invis von Disappearo):** An experimental, unhelpful author who refuses to explain anything and leaves threads unresolved.

### 5. The Pitch Writer (`/pitch`)

Need to sell your story idea? The Pitch Writer is a specialized pipeline designed to generate compelling pitches, slide decks, or summaries of your core concepts.

*   **Usage:** Type `/pitch` and enter your prompt. The AI will process your request and output a structured, persuasive pitch document.

### 6. Chapter Management (`/chapters`)

The Chapters subshell provides a dedicated environment for organizing and reviewing your manuscript.

*   **Entering the Subshell:** Type `/chapters` to access the management menu.
*   **Commands:**
    *   `list`: Display all chapters with their word counts, last modified dates, and a brief preview.
    *   `show <index>`: Read the full text of a specific chapter.
    *   `edit <index>`: Modify the text of a chapter.
    *   `title <index> <new_title>`: Rename a chapter.
    *   `move <old_index> <new_index>`: Reorder your chapters.
    *   `insert <index>`: Add a new chapter at a specific position.
    *   `add`: Append a new chapter to the end of the story.
    *   `delete <index>`: Remove a chapter.
    *   `search <query>`: Find specific text across all chapters.
    *   `stats`: View overall manuscript statistics (total chapters, total words).
    *   `export`: Save your chapters to external files.

### 7. Summarization (`/summary`)

The Summary command quickly condenses parts of your story, which is useful for reviewing progress or generating synopses.

*   **Usage:** Type `/summary <option>`
*   **Options:**
    *   `last`: Summarize only the most recent chapter/element.
    *   `all`: Summarize the entire story sequentially.
    *   `1-3` (Range): Summarize a specific span of chapters (e.g., chapters 1 through 3).
    *   `5` (Index): Summarize a single specific chapter.
    *   `[custom text]`: Provide a custom prompt to guide the summarization (e.g., `/summary Focus only on the romantic subplot`).

### 8. The Chat & Discussion Subshell (`/chat`)

Use `/chat` to talk *about* your story. Ask questions about plot holes, character motivations, or thematic consistency without affecting your story text.

### 9. Personas and Roles (`/persona`)

TPipeWriter allows you to switch the "voice" of the AI for different tasks using the `/persona` command.

*   **Author:** The primary voice used for generating story prose.
*   **Editor:** The voice used for rewriting and expanding existing text.
*   **Richard Treadwell:** A specialized, reasoning-enabled persona designed for deep creative consulting and architectural critique.
*   **Writing Control:** An internal persona that manages the logic of the writing process.

To change a persona, type `/persona` and select the role you wish to modify. You can then choose from a list of available AI personalities.

### 10. The Settings Subshell (`/settings` & `/llm-settings`)

*   **General Settings (`/settings`):** Adjust **Writing Style**, **Max Tokens**, and toggle **Auto-Lorebook**.
*   **LLM Configuration (`/llm-settings`):** Advanced users can swap models (e.g., switching from DeepSeek to Nova) and adjust **Temperature** for more or less creative "drift."

---

## Troubleshooting

If you encounter issues, check the following common solutions:

| Issue | Potential Solution |
| :--- | :--- |
| **Model Access Denied** | Ensure you have enabled the specific model in the AWS Bedrock console. |
| **Missing Credentials** | Verify that your `~/.aws/credentials` file exists and is correctly formatted. |
| **Region Mismatch** | Ensure your AWS account is active in `us-east-2` or `us-west-2`. |
| **AI Stops Mid-Sentence** | You may have hit the `Max Tokens` limit. Increase this value in the `/settings` menu. |
| **Java Errors** | Ensure you are running Java 17 or higher (`java -version`). |
| **Connection Timeout** | Check your internet connection and ensure your AWS keys have the necessary permissions for Bedrock. |

---

## Common Questions

**Q: Does TPipeWriter store my data in the cloud?**
A: Your story text and lorebook are stored locally on your machine. However, the text you send to the AI is processed by AWS Bedrock.

**Q: Can I use my own models?**
A: TPipeWriter is designed to work with AWS Bedrock. If you are an advanced developer, you can modify the `BedrockPipe` configuration to point to other compatible endpoints.

**Q: How do I backup my story?**
A: Use the `/save` command to ensure your current context is written to disk. You can also manually copy the story files from the project directory.

---

*Happy Writing! We can't wait to see what you create.*