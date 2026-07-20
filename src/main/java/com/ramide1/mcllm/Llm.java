package com.ramide1.mcllm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Llm implements CommandExecutor {
    private static final Set<String> VALID_REASONING_EFFORTS = Set.of("", "none", "low", "medium", "high");
    private static final Pattern UNSANITIZED = Pattern.compile("[^a-zA-Z0-9_\\-]");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final App plugin;

    public Llm(App plugin) {
        this.plugin = plugin;
    }

    private String sendRequestToApi(String instructions, String senderName, String question, String apiKey,
            String baseUrl, String model, int maxTokens, boolean isPlayer,
            boolean hideReasoning, String reasoningEffort) {
        try {
            List<DatabaseManager.ChatMessage> history = plugin.dbManager.getHistory(senderName);

            String baseUrlToUse = baseUrl;
            if (baseUrlToUse == null || baseUrlToUse.isEmpty()) {
                baseUrlToUse = "https://api.openai.com/v1/chat/completions";
            } else {
                baseUrlToUse = baseUrlToUse.replaceAll("/+$", "");
                if (!baseUrlToUse.endsWith("/chat/completions")) {
                    baseUrlToUse += "/chat/completions";
                }
            }

            ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
            requestBody.put("model", model);

            boolean hasReasoning = reasoningEffort != null && !reasoningEffort.isEmpty();
            if (hasReasoning) {
                requestBody.put("max_completion_tokens", maxTokens);
                requestBody.put("reasoning_effort", reasoningEffort);
            } else {
                requestBody.put("max_tokens", maxTokens);
            }

            var messages = OBJECT_MAPPER.createArrayNode();
            if (!instructions.isEmpty()) {
                var systemMsg = OBJECT_MAPPER.createObjectNode();
                systemMsg.put("role", "system");
                systemMsg.put("content", instructions);
                messages.add(systemMsg);
            }
            for (DatabaseManager.ChatMessage msg : history) {
                var message = OBJECT_MAPPER.createObjectNode();
                message.put("role", msg.getRole());
                message.put("content", msg.getContent());
                messages.add(message);
            }
            var userMsg = OBJECT_MAPPER.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", question);
            messages.add(userMsg);
            requestBody.set("messages", messages);

            String jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrlToUse))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                plugin.getLogger()
                        .warning("OpenAI API returned status " + response.statusCode() + ": " + response.body());
                return "Error: API returned status " + response.statusCode();
            }

            JsonNode responseJson = OBJECT_MAPPER.readTree(response.body());
            JsonNode choices = responseJson.get("choices");
            if (choices == null || choices.isEmpty()) {
                plugin.getLogger().warning("No choices in response: " + response.body());
                return "Error: No choices in response";
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message == null) {
                plugin.getLogger().warning("No message in choice: " + response.body());
                return "Error: Invalid API response format";
            }
            JsonNode contentNode = message.get("content");

            String text;
            if (contentNode != null && contentNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : contentNode) {
                    String type = block.has("type") ? block.get("type").asText() : "";
                    if (type.equals("reasoning")) {
                        if (!hideReasoning) {
                            String reasoning = block.has("reasoning") ? block.get("reasoning").asText() : "";
                            if (!reasoning.isEmpty()) {
                                sb.append(reasoning).append("\n\n");
                            }
                        }
                    } else if (type.equals("text")) {
                        String t = block.has("text") ? block.get("text").asText() : "";
                        sb.append(t);
                    }
                }
                text = sb.toString().trim();
            } else if (contentNode != null) {
                text = contentNode.asText();
            } else {
                text = "";
            }

            if (!text.isEmpty()) {
                plugin.dbManager.saveMessage(senderName, "user", question);
                plugin.dbManager.saveMessage(senderName, "assistant", text);
            }

            return text.isEmpty() ? "No response from model." : text;
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Error calling OpenAI API: " + e.getMessage());
            Thread.currentThread().interrupt();
            return "Error: Could not reach AI service.";
        } catch (IOException e) {
            plugin.getLogger().warning("Error calling OpenAI API: " + e.getMessage());
            return "Error: Could not reach AI service.";
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing OpenAI API response: " + e.getMessage());
            return "Error: Failed to process AI response.";
        }
    }

    private void processResponse(String response, CommandSender sender, String senderName, String question,
            boolean isPlayer) {
        LlmEvent llmEvent = new LlmEvent(senderName, !isPlayer, question, response);

        Runnable task = () -> {
            Bukkit.getPluginManager().callEvent(llmEvent);

            if (!llmEvent.isCancelled()) {
                String finalResponse = llmEvent.getResponse();
                List<String> chunks = splitMessage(finalResponse, 200);
                if (isPlayer) {
                    Player player = (Player) sender;
                    if (!player.isOnline())
                        return;
                    for (String chunk : chunks) {
                        player.sendMessage(Component.text(chunk));
                    }
                } else {
                    for (String chunk : chunks) {
                        plugin.getLogger().info(chunk);
                    }
                }
            }
        };

        if (isFolia()) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isPlayer = sender instanceof Player;
        if (args.length < 1) {
            if (isPlayer) {
                ((Player) sender).sendMessage(
                        Component.text("This command needs at least one argument.").color(NamedTextColor.RED));
            } else {
                plugin.getLogger().info("This command needs at least one argument.");
            }
            return true;
        }

        var config = plugin.getConfig();
        String instructions = config.getString("Config.instructions",
                "You are a helpful assistant in Minecraft. Respond concisely and friendly.");
        String apiKey = config.getString("Config.apiKey", "");
        String baseUrl = config.getString("Config.baseUrl", "");
        String model = config.getString("Config.model", "gpt-4o-mini");
        int maxTokens = Math.max(1, Math.min(128000, config.getInt("Config.maxTokens", 800)));
        boolean hideReasoning = config.getBoolean("Config.hideReasoning", false);
        String reasoningEffortRaw = config.getString("Config.reasoningEffort", "");
        final String reasoningEffort = VALID_REASONING_EFFORTS.contains(reasoningEffortRaw) ? reasoningEffortRaw : "";

        String question = String.join(" ", args);
        String senderName = isPlayer
                ? UNSANITIZED.matcher(((Player) sender).getName()).replaceAll("_")
                : "console";

        if (isFolia()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                String response = sendRequestToApi(instructions, senderName, question, apiKey, baseUrl, model,
                        maxTokens, isPlayer, hideReasoning, reasoningEffort);
                processResponse(response, sender, senderName, question, isPlayer);
            });
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    String response = sendRequestToApi(instructions, senderName, question, apiKey, baseUrl, model,
                            maxTokens, isPlayer, hideReasoning, reasoningEffort);
                    processResponse(response, sender, senderName, question, isPlayer);
                }
            }.runTaskAsynchronously(plugin);
        }
        return true;
    }

    static List<String> splitMessage(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        if (text.length() <= maxLength) {
            chunks.add(text);
            return chunks;
        }

        String[] paragraphs = text.split("\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() + 1 > maxLength && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }

            if (paragraph.length() > maxLength) {
                String[] sentences = paragraph.split("(?<=[.!?])\\s+");
                for (String sentence : sentences) {
                    if (sentence.length() > maxLength) {
                        if (current.length() > 0) {
                            chunks.add(current.toString().trim());
                            current = new StringBuilder();
                        }
                        for (int i = 0; i < sentence.length(); i += maxLength) {
                            chunks.add(sentence.substring(i, Math.min(i + maxLength, sentence.length())));
                        }
                    } else {
                        if (current.length() + sentence.length() + 1 > maxLength && current.length() > 0) {
                            chunks.add(current.toString().trim());
                            current = new StringBuilder();
                        }
                        if (current.length() > 0) {
                            current.append(" ");
                        }
                        current.append(sentence);
                    }
                }
            } else {
                if (current.length() > 0) {
                    current.append("\n");
                }
                current.append(paragraph);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}