package com.stoopad.qqwhitelist.listener;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final QQWhitelistPlugin plugin;

    public ReloadCommand(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /huhostdwhitelist reload", NamedTextColor.YELLOW));
            return true;
        }

        // Velocity 端发来的绑定请求
        if ("velocitybind".equalsIgnoreCase(args[0])) {
            return handleVelocityBind(sender, args);
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            return handleReload(sender);
        }

        sender.sendMessage(Component.text("用法: /huhostdwhitelist reload", NamedTextColor.YELLOW));
        return true;
    }

    /**
     * 处理 Velocity 端发来的绑定请求
     * 格式: /huhostdwhitelist velocitybind <code> <openId>
     */
    private boolean handleVelocityBind(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getLogger().warning("Velocity bind: 参数不足，需要 code 和 openId");
            return true;
        }

        String code = args[1];
        String openId = args[2];

        // 通过验证码获取玩家名
        String playerName = plugin.getCodeManager().consumeCode(code);
        if (playerName == null) {
            plugin.getLogger().warning("Velocity bind: 验证码无效或已过期 " + code);
            return true;
        }

        // 检查绑定上限
        if (!plugin.getBindManager().canBind(openId)) {
            plugin.getLogger().warning("Velocity bind: " + openId + " 已达上限");
            return true;
        }

        if (plugin.getBindManager().isBound(playerName)) {
            plugin.getLogger().info("Velocity bind: " + playerName + " 已绑定，跳过");
            return true;
        }

        boolean success = plugin.getBindManager().bind(playerName, openId);
        if (!success) {
            plugin.getLogger().warning("Velocity bind 失败: " + playerName + " -> " + openId);
            return true;
        }

        // 加白名单
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        offline.setWhitelisted(true);
        plugin.getLogger().info("Velocity bind: " + playerName + " <-> " + openId + " 已加白名单");

        // 如果玩家在线且处于倒计时中，取消倒计时放行
        Player target = Bukkit.getPlayer(playerName);
        if (target != null && target.isOnline() && plugin.getJoinListener().isInCountdown(target.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getJoinListener().cancelCountdown(target));
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("qqwhitelist.admin")) {
            sender.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
            return true;
        }

        plugin.reloadConfig();
        plugin.getCodeManager().reloadConfig();
        plugin.getBindManager().reloadConfig();

        sender.sendMessage(Component.text("[HuHoSTDWhiteList] 配置已重载", NamedTextColor.GREEN));
        return true;
    }
}
