# Agent Instructions - Minecraft LLM

## Build & Build
- **Build System:** Gradle (Kotlin DSL).
- **JDK:** Java 25.
- **Build Command:** `./gradlew build` (generates a shadow JAR in `build/libs/` containing all dependencies).
- **Dependencies:**
    - `com.openai:openai-java:4.0.0` (Official OpenAI SDK).
    - `org.xerial:sqlite-jdbc` (SQLite for history).
    - `io.papermc.paper:paper-api` (Paper/Folia API).

## Architecture Notes
- **Folia Support:** The plugin supports both Paper and Folia.
    - Uses `isFolia()` check in `Llm.java` to determine scheduling logic.
    - Asynchronous tasks are handled via `AsyncScheduler` (Folia) or `BukkitRunnable` (Paper).
- **Persistence:** History is stored in SQLite (`plugins/Minecraft LLM/history.db`) via `DatabaseManager`.
- **API Integration:** Uses the OpenAI Responses API.

## Conventions
- **Threading:** All API calls and Database I/O must remain asynchronous to avoid hanging the main server thread.
- **Scheduling:**
    - Folia: Use `GlobalRegionScheduler` or `player.getScheduler()`.
    - Paper: Use `Bukkit.getScheduler()`.
