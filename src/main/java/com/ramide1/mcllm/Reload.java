package com.ramide1.mcllm;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;

public class Reload implements CommandExecutor {
    private final App plugin;

    public Reload(App plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command llmreload, String label, String[] args) {
        plugin.reloadConfig();
        int maxHistoryMessages = Math.max(0, Math.min(500,
                plugin.getConfig().getInt("Config.maxHistoryMessages", 50)));
        plugin.dbManager.setMaxHistoryMessages(maxHistoryMessages);
        sender.sendMessage(Component.text(plugin.pluginName + " has reloaded!"));
        return true;
    }
}