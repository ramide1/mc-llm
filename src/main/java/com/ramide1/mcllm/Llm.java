package com.ramide1.mcllm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class Llm implements CommandExecutor {
    private final App plugin;

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

    private String sendRequestToApi(String instructions, String senderName, String question, String apiKey,
                                   String baseUrl, String model, int maxTokens, boolean isPlayer) {
        try {
            OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                    .apiKey(apiKey);

            if (baseUrl != null && !baseUrl.isEmpty()) {
                builder.baseUrl(baseUrl);
            }

            OpenAIClient client = builder.build();
            
            // Construimos la entrada. La nueva API de Responses simplifica esto, 
            // pero para mantener el historial necesitamos pasar los mensajes.
            List<DatabaseManager.ChatMessage> history = plugin.dbManager.getHistory(senderName);
            
            StringBuilder inputBuilder = new StringBuilder();
            if (!instructions.isEmpty()) {
                inputBuilder.append("System: ").append(instructions).append("\n");
            }
            for (DatabaseManager.ChatMessage msg : history) {
                inputBuilder.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            inputBuilder.append("user: ").append(question);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .input(inputBuilder.toString())
                    .model(model)
                    .maxOutputTokens(maxTokens)
                    .build();
            
            Response response = client.responses().create(params);
            String content = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(c -> c.outputText().stream())
                    .findFirst()
                    .map(ResponseOutputText::text)
                    .orElse("");
            
            plugin.dbManager.saveMessage(senderName, "user", question);
            plugin.dbManager.saveMessage(senderName, "assistant", content);
            
            return content;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
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
                if (!player.isOnline()) return;
                
                Runnable sendMessage = () -> player.sendMessage(Component.text(response));
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

        String instructions = plugin.getConfig().getString("Config.instructions",
                "You are a helpful assistant in Minecraft. Respond concisely and friendly.");
        String apiKey = plugin.getConfig().getString("Config.apiKey", "");
        String baseUrl = plugin.getConfig().getString("Config.baseUrl", "");
        String model = plugin.getConfig().getString("Config.model", "gpt-4o-mini");
        int maxTokens = plugin.getConfig().getInt("Config.maxTokens", 800);
        String question = String.join(" ", args);
        String senderName = isPlayer ? ((Player) sender).getName() : "console";

        if (isFolia()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                String response = sendRequestToApi(instructions, senderName, question, apiKey, baseUrl, model, maxTokens, isPlayer);
                processResponse(response, sender, senderName, question, isPlayer);
            });
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    String response = sendRequestToApi(instructions, senderName, question, apiKey, baseUrl, model, maxTokens, isPlayer);
                    processResponse(response, sender, senderName, question, isPlayer);
                }
            }.runTaskAsynchronously(plugin);
        }
        return true;
    }
}
