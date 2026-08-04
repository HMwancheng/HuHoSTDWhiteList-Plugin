package com.stoopad.qqwhitelist.listener;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class JoinListener implements Listener {

    private final QQWhitelistPlugin plugin;
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> countdownTasks = new ConcurrentHashMap<>();

    public JoinListener(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== pre-login 模式 ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        String mode = plugin.getConfig().getString("verify-mode", "countdown");
        if (!"prelogin".equalsIgnoreCase(mode)) return;

        String name = event.getName();

        // 名单绕过（prelogin 不支持权限检查）
        if (isNameBypassed(name)) {
            plugin.getLogger().info(name + " 已绕过验证（名单白名单）");
            return;
        }

        // 已绑定
        if (plugin.getBindManager().isBound(name)) {
            if (plugin.getBindManager().isExpired(name)) {
                handleExpiredBindPreLogin(event, name);
                plugin.getLogger().info(name + " 绑定已过期，prelogin 拒绝");
            }
            return;
        }

        // 未绑定 → 生成验证码并拒绝
        String code = plugin.getCodeManager().generateCode(name);
        String cmd = plugin.getBindCommand();
        String message = plugin.getConfig().getString("kick-message",
                "§c你尚未绑定QQ！§e请在QQ群 @HuHoBot /" + cmd + " {code}")
                .replace("{cmd}", cmd).replace("{code}", code);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(message));
        plugin.getLogger().info(name + " prelogin 拒绝，验证码: " + code);
    }

    private void handleExpiredBindPreLogin(AsyncPlayerPreLoginEvent event, String name) {
        plugin.getBindManager().unbind(name);
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(name);
        if (offline != null) offline.setWhitelisted(false);

        String code = plugin.getCodeManager().generateCode(name);
        String cmd = plugin.getBindCommand();
        String message = plugin.getConfig().getString("rebind-kick-message",
                "§c绑定已过期！§e请在QQ群 @HuHoBot /" + cmd + " {code} 重新绑定")
                .replace("{cmd}", cmd).replace("{code}", code);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(message));
    }

    // ==================== countdown 模式 ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        String mode = plugin.getConfig().getString("verify-mode", "countdown");
        if (!"countdown".equalsIgnoreCase(mode)) return;

        Player player = event.getPlayer();

        // 白名单绕过
        if (isBypassed(player)) {
            plugin.getLogger().info(player.getName() + " 已绕过验证（白名单/权限）");
            return;
        }

        String cmd = plugin.getBindCommand();

        if (plugin.getBindManager().isBound(player.getName())) {
            if (plugin.getBindManager().isExpired(player.getName())) {
                handleExpiredBindCountdown(player, cmd);
            }
            return;
        }

        // 未绑定 → 开始倒计时
        String code = plugin.getCodeManager().generateCode(player.getName());
        startCountdown(player, code, cmd);
    }

    private void handleExpiredBindCountdown(Player player, String cmd) {
        plugin.getBindManager().unbind(player.getName());
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(player.getName());
        offline.setWhitelisted(false);

        String code = plugin.getCodeManager().generateCode(player.getName());
        startCountdown(player, code, cmd);
        plugin.getLogger().info(player.getName() + " 绑定已过期，重新倒计时");
    }

    // ==================== 倒计时逻辑 ====================

    private void startCountdown(Player player, String code, String cmd) {
        int totalSeconds = plugin.getConfig().getInt("countdown-seconds", 15);
        boolean freeze = plugin.getConfig().getBoolean("countdown-freeze", true);
        String barTemplate = plugin.getConfig().getString("countdown-bar",
                "§e请在QQ群 @HuHoBot /{cmd} {code}  §c{time}秒后踢出");

        // 创建 BossBar
        String title = barTemplate.replace("{cmd}", cmd).replace("{code}", code)
                .replace("{time}", String.valueOf(totalSeconds));
        BossBar bar = BossBar.bossBar(Component.text(title), 1.0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        activeBossBars.put(player.getUniqueId(), bar);

        if (freeze) {
            player.setWalkSpeed(0);
            player.setFlySpeed(0);
        }

        BukkitRunnable task = new BukkitRunnable() {
            int remaining = totalSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup(player);
                    cancel();
                    return;
                }

                // 倒计时期间完成绑定 → 放行
                if (plugin.getBindManager().isBound(player.getName())) {
                    cleanup(player);
                    plugin.getLogger().info(player.getName() + " 在倒计时期间完成绑定，已放行");
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    String message = plugin.getConfig().getString("kick-message",
                            "§c你尚未绑定QQ！§e请在QQ群 @HuHoBot /" + cmd + " {code}")
                            .replace("{cmd}", cmd).replace("{code}", code);
                    // 先取消任务，清理由 PlayerQuitEvent 处理
                    countdownTasks.remove(player.getUniqueId());
                    cancel();
                    player.kick(Component.text(message));
                    return;
                }

                bar.name(Component.text(barTemplate.replace("{cmd}", cmd)
                        .replace("{code}", code).replace("{time}", String.valueOf(remaining))));
                bar.progress((float) remaining / totalSeconds);
                remaining--;
            }
        };
        countdownTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    private void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bar = activeBossBars.remove(uuid);
        if (bar != null) player.hideBossBar(bar);
        countdownTasks.remove(uuid);

        // 恢复移动速度
        if (player.isOnline()) {
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        }
    }

    /**
     * 由 QQWhitelistPlugin 调用，取消某玩家的倒计时（Velocity 发来绑定消息时）
     */
    public void cancelCountdown(Player player) {
        BukkitRunnable task = countdownTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        cleanup(player);
        player.sendMessage(Component.text("§a绑定成功！欢迎进入服务器", NamedTextColor.GREEN));
        plugin.getLogger().info(player.getName() + " 倒计时已取消（Velocity 绑定）");
    }

    /**
     * 获取处于倒计时中的玩家名列表
     */
    public boolean isInCountdown(UUID uuid) {
        return countdownTasks.containsKey(uuid);
    }

    // ==================== 玩家退出 ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BossBar bar = activeBossBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
        BukkitRunnable task = countdownTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    // ==================== 绕过检查 ====================

    private boolean isBypassed(Player player) {
        // 名单绕过
        if (plugin.getConfig().getBoolean("bypass-names-enabled", false)) {
            if (isNameBypassed(player.getName())) return true;
        }

        // 权限绕过（仅 countdown 模式）
        if (plugin.getConfig().getBoolean("bypass-permission-enabled", false)) {
            String perm = plugin.getConfig().getString("bypass-permission", "huhostdwhitelist.bypass");
            if (player.hasPermission(perm)) return true;
        }

        return false;
    }

    private boolean isNameBypassed(String name) {
        if (!plugin.getConfig().getBoolean("bypass-names-enabled", false)) return false;
        List<String> bypassNames = plugin.getConfig().getStringList("bypass-names");
        for (String pattern : bypassNames) {
            if (globMatch(name, pattern)) return true;
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