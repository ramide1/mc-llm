package com.ramide1.mcllm;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Llm implements CommandExecutor {
    private App plugin;

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public Llm(App plugin) {
        this.plugin = plugin;
    }

    private String sendRequestToApi(String url, String instructions, String sender, String question, String apikey,
            String model, int maxTokens, boolean isPlayer) {
        try {
            if (url.isEmpty())
                throw new Exception("Url is empty.");
            String messages = "{\"role\": \"user\",\"content\": \"" + question + "\"}";
            String history = getHistory(sender, isPlayer);
            if (!history.isEmpty())
                messages = history + "," + messages;
            String newHistory = messages;
            if (!instructions.isEmpty())
                messages = "{\"role\": \"system\",\"content\": \"" + instructions + "\"}" + "," + messages;
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
            saveHistory(sender, newHistory, isPlayer);
            return content;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private void processResponse(String response, CommandSender sender, String senderName, String question,
            boolean isPlayer) {
        LlmEvent llmEvent = new LlmEvent(senderName, !isPlayer, question, response);
        Runnable callEvent = () -> Bukkit.getPluginManager().callEvent(llmEvent);
        if (isFolia()) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> callEvent.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, callEvent);
        }
        if (!llmEvent.isCancelled()) {
            if (isPlayer) {
                Player player = (Player) sender;
                if (!player.isOnline())
                    return;
                Runnable sendMessage = () -> player.sendMessage(response);
                if (isFolia()) {
                    player.getScheduler().run(plugin, task -> sendMessage.run(), null);
                } else {
                    Bukkit.getScheduler().runTask(plugin, sendMessage);
                }
            } else {
                Runnable logInfo = () -> plugin.getLogger().info(response);
                if (isFolia()) {
                    plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> logInfo.run());
                } else {
                    Bukkit.getScheduler().runTask(plugin, logInfo);
                }
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command llm, String label, String[] args) {
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
        String url = plugin.getConfig().getString("Config.url", "https://api.openai.com/v1/chat/completions");
        String instructions = plugin.getConfig().getString("Config.instructions",
                "You are a helpful assistant in Minecraft. Respond concisely and friendly.");
        String apiKey = plugin.getConfig().getString("Config.apikey", "");
        String model = plugin.getConfig().getString("Config.model", "gpt-4o-mini");
        int maxTokens = plugin.getConfig().getInt("Config.maxtokens", 800);
        String question = String.join(" ", args);
        String senderName = isPlayer ? ((Player) sender).getName() : "console";
        if (isFolia()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                String response = sendRequestToApi(url, instructions, senderName, question, apiKey, model, maxTokens,
                        isPlayer);
                processResponse(response, sender, senderName, question, isPlayer);
            });
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    String response = sendRequestToApi(url, instructions, senderName, question, apiKey, model,
                            maxTokens, isPlayer);
                    processResponse(response, sender, senderName, question, isPlayer);
                }
            }.runTaskAsynchronously(plugin);
        }
        return true;
    }

    private synchronized boolean saveHistory(String sender, String history, boolean isPlayer) {
        try {
            plugin.dataConfig.set(isPlayer ? "players." + sender : sender, history);
            plugin.dataConfig.save(plugin.data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized String getHistory(String sender, boolean isPlayer) {
        String path = isPlayer ? "players." + sender : sender;
        return plugin.dataConfig.contains(path) ? plugin.dataConfig.getString(path) : "";
    }
}
