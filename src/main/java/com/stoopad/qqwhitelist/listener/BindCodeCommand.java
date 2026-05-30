package com.stoopad.qqwhitelist.listener;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BindCodeCommand implements CommandExecutor {

    private final QQWhitelistPlugin plugin;

    public BindCodeCommand(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("用法: /bindcode <验证码>", NamedTextColor.RED));
            return true;
        }

        // 已绑定的不需要再绑
        if (plugin.getBindManager().isBound(player.getName())) {
            player.sendMessage(Component.text("你已经绑定过了", NamedTextColor.YELLOW));
            return true;
        }

        String code = args[0];
        // 注意：这个命令实际上不需要使用，绑定通过QQ群完成
        // 这里只是备用提示
        player.sendMessage(Component.text("请在QQ群 @机器人 /S验证码 <验证码> 完成绑定", NamedTextColor.YELLOW));
        return true;
    }
}
