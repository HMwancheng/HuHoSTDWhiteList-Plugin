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

public final class QQWhitelistPlugin extends JavaPlugin implements PluginMessageListener {

    private static final String CHANNEL = "huhostdwhitelist:main";

    private static QQWhitelistPlugin instance;
    private CodeManager codeManager;
    private BindManager bindManager;
    private JoinListener joinListener;
    private String bindCommand;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        updateConfig();

        bindCommand = getConfig().getString("bind-command", "验证码");
        codeManager = new CodeManager(this);
        bindManager = new BindManager(this);

        // 根据通信模式决定是否注册本地 HuHoBot 监听
        String requireMode = getConfig().getString("require", "huhobot").toLowerCase();
        switch (requireMode) {
            case "huhobot":
                Plugin huhoBot = getServer().getPluginManager().getPlugin("HuHoBot");
                if (huhoBot == null) {
                    getLogger().severe("require 设为 huhobot 但 HuHoBot 未安装！禁用 HuHoSTDWhiteList");
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
                getServer().getPluginManager().registerEvents(new BotCommandListener(this), this);
                getLogger().info("通信模式: HuHoBot 本地事件");
                break;
            case "rcadapter":
                getLogger().info("通信模式: GroupRCAdapter (Redis 控制台命令)");
                break;
            case "velocity":
                getLogger().info("通信模式: Velocity PluginMessage 通道");
                break;
            default:
                getLogger().warning("未知的 require 模式: " + requireMode + "，使用默认 huhobot 模式");
                getServer().getPluginManager().registerEvents(new BotCommandListener(this), this);
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
            handleVelocityBind(code, openId, player);
        }
    }

    private void handleVelocityBind(String code, String openId, Player relay) {
        // 通过验证码获取玩家名
        String playerName = codeManager.consumeCode(code);
        if (playerName == null) {
            getLogger().warning("Velocity 绑定: 验证码无效或已过期 " + code);
            sendBindResult(code, "error", getMessageWithPrefix("invalid-code"), relay);
            return;
        }

        // 检查绑定上限
        if (!bindManager.canBind(openId)) {
            getLogger().warning("Velocity 绑定失败: " + openId + " 已达上限");
            sendBindResult(code, "error", getMessageWithPrefix("bind-limit")
                    .replace("{max}", String.valueOf(bindManager.getMaxAccountsPerQQ())), relay);
            return;
        }

        if (bindManager.isBound(playerName)) {
            getLogger().info("Velocity 绑定: " + playerName + " 已绑定，跳过");
            sendBindResult(code, "success", getMessageWithPrefix("already-bound")
                    .replace("{player}", playerName), relay);
            return;
        }

        boolean success = bindManager.bind(playerName, openId);
        if (!success) {
            getLogger().warning("Velocity 绑定失败: " + playerName + " -> " + openId);
            sendBindResult(code, "error", getMessageWithPrefix("already-bound")
                    .replace("{player}", playerName), relay);
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
        sendBindResult(code, "success", getMessageWithPrefix("success")
                .replace("{player}", playerName), relay);

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
    private void sendBindResult(String code, String status, String message, Player relay) {
        String msg = "BIND_RESULT|" + status + "|" + message + "|" + code;
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        if (relay != null && relay.isOnline()) {
            relay.sendPluginMessage(this, CHANNEL, data);
        } else {
            // relay 不可用时降级为查找任意在线玩家
            Player anyPlayer = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                anyPlayer = p;
                break;
            }
            if (anyPlayer != null) {
                anyPlayer.sendPluginMessage(this, CHANNEL, data);
            } else {
                getLogger().warning("无在线玩家，无法回报绑定结果到 Velocity");
            }
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
     * 获取回报消息（不含前缀，仅消息文本）
     */
    public String getMessage(String key) {
        return getConfig().getString("messages." + key, key);
    }

    /**
     * 获取带前缀的回报消息（用于 QQ 群回报）
     */
    public String getMessageWithPrefix(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        String msg = getMessage(key);
        if (prefix.isEmpty()) return msg;
        return prefix + " " + msg;
    }

    public static QQWhitelistPlugin getInstance() { return instance; }
    public CodeManager getCodeManager() { return codeManager; }
    public BindManager getBindManager() { return bindManager; }
    public JoinListener getJoinListener() { return joinListener; }
    public String getBindCommand() { return bindCommand; }
}