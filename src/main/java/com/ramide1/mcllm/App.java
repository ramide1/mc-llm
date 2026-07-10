package com.ramide1.mcllm;

import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class App extends JavaPlugin {
    String pluginName = "Minecraft LLM";
    DatabaseManager dbManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        dbManager = new DatabaseManager(this);
        
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