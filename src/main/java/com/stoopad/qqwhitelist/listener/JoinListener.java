package com.stoopad.qqwhitelist.listener;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.regex.Pattern;

public class JoinListener implements Listener {

    private final QQWhitelistPlugin plugin;

    public JoinListener(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // === 白名单绕过 ===
        if (isBypassed(player)) {
            plugin.getLogger().info(player.getName() + " 已绕过验证（白名单/权限）");
            return;
        }

        String cmd = plugin.getBindCommand();

        if (plugin.getBindManager().isBound(player.getName())) {
            // 已绑定，检查是否过期
            if (plugin.getBindManager().isExpired(player.getName())) {
                // 过期：解除绑定、移除白名单、生成新验证码踢出
                plugin.getBindManager().unbind(player.getName());
                OfflinePlayer offline = plugin.getServer().getOfflinePlayer(player.getName());
                offline.setWhitelisted(false);

                String code = plugin.getCodeManager().generateCode(player.getName());
                String message = plugin.getConfig().getString("rebind-kick-message",
                        "§c绑定已过期！§e请在QQ群 @HuHoBot /" + cmd + " {code} 重新绑定")
                        .replace("{cmd}", cmd).replace("{code}", code);
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
        String message = plugin.getConfig().getString("kick-message",
                "§c你尚未绑定QQ！§e请在QQ群 @HuHoBot /" + cmd + " {code}")
                .replace("{cmd}", cmd).replace("{code}", code);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.kick(Component.text(message));
            }
        }, 5L);
    }

    private boolean isBypassed(Player player) {
        // 名单绕过
        if (plugin.getConfig().getBoolean("bypass-names-enabled", false)) {
            List<String> bypassNames = plugin.getConfig().getStringList("bypass-names");
            String name = player.getName();
            for (String pattern : bypassNames) {
                if (globMatch(name, pattern)) {
                    return true;
                }
            }
        }

        // 权限绕过
        if (plugin.getConfig().getBoolean("bypass-permission-enabled", false)) {
            String perm = plugin.getConfig().getString("bypass-permission", "qqwhitelist.bypass");
            if (player.hasPermission(perm)) {
                return true;
            }
        }

        return false;
    }

    private boolean globMatch(String text, String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append(".");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(text).matches();
    }
}
