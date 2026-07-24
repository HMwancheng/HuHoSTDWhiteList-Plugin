package cn.huohuas001.huhobot.spigot.api;

import com.alibaba.fastjson2.JSONObject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.List;

public class BotCustomCommand extends Event {

    private static final HandlerList handlers = new HandlerList();

    public String getCommand() {
        return "";
    }

    public List<String> getParam() {
        return Collections.emptyList();
    }

    public JSONObject getData() {
        return new JSONObject();
    }

    public void respone(String message, String type) {
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}