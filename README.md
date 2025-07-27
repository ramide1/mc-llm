# McLLM - Minecraft LLM Plugin
McLLM is a plugin for Minecraft servers (Spigot) that allows users to interact with large language models (LLMs) directly in the game. With this plugin, players can use commands like /llm "query" to get AI-generated responses by leveraging APIs such as OpenAI.
# Key Features
LLM Interaction: Use in-game commands to query advanced language models.
Flexible Configuration: Customize the API, model, and initial instructions via a configuration file.
Multi-API Support: Compatible with APIs like OpenAI.
Easy to Use: Simple installation and quick setup.
# Installation
Download the .jar file from the actions section.
Place the .jar file in the plugins folder of your Spigot server.
Restart the server to load the plugin.
# Configuration
The plugin is configured using the config.yml file, which is automatically generated the first time the plugin runs. Below is an example configuration:
```yml
Config:
  url: "https://api.openai.com/v1/chat/completions" # URL of the LLM API endpoint
  instructions: "You are a helpful assistant in Minecraft. Respond concisely and friendly."  # Initial instructions for the model
  apikey: "your_api_key_here" # API key for authentication
  model: "gpt-4o-mini" # Language model to use (e.g., gpt-4, gpt-3.5-turbo, etc.)
  maxtokens: 800 # Max output tokens
```
# Explanation of Fields:
url: The URL of the language model API endpoint.
instructions: Initial instructions sent to the model to define its behavior.
apikey: Your API key for authenticating with the LLM service.
model: The language model you want to use (e.g., gpt-4, gpt-3.5-turbo, etc.).
# Usage
Once the plugin is configured, players can use the following command in the game:
/llm "your query here"
For example:
/llm "How do I build an automatic farm in Minecraft?"
The plugin will send the query to the configured language model and return the response in the game chat.
# Contributing
Contributions are welcome! If you have ideas to improve the plugin, find bugs, or want to add new features, feel free to contribute. You can do so in the following ways:
Report Issues: Open an issue on GitHub to report bugs or suggest improvements.
Submit Pull Requests: If you've implemented a feature or fix, submit a pull request for review.
# License
This project is licensed under the MIT License. Feel free to use, modify, and distribute it according to the license terms.