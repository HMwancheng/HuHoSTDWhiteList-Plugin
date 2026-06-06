package com.stoopad.qqwhitelist.listener;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final QQWhitelistPlugin plugin;

    public ReloadCommand(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(Component.text("用法: /huhostdwhitelist reload", NamedTextColor.YELLOW));
            return true;
        }

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
