package com.stoopad.qqwhitelist.listener;

import cn.huohuas001.huhobot.spigot.api.BotCustomCommand;
import com.alibaba.fastjson2.JSONObject;
import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

public class BotCommandListener implements Listener {

    private final QQWhitelistPlugin plugin;

    public BotCommandListener(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBotCommand(BotCustomCommand event) {
        String command = event.getCommand();
        List<String> params = event.getParam();

        if (plugin.getBindCommand().equals(command)) {
            handleBindCode(event, params);
        }
    }

    private void handleBindCode(BotCustomCommand event, List<String> params) {
        event.setCancelled(true);

        // 获取用户信息
        JSONObject data = event.getData();
        JSONObject author = data.getJSONObject("author");
        String openId = author.getString("openId");

        // 无参数
        if (params.isEmpty()) {
            event.respone(plugin.getMessage("usage"), "success");
            return;
        }

        String code = params.get(0);

        // 检查QQ绑定数量
        if (!plugin.getBindManager().canBind(openId)) {
            event.respone(plugin.getMessage("bind-limit")
                    .replace("{max}", String.valueOf(plugin.getBindManager().getMaxAccountsPerQQ())), "success");
            return;
        }

        // 验证码校验
        String playerName = plugin.getCodeManager().consumeCode(code);
        if (playerName == null) {
            event.respone(plugin.getMessage("invalid-code"), "success");
            return;
        }

        // 检查玩家是否已绑定
        if (plugin.getBindManager().isBound(playerName)) {
            event.respone(plugin.getMessage("already-bound")
                    .replace("{player}", playerName), "success");
            return;
        }

        // 执行绑定
        boolean success = plugin.getBindManager().bind(playerName, openId);
        if (!success) {
            event.respone(plugin.getMessage("already-bound")
                    .replace("{player}", playerName), "success");
            return;
        }

        // 加白名单
        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            offlinePlayer.setWhitelisted(true);
            plugin.getLogger().info("已为 " + playerName + " 添加白名单（QQ绑定 by " + openId + "）");
        });

        // 回报成功
        event.respone(plugin.getMessage("success")
                .replace("{player}", playerName), "success");
    }
}
