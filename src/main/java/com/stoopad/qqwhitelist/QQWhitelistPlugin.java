package com.stoopad.qqwhitelist;

import com.stoopad.qqwhitelist.listener.BotCommandListener;
import com.stoopad.qqwhitelist.listener.BindCodeCommand;
import com.stoopad.qqwhitelist.listener.JoinListener;
import com.stoopad.qqwhitelist.listener.ReloadCommand;
import com.stoopad.qqwhitelist.manager.BindManager;
import com.stoopad.qqwhitelist.manager.CodeManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class QQWhitelistPlugin extends JavaPlugin implements PluginMessageListener {

    private static final String CHANNEL = "huhostdwhitelist:main";

    private static QQWhitelistPlugin instance;
    private CodeManager codeManager;
    private BindManager bindManager;
    private JoinListener joinListener;
    private String bindCommand;
    private final Queue<String> pendingBindResults = new ConcurrentLinkedQueue<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        updateConfig();

        bindCommand = getConfig().getString("bind-command", "验证码");
        codeManager = new CodeManager(this);
        bindManager = new BindManager(this);

        // 检查是否需要本地 HuHoBot
        boolean requireHuHoBot = getConfig().getBoolean("require-huhobot", true);
        if (requireHuHoBot) {
            Plugin huhoBot = getServer().getPluginManager().getPlugin("HuHoBot");
            if (huhoBot == null) {
                getLogger().severe("HuHoBot 未安装！禁用 HuHoSTDWhiteList");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            try {
                Class.forName("cn.huohuas001.huhobot.spigot.api.BotCustomCommand");
            } catch (ClassNotFoundException e) {
                getLogger().severe("HuHoBot API 加载失败: " + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // 注册 HuHoBot 本地命令监听
            getServer().getPluginManager().registerEvents(new BotCommandListener(this), this);
        } else {
            getLogger().info("HuHoBot 本地检测已跳过，仅通过 Velocity 插件消息通道接收绑定");
        }

        // 注册事件
        joinListener = new JoinListener(this);
        getServer().getPluginManager().registerEvents(joinListener, this);

        // 注册命令
        getCommand("bindcode").setExecutor(new BindCodeCommand(this));
        getCommand("huhostdwhitelist").setExecutor(new ReloadCommand(this));

        // 注册 Plugin Message 通道（与 Velocity 配套插件通信）
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);

        getLogger().info("HuHoSTDWhiteList 已加载  mode=" + getConfig().getString("verify-mode", "countdown"));
    }

    @Override
    public void onDisable() {
        if (codeManager != null) codeManager.shutdown();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getLogger().info("HuHoSTDWhiteList 已卸载");
    }

    // ==================== Plugin Message ====================

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        String msg = new String(message, StandardCharsets.UTF_8);
        String[] parts = msg.split("\\|", 3);
        if (parts.length < 3) return;

        String action = parts[0];
        String code = parts[1];
        String openId = parts[2];

        if ("BIND".equals(action)) {
            handleVelocityBind(code, openId);
        }
    }

    private void handleVelocityBind(String code, String openId) {
        // 通过验证码获取玩家名
        String playerName = codeManager.consumeCode(code);
        if (playerName == null) {
            getLogger().warning("Velocity 绑定: 验证码无效或已过期 " + code);
            sendBindResult(code, "error", getMessage("invalid-code"));
            return;
        }

        // 检查绑定上限
        if (!bindManager.canBind(openId)) {
            getLogger().warning("Velocity 绑定失败: " + openId + " 已达上限");
            sendBindResult(code, "error", getMessage("bind-limit")
                    .replace("{max}", String.valueOf(bindManager.getMaxAccountsPerQQ())));
            return;
        }

        if (bindManager.isBound(playerName)) {
            getLogger().info("Velocity 绑定: " + playerName + " 已绑定，跳过");
            sendBindResult(code, "success", getMessage("already-bound")
                    .replace("{player}", playerName));
            return;
        }

        boolean success = bindManager.bind(playerName, openId);
        if (!success) {
            getLogger().warning("Velocity 绑定失败: " + playerName + " -> " + openId);
            sendBindResult(code, "error", getMessage("already-bound")
                    .replace("{player}", playerName));
            return;
        }

        // 加白名单
        String finalName = playerName;
        getServer().getScheduler().runTask(this, () -> {
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(finalName);
            offline.setWhitelisted(true);
            getLogger().info("Velocity 绑定: " + finalName + " <-> " + openId + " 已加白名单");
        });

        // 回报 HuHoBot-Velocity → QQ群消息
        sendBindResult(code, "success", getMessage("success")
                .replace("{player}", playerName));

        // 如果玩家在线且处于倒计时中，取消倒计时放行
        Player target = Bukkit.getPlayer(playerName);
        if (target != null && target.isOnline() && joinListener.isInCountdown(target.getUniqueId())) {
            getServer().getScheduler().runTask(this, () -> joinListener.cancelCountdown(target));
        }
    }

    /**
     * 向 Velocity 发送绑定结果回报
     * 格式: BIND_RESULT|status|resolved_message|code
     * status: success / error
     */
    private void sendBindResult(String code, String status, String message) {
        String msg = "BIND_RESULT|" + status + "|" + message + "|" + code;
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        // 通过任意在线玩家发送插件消息回 Velocity
        Player anyPlayer = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            anyPlayer = p;
            break;
        }
        if (anyPlayer != null) {
            // 先发送积压的消息
            flushBindResults(anyPlayer);
            anyPlayer.sendPluginMessage(this, CHANNEL, data);
        } else {
            // 无人在线，缓存消息等待玩家加入时补发
            pendingBindResults.add(msg);
            getLogger().info("无在线玩家，BIND_RESULT 已缓存待玩家加入时补发");
        }
    }

    /**
     * 补发缓存中的绑定结果（玩家加入时调用）
     */
    public void flushBindResults(Player player) {
        String msg;
        while ((msg = pendingBindResults.poll()) != null) {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            player.sendPluginMessage(this, CHANNEL, data);
            getLogger().info("补发缓存 BIND_RESULT: " + msg);
        }
    }

    // ==================== 配置更新 ====================

    /**
     * 自动更新已有配置文件，合并新增的配置项
     */
    private void updateConfig() {
        try {
            Reader reader = new InputStreamReader(getResource("config.yml"), StandardCharsets.UTF_8);
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
            reader.close();

            File configFile = new File(getDataFolder(), "config.yml");
            YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(configFile);

            boolean updated = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!diskConfig.contains(key)) {
                    diskConfig.set(key, defaultConfig.get(key));
                    updated = true;
                    getLogger().info("新增配置项: " + key);
                }
            }
            if (updated) {
                diskConfig.save(configFile);
                reloadConfig();
                getLogger().info("配置文件已自动更新");
            }
        } catch (Exception e) {
            getLogger().warning("配置更新检查失败: " + e.getMessage());
        }
    }

    /**
     * 获取回报消息
     */
    public String getMessage(String key) {
        return getConfig().getString("messages." + key, key);
    }

    public static QQWhitelistPlugin getInstance() { return instance; }
    public CodeManager getCodeManager() { return codeManager; }
    public BindManager getBindManager() { return bindManager; }
    public JoinListener getJoinListener() { return joinListener; }
    public String getBindCommand() { return bindCommand; }
}