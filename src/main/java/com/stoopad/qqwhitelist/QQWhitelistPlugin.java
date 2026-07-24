package com.stoopad.qqwhitelist;

import com.stoopad.qqwhitelist.listener.BotCommandListener;
import com.stoopad.qqwhitelist.listener.BindCodeCommand;
import com.stoopad.qqwhitelist.listener.JoinListener;
import com.stoopad.qqwhitelist.listener.ReloadCommand;
import com.stoopad.qqwhitelist.manager.BindManager;
import com.stoopad.qqwhitelist.manager.CodeManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class QQWhitelistPlugin extends JavaPlugin {

    private static QQWhitelistPlugin instance;
    private CodeManager codeManager;
    private BindManager bindManager;
    private String bindCommand;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        updateConfig();

        // 检查 HuHoBot
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

        bindCommand = getConfig().getString("bind-command", "验证码");
        codeManager = new CodeManager(this);
        bindManager = new BindManager(this);

        // 注册事件
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new BotCommandListener(this), this);

        // 注册命令
        getCommand("bindcode").setExecutor(new BindCodeCommand(this));
        getCommand("huhostdwhitelist").setExecutor(new ReloadCommand(this));

        getLogger().info("HuHoSTDWhiteList 已加载");
    }

    @Override
    public void onDisable() {
        if (codeManager != null) codeManager.shutdown();
        getLogger().info("HuHoSTDWhiteList 已卸载");
    }

    /**
     * 自动更新已有配置文件，合并新增的配置项
     */
    private void updateConfig() {
        try {
            // 读取 jar 内默认配置
            Reader reader = new InputStreamReader(getResource("config.yml"), StandardCharsets.UTF_8);
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
            reader.close();

            // 直接读取磁盘文件，避免 reloadConfig 的 defaults 干扰 contains 判断
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
    public String getBindCommand() { return bindCommand; }
}
