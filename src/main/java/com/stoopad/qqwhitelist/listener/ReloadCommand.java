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
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "delete":
                return handleDelete(sender, args);
            case "whitelist":
                return handleWhitelist(sender, args);
            case "velocitybind":
                return handleVelocityBind(sender, args);
            case "bind_redis":
                return handleRedisBind(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== HuHoSTDWhiteList ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/huhostdwhitelist reload", NamedTextColor.YELLOW)
                .append(Component.text(" - 重载配置", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/huhostdwhitelist delete <玩家名>", NamedTextColor.YELLOW)
                .append(Component.text(" - 删除玩家绑定", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/huhostdwhitelist whitelist add <玩家名>", NamedTextColor.YELLOW)
                .append(Component.text(" - 手动添加白名单(无QQ)", NamedTextColor.GRAY)));
    }

    private boolean checkPermission(CommandSender sender) {
        if (!sender.hasPermission("qqwhitelist.admin")) {
            sender.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    // ==================== reload ====================

    private boolean handleReload(CommandSender sender) {
        if (!checkPermission(sender)) return true;

        plugin.reloadConfig();
        plugin.getCodeManager().reloadConfig();
        plugin.getBindManager().reloadConfig();

        sender.sendMessage(Component.text("[HuHoSTDWhiteList] 配置已重载", NamedTextColor.GREEN));
        return true;
    }

    // ==================== delete ====================

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!checkPermission(sender)) return true;

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /huhostdwhitelist delete <玩家名>", NamedTextColor.YELLOW));
            return true;
        }

        String playerName = args[1];
        String openId = plugin.getBindManager().delete(playerName);

        if (openId == null) {
            sender.sendMessage(Component.text(playerName + " 没有绑定记录", NamedTextColor.RED));
            return true;
        }

        // 移除白名单
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline != null) {
            offline.setWhitelisted(false);
        }

        sender.sendMessage(Component.text(playerName + " 绑定已删除 (QQ: " + openId + ")，白名单已移除", NamedTextColor.GREEN));
        plugin.getLogger().info(sender.getName() + " 删除了 " + playerName + " 的绑定");
        return true;
    }

    // ==================== whitelist ====================

    private boolean handleWhitelist(CommandSender sender, String[] args) {
        if (!checkPermission(sender)) return true;

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /huhostdwhitelist whitelist add <玩家名>", NamedTextColor.YELLOW));
            return true;
        }

        if (!"add".equalsIgnoreCase(args[1])) {
            sender.sendMessage(Component.text("用法: /huhostdwhitelist whitelist add <玩家名>", NamedTextColor.YELLOW));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("请指定玩家名", NamedTextColor.RED));
            return true;
        }

        String playerName = args[2];
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline == null) {
            sender.sendMessage(Component.text("玩家 " + playerName + " 不存在", NamedTextColor.RED));
            return true;
        }

        offline.setWhitelisted(true);
        sender.sendMessage(Component.text(playerName + " 已手动添加白名单（无QQ绑定）", NamedTextColor.GREEN));
        plugin.getLogger().info(sender.getName() + " 手动为 " + playerName + " 添加了白名单");
        return true;
    }

    // ==================== velocitybind (内部) ====================

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

    // ==================== bind_redis (Redis 远程绑定) ====================

    private boolean handleRedisBind(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getLogger().info(plugin.getMessageWithPrefix("usage"));
            return true;
        }

        String code = args[1];
        String openId = args[2];

        // 验证码校验
        String playerName = plugin.getCodeManager().consumeCode(code);
        if (playerName == null) {
            plugin.getLogger().info(plugin.getMessageWithPrefix("invalid-code"));
            return true;
        }

        // 检查绑定上限
        if (!plugin.getBindManager().canBind(openId)) {
            plugin.getLogger().info(plugin.getMessageWithPrefix("bind-limit")
                    .replace("{max}", String.valueOf(plugin.getBindManager().getMaxAccountsPerQQ())));
            return true;
        }

        // 检查是否已绑定
        if (plugin.getBindManager().isBound(playerName)) {
            plugin.getLogger().info(plugin.getMessageWithPrefix("already-bound")
                    .replace("{player}", playerName));
            return true;
        }

        // 执行绑定
        boolean success = plugin.getBindManager().bind(playerName, openId);
        if (!success) {
            plugin.getLogger().info(plugin.getMessageWithPrefix("bind-fail"));
            return true;
        }

        // 加白名单
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        offline.setWhitelisted(true);
        plugin.getLogger().info(plugin.getMessageWithPrefix("success")
                .replace("{player}", playerName));

        // 如果玩家在线且处于倒计时中，取消倒计时放行
        Player target = Bukkit.getPlayer(playerName);
        if (target != null && target.isOnline() && plugin.getJoinListener().isInCountdown(target.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getJoinListener().cancelCountdown(target));
        }

        return true;
    }
}