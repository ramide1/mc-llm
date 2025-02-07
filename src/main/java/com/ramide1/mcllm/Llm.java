package com.ramide1.mcllm;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Llm implements CommandExecutor {
    private App plugin;

    public Llm(App plugin) {
        this.plugin = plugin;
    }

    private String sendRequestToGPTApi(String url, String instructions, String sender, String question, String apikey,
            String model) {
        String content = "Url is empty.";
        if (!url.isEmpty()) {
            content = "Response was not ok.";
            try {
                HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                if (!apikey.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + apikey);
                }
                connection.setDoOutput(true);
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
                String data = !model.isEmpty() ? "{\"model\": \"" + model + "\", \"messages\": " + messages + "}"
                        : "{\"messages\": " + messages + "}";
                OutputStream os = connection.getOutputStream();
                byte[] postData = data.getBytes("utf-8");
                os.write(postData, 0, postData.length);
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    content = "Field content was not found in JSON response.";
                    BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    String regex = "\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(response.toString());
                    if (matcher.find()) {
                        content = matcher.group(1).replace("\\n", "").replace("\\", "").replace("\"", "")
                                .replace("{", "").replace("}", "");
                        newHistory = newHistory + "," + "{\"role\": \"assistant\",\"content\": \"" + content + "\"}";
                        saveHistory(sender, newHistory, false);
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                content = e.getMessage();
            }
        }
        return content;
    }

    private String sendRequestToGeminiApi(String instructions, String sender, String question, String apikey,
            String model) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key="
                + apikey;
        String content = "Response was not ok.";
        try {
            HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
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
            String data = "{\"contents\": " + messages + "}";
            OutputStream os = connection.getOutputStream();
            byte[] postData = data.getBytes("utf-8");
            os.write(postData, 0, postData.length);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                content = "Field text was not found in JSON response.";
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                String regex = "\"candidates\"\\s*:\\s*\\[\\s*\\{\\s*\"content\"\\s*:\\s*\\{\\s*\"parts\"\\s*:\\s*\\[\\s*\\{\\s*\"text\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(response.toString());
                if (matcher.find()) {
                    content = matcher.group(1).replace("\\n", "").replace("\\", "").replace("\"", "").replace("{", "")
                            .replace("}", "");
                    newHistory = newHistory + "," + "{\"role\": \"model\",\"parts\": [" + "{\"text\": \"" + content
                            + "\"}" + "]}";
                    saveHistory(sender, newHistory, true);
                }
            }
            connection.disconnect();
        } catch (Exception e) {
            content = e.getMessage();
        }
        return content;
    }

    public boolean onCommand(CommandSender sender, Command ai, String label, String[] args) {
        String url = plugin.getConfig().getString("Config.url", "https://api.openai.com/v1/chat/completions");
        String instructions = plugin.getConfig().getString("Config.instructions",
                "You are a helpful assistant in Minecraft. Respond concisely and friendly.");
        String apiKey = plugin.getConfig().getString("Config.apikey", "");
        String model = plugin.getConfig().getString("Config.model", "gpt-4o-mini");
        boolean googleApi = plugin.getConfig().getBoolean("Config.googleapi", false);
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
                                    apiKey, model);
                            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(response));
                        } else {
                            String response = sendRequestToGPTApi(url, instructions, player.getName(),
                                    question.toString(), apiKey,
                                    model);
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
                                    apiKey, model);
                            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().info(response));
                        } else {
                            String response = sendRequestToGPTApi(url, instructions, "console",
                                    question.toString(),
                                    apiKey,
                                    model);
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
            if (googleApi) {
                plugin.googleDataConfig.set(sender, history);
                plugin.googleDataConfig.save(plugin.googleData);
            } else {
                plugin.gptDataConfig.set(sender, history);
                plugin.gptDataConfig.save(plugin.gptData);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getHistory(String sender, boolean googleApi) {
        if (googleApi) {
            return plugin.googleDataConfig.contains(sender) ? plugin.googleDataConfig.getString(sender) : "";
        } else {
            return plugin.gptDataConfig.contains(sender) ? plugin.gptDataConfig.getString(sender) : "";
        }
    }
}
