# Minecraft LLM (Paper/Folia)

A plugin for Minecraft servers (Paper/Folia) that lets you chat with AI directly in the game using the OpenAI API.

## Features

- `/llm <question>` command to ask the AI
- `/llmreload` command to reload configuration
- Conversation history saved in SQLite
- Configurable (API key, base URL, model, max tokens, instructions)
- Compatible with Paper and Folia

## Installation

1. Download the `.jar` file from the actions section
2. Place the `.jar` file in the `plugins/` folder of your Paper server
3. Restart the server to load the plugin

## Configuration

The configuration file is located at `plugins/MinecraftLLM/config.yml`:

```yml
Config:
  instructions: "You are a helpful assistant in Minecraft. Respond concisely and friendly."
  apiKey: "your-api-key-here"
  baseUrl: ""
  model: "gpt-4o-mini"
  maxTokens: 800
```

### Parameters

- `instructions`: System instructions for the AI
- `apiKey`: Your OpenAI API key
- `baseUrl`: API base URL (empty = official OpenAI, e.g. `http://localhost:11434/v1` for Ollama)
- `model`: AI model to use (default: gpt-4o-mini)
- `maxTokens`: Maximum number of tokens in the response

## Commands

- `/llm <question>` - Ask a question to the AI
- `/llmreload` - Reload configuration

## Dependencies

- [Paper](https://papermc.io/) or [Folia](https://papermc.io/software/folia)
- [Jackson Databind](https://github.com/FasterXML/jackson-databind) 2.21.4
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) 3.45.1.0

## Building

```bash
./gradlew build
```

The JAR will be generated in `build/libs/`

## License

MIT License - See [LICENSE](LICENSE) for details.
