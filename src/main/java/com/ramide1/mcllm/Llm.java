package com.ramide1.mcllm;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Llm implements CommandExecutor {
    private App plugin;

    public Llm(App plugin) {
        this.plugin = plugin;
    }

    private String sendRequestToGPTApi(String url, String instructions, String sender, String question, String apikey,
            String model, int maxTokens) {
        try {
            if (url.isEmpty())
                throw new Exception("Url is empty.");
            String messages = "{\"role\": \"user\",\"content\": \"" + question + "\"}";
            String history = getHistory(sender, false);
            if (!history.isEmpty()) {
                messages = history + "," + messages;
            }
            String newHistory = messages;
            if (!instructions.isEmpty()) {
                messages = "{\"role\": \"system\",\"content\": \"" + instructions + "\"}" + "," + messages;
            }
            messages = "[" + messages + "]";
            String data = "{\"model\": \"" + model + "\", \"messages\": " + messages + ", \"max_tokens\": " + maxTokens
                    + "}";
            HttpRequest request = new HttpRequest(url, "POST", "application/json", "Bearer " + apikey, data);
            request.sendRequest();
            boolean error = request.getError();
            String response = request.getResponse();
            if (error == true)
                throw new Exception(response);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response);
            String content = rootNode.path("choices").path(0).path("message").path("content").asText()
                    .replace("\\n", "").replace("\\", "").replace("\"", "").replace("{", "").replace("}", "");
            newHistory = newHistory + "," + "{\"role\": \"assistant\",\"content\": \"" + content + "\"}";
            saveHistory(sender, newHistory, false);
            return content;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String sendRequestToGeminiApi(String instructions, String sender, String question, String apikey,
            String model, int maxTokens) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key="
                    + apikey;
            String messages = "{\"role\": \"user\",\"parts\": [" + "{\"text\": \"" + question + "\"}" + "]}";
            String history = getHistory(sender, true);
            if (!history.isEmpty()) {
                messages = history + "," + messages;
            }
            String newHistory = messages;
            if (!instructions.isEmpty()) {
                messages = "{\"role\": \"user\",\"parts\": [" + "{\"text\": \"" + instructions + "\"}" + "]}" + ","
                        + "{\"role\": \"model\",\"parts\": [" + "{\"text\": \"Ok.\"}" + "]}" + "," + messages;
            }
            messages = "[" + messages + "]";
            String data = "{\"contents\": " + messages + ", \"generationConfig\": {\"maxOutputTokens\": " + maxTokens
                    + "}}";
            HttpRequest request = new HttpRequest(url, "POST", "application/json", "", data);
            request.sendRequest();
            boolean error = request.getError();
            String response = request.getResponse();
            if (error == true)
                throw new Exception(response);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response);
            String content = rootNode.path("candidates").path(0).path("content").path("parts").path(0).path("text")
                    .asText().replace("\\n", "").replace("\\", "").replace("\"", "").replace("{", "").replace("}", "");
            newHistory = newHistory + "," + "{\"role\": \"model\",\"parts\": [" + "{\"text\": \"" + content
                    + "\"}" + "]}";
            saveHistory(sender, newHistory, true);
            return content;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public boolean onCommand(CommandSender sender, Command llm, String label, String[] args) {
        String url = plugin.getConfig().getString("Config.url", "https://api.openai.com/v1/chat/completions");
        String instructions = plugin.getConfig().getString("Config.instructions",
                "You are a helpful assistant in Minecraft. Respond concisely and friendly.");
        String apiKey = plugin.getConfig().getString("Config.apikey", "");
        String model = plugin.getConfig().getString("Config.model", "gpt-4o-mini");
        boolean googleApi = plugin.getConfig().getBoolean("Config.googleapi", false);
        int maxTokens = plugin.getConfig().getInt("Config.maxtokens", 800);
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length >= 1) {
                StringBuilder question = new StringBuilder();
                for (String arg : args) {
                    question.append(arg).append(" ");
                }
                question.deleteCharAt(question.length() - 1);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (googleApi) {
                            String response = sendRequestToGeminiApi(instructions, player.getName(),
                                    question.toString(),
                                    apiKey, model, maxTokens);
                            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(response));
                        } else {
                            String response = sendRequestToGPTApi(url, instructions, player.getName(),
                                    question.toString(), apiKey,
                                    model, maxTokens);
                            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(response));
                        }
                    }
                }.runTaskAsynchronously(plugin);
            } else {
                player.sendMessage(ChatColor.RED + "This command needs at least one argument.");
            }
        } else {
            if (args.length >= 1) {
                StringBuilder question = new StringBuilder();
                for (String arg : args) {
                    question.append(arg).append(" ");
                }
                question.deleteCharAt(question.length() - 1);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (googleApi) {
                            String response = sendRequestToGeminiApi(instructions, "console", question.toString(),
                                    apiKey, model, maxTokens);
                            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().info(response));
                        } else {
                            String response = sendRequestToGPTApi(url, instructions, "console",
                                    question.toString(),
                                    apiKey,
                                    model, maxTokens);
                            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().info(response));
                        }
                    }
                }.runTaskAsynchronously(plugin);
            } else {
                plugin.getLogger().info(ChatColor.RED + "This command needs at least one argument.");
            }
        }
        return true;
    }

    private boolean saveHistory(String sender, String history, boolean googleApi) {
        try {
            plugin.dataConfig.set((googleApi ? "google." : "gpt.") + sender, history);
            plugin.dataConfig.save(plugin.data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getHistory(String sender, boolean googleApi) {
        return plugin.dataConfig.contains((googleApi ? "google." : "gpt.") + sender)
                ? plugin.dataConfig.getString((googleApi ? "google." : "gpt.") + sender)
                : "";
    }
}
