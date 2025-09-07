package com.ramide1.mcllm;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class LlmEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String playerName;
    private final boolean isConsole;
    private final String message;
    private final String response;
    private boolean isCancelled;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public LlmEvent(String playerName, boolean isConsole, String message, String response) {
        this.playerName = playerName;
        this.isConsole = isConsole;
        this.message = message;
        this.response = response;
    }

    @Override
    public boolean isCancelled() {
        return this.isCancelled;
    }

    @Override
    public void setCancelled(boolean isCancelled) {
        this.isCancelled = isCancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public boolean getIsConsole() {
        return this.isConsole;
    }

    public String getMessage() {
        return this.message;
    }

    public String getResponse() {
        return this.response;
    }
}