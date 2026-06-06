package com.stoopad.qqwhitelist.manager;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BindManager {

    private final QQWhitelistPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;
    private int maxAccountsPerQQ;
    private boolean rebindEnabled;
    private long rebindMs;

    // 内存缓存: username -> openId
    private final Map<String, String> bindings = new ConcurrentHashMap<>();
    // 绑定时间: username -> timestamp
    private final Map<String, Long> bindTimes = new ConcurrentHashMap<>();

    public BindManager(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        reloadConfig();
        loadData();
    }

    /**
     * 重载配置
     */
    public void reloadConfig() {
        this.maxAccountsPerQQ = plugin.getConfig().getInt("max-accounts-per-qq", 1);
        this.rebindEnabled = plugin.getConfig().getBoolean("rebind-enabled", false);
        this.rebindMs = plugin.getConfig().getLong("rebind-days", 30) * 24L * 60 * 60 * 1000;
    }

    private void loadData() {
        if (!dataFile.exists()) {
            data = new YamlConfiguration();
            saveData();
        } else {
            data = YamlConfiguration.loadConfiguration(dataFile);
        }

        ConfigurationSection bindingsSection = data.getConfigurationSection("bindings");
        if (bindingsSection != null) {
            for (String username : bindingsSection.getKeys(false)) {
                String openId = bindingsSection.getString(username + ".qq");
                long boundAt = bindingsSection.getLong(username + ".boundAt", 0L);
                if (openId != null) {
                    bindings.put(username.toLowerCase(), openId);
                    bindTimes.put(username.toLowerCase(), boundAt);
                }
            }
        }
        plugin.getLogger().info("已加载 " + bindings.size() + " 条绑定数据");
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("保存绑定数据失败: " + e.getMessage());
        }
    }

    /**
     * 玩家是否已绑定
     */
    public boolean isBound(String username) {
        return bindings.containsKey(username.toLowerCase());
    }

    /**
     * 绑定是否已过期
     */
    public boolean isExpired(String username) {
        if (!rebindEnabled) return false;
        Long boundAt = bindTimes.get(username.toLowerCase());
        if (boundAt == null || boundAt == 0L) return false;
        return System.currentTimeMillis() - boundAt > rebindMs;
    }

    /**
     * 通过用户名获取绑定的openId
     */
    public String getOpenId(String username) {
        return bindings.get(username.toLowerCase());
    }

    /**
     * 统计某个QQ绑定了多少个账号
     */
    public int countByOpenId(String openId) {
        int count = 0;
        for (String oid : bindings.values()) {
            if (oid.equals(openId)) count++;
        }
        return count;
    }

    /**
     * 获取某个QQ绑定的所有用户名
     */
    public List<String> getBoundUsers(String openId) {
        List<String> users = new ArrayList<>();
        bindings.forEach((user, oid) -> {
            if (oid.equals(openId)) users.add(user);
        });
        return users;
    }

    /**
     * 获取单个QQ最大绑定数
     */
    public int getMaxAccountsPerQQ() {
        return maxAccountsPerQQ;
    }

    /**
     * 检查QQ是否还能绑定更多账号
     */
    public boolean canBind(String openId) {
        return countByOpenId(openId) < maxAccountsPerQQ;
    }

    /**
     * 执行绑定
     */
    public boolean bind(String username, String openId) {
        String key = username.toLowerCase();
        if (bindings.containsKey(key)) return false;
        if (!canBind(openId)) return false;

        long now = System.currentTimeMillis();
        bindings.put(key, openId);
        bindTimes.put(key, now);
        data.set("bindings." + key + ".qq", openId);
        data.set("bindings." + key + ".boundAt", now);
        saveData();
        return true;
    }

    /**
     * 解除绑定（过期时用）
     */
    public void unbind(String username) {
        String key = username.toLowerCase();
        bindings.remove(key);
        bindTimes.remove(key);
        data.set("bindings." + key, null);
        saveData();
    }
}
