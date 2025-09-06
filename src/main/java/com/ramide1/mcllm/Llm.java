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

    private String sendRequestToApi(String url, String instructions, String sender, String question, String apikey,
            String model, int maxTokens) {
        try {
            if (url.isEmpty())
                throw new Exception("Url is empty.");
            String messages = "{\"role\": \"user\",\"content\": \"" + question + "\"}";
            String history = getHistory(sender);
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
            saveHistory(sender, newHistory);
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
                        String response = sendRequestToApi(url, instructions, player.getName(),
                                question.toString(), apiKey,
                                model, maxTokens);
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(response));
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
                        String response = sendRequestToApi(url, instructions, "console",
                                question.toString(),
                                apiKey,
                                model, maxTokens);
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().info(response));
                    }
                }.runTaskAsynchronously(plugin);
            } else {
                plugin.getLogger().info(ChatColor.RED + "This command needs at least one argument.");
            }
        }
        return true;
    }

    private boolean saveHistory(String sender, String history) {
        try {
            plugin.dataConfig.set(sender, history);
            plugin.dataConfig.save(plugin.data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getHistory(String sender) {
        return plugin.dataConfig.contains(sender) ? plugin.dataConfig.getString(sender) : "";
    }
}
