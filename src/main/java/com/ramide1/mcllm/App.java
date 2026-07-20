package com.ramide1.mcllm;

import org.bukkit.plugin.java.JavaPlugin;

public class App extends JavaPlugin {
    String pluginName = "Minecraft LLM";
    DatabaseManager dbManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int maxHistoryMessages = Math.max(0, Math.min(500, getConfig().getInt("Config.maxHistoryMessages", 50)));
        dbManager = new DatabaseManager(this, maxHistoryMessages);

        getCommand("llm").setExecutor(new Llm(this));
        getCommand("llmreload").setExecutor(new Reload(this));
        getLogger().info(pluginName + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (dbManager != null) {
            dbManager.close();
        }
        getLogger().info(pluginName + " has been disabled!");
    }
}