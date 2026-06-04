package com.stoopad.qqwhitelist.listener;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final QQWhitelistPlugin plugin;
    private final String kickMessageTemplate;
    private final String rebindKickMessage;

    public JoinListener(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
        String cmd = plugin.getBindCommand();
        this.kickMessageTemplate = plugin.getConfig().getString("kick-message",
                "§c你尚未绑定QQ！§e请在QQ群 @HuHoBot /" + cmd + " {code}");
        this.rebindKickMessage = plugin.getConfig().getString("rebind-kick-message",
                "§c绑定已过期！§e请在QQ群 @HuHoBot /" + cmd + " {code} 重新绑定");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getBindManager().isBound(player.getName())) {
            // 已绑定，检查是否过期
            if (plugin.getBindManager().isExpired(player.getName())) {
                // 过期：解除绑定、移除白名单、生成新验证码踢出
                plugin.getBindManager().unbind(player.getName());
                OfflinePlayer offline = plugin.getServer().getOfflinePlayer(player.getName());
                offline.setWhitelisted(false);

                String code = plugin.getCodeManager().generateCode(player.getName());
                String message = rebindKickMessage.replace("{cmd}", plugin.getBindCommand()).replace("{code}", code);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(Component.text(message));
                    }
                }, 5L);
                plugin.getLogger().info(player.getName() + " 绑定已过期，需重新验证");
            }
            // 未过期 -> 放行
            return;
        }

        // 未绑定 -> 生成验证码并踢出
        String code = plugin.getCodeManager().generateCode(player.getName());
        String message = kickMessageTemplate.replace("{cmd}", plugin.getBindCommand()).replace("{code}", code);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.kick(Component.text(message));
            }
        }, 5L);
    }
}
