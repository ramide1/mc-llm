package com.ramide1.mcllm;

import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class App extends JavaPlugin {
    String pluginName = "Minecraft LLM";
    File config;
    File data;
    FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        config = new File(getDataFolder(), "config.yml");
        if (!config.exists()) {
            saveDefaultConfig();
        }
        data = new File(getDataFolder(), "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(data);
        if (!data.exists()) {
            try {
                dataConfig.save(data);
            } catch (Exception e) {
                getLogger().info(ChatColor.RED + "An error has ocurred while saving data file");
            }
        }
        getCommand("llm").setExecutor(new Llm(this));
        getCommand("llmreload").setExecutor(new Reload(this));
        getLogger().info(ChatColor.GREEN + pluginName + " has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.GREEN + pluginName + " has been disabled!");
    }
}