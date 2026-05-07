/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
# Anahata ASI Gemini Provider (`anahata-asi-gemini`)

> [!IMPORTANT]
> This file is an extension of the `anahata.md` in the parent project. Always keep the root `anahata.md` in context as it contains the master Coding Principles and Javadoc Standards.

This module provides the adapter implementation for the Google Gemini API.

## 1. Purpose

This module's sole responsibility is to act as an **Adapter** between the Google Gemini API and the core `anahata-asi` framework.

## 2. Key Components

-   **`GeminiAgiProvider`:** The concrete implementation of `AbstractAgiProvider`.

-   **`GeminiModel`:** A wrapper around the native Google GenAI `GenerativeModel`. Implements `generateContent`.

-   **`adapter` Package:** Logic for translating between core domain models and native Gemini API types.

## 3. CLI Test Harness

-   **`GeminiCliAgiConfig.java`:** Registers the `GeminiAgiProvider` for CLI usage.

-   **`CliMain.java` (in Standalone)**: Launcher for the core CLI with this provider.


## tasks
1 in gemini, hosted call executable code parts and their responses have an id, when streaming, we  need to see what the chunks look like and how to append them to the previous chunk or whether it is already appending because they subclass text part
2 when replaying history, we need to see what to do with ModelWebSearchCall from openai because gemini doesn't seem to ha veone?
3 see what the deal is with grounding metadata when streaming because according to google ai studio, 