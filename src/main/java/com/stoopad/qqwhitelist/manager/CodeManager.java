package com.stoopad.qqwhitelist.manager;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import org.bukkit.Bukkit;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CodeManager {

    private final QQWhitelistPlugin plugin;
    private final int codeLength;
    private final long expiryMs;
    private final SecureRandom random = new SecureRandom();

    // code -> CodeEntry
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public CodeManager(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.codeLength = plugin.getConfig().getInt("code-length", 6);
        this.expiryMs = plugin.getConfig().getLong("code-expiry-seconds", 300) * 1000L;

        // 定时清理过期验证码（每60秒）
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 20L * 60, 20L * 60);
    }

    /**
     * 为玩家生成验证码
     */
    public String generateCode(String playerName) {
        // 移除该玩家旧的验证码
        codes.entrySet().removeIf(e -> e.getValue().playerName().equalsIgnoreCase(playerName));

        String code = generateRandomCode();
        codes.put(code, new CodeEntry(playerName, System.currentTimeMillis() + expiryMs));
        return code;
    }

    /**
     * 验证并消费验证码，返回玩家名（无效返回null）
     */
    public String consumeCode(String code) {
        CodeEntry entry = codes.remove(code);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiry()) return null;
        return entry.playerName();
    }

    /**
     * 检查验证码是否有效
     */
    public boolean isValid(String code) {
        CodeEntry entry = codes.get(code);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiry()) {
            codes.remove(code);
            return false;
        }
        return true;
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        codes.entrySet().removeIf(e -> now > e.getValue().expiry());
    }

    public void shutdown() {
        codes.clear();
    }

    private record CodeEntry(String playerName, long expiry) {}
}
