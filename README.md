# HuHoSTDWhiteList

HuHoBot 附属插件 — 离线服 QQ 验证码绑定白名单

## 功能

- 玩家首次进服自动踢出并生成随机验证码
- QQ 群发送绑定命令完成验证，自动加白名单
- 再次进服即可正常游玩
- 支持单 QQ 限制绑定账号数量
- 支持绑定过期重新验证（可选）
- 绑定命令可自定义（默认 `验证码`）
- **消息回报** — 绑定结果通过 HuHoBot 实时反馈到 QQ 群
- **配置热重载** — `/huhostdwhitelist reload` 无需重启服务器

## 前置依赖

- [HuHoBot](https://github.com/HuHoBot/SpigotAdapter) Spigot 版
- Paper / Spigot 1.21+
- JDK 21

## 安装

1. 下载 Releases 中的 `HuHoSTDWhiteList.jar`
2. 放入服务器 `plugins/` 目录
3. 确保 HuHoBot 已安装并正常运行
4. 重启服务器

## 配置

`plugins/HuHoSTDWhiteList/config.yml`：

```yaml
# QQ群绑定命令名（玩家在QQ群发送的命令）
bind-command: "验证码"

# 单个QQ最多绑定账号数量
max-accounts-per-qq: 1

# 验证码长度
code-length: 6

# 验证码有效期（秒）
code-expiry-seconds: 300

# 踢出提示消息（{code} = 验证码，{cmd} = 绑定命令名）
kick-message: "§c你尚未绑定QQ！§e请在QQ群 @HuHoBot /{cmd} {code}"

# 绑定过期重新验证（false=永不过期）
rebind-enabled: false

# 过期天数（rebind-enabled: true 时生效）
rebind-days: 30

# 过期踢出提示
rebind-kick-message: "§c绑定已过期！§e请在QQ群 @HuHoBot /{cmd} {code} 重新绑定"

# 回报消息（{player}=用户名, {max}=绑定上限）
messages:
  # 无参数时提示
  usage: "用法: /验证码 <验证码>"
  # 验证码不存在或已过期
  invalid-code: "验证码无效或已过期"
  # QQ绑定数量已达上限
  bind-limit: "绑定上限（最多{max}个）"
  # 玩家已被其他QQ绑定
  already-bound: "{player} 已绑定"
  # 绑定成功
  success: "{player} 绑定成功，已加白名单"
```

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/huhostdwhitelist reload` | `qqwhitelist.admin` | 重载配置文件（游戏内/控制台） |

## 使用流程

1. 玩家进入服务器
2. 未绑定 → 自动踢出，提示验证码
3. 玩家在 QQ 群发送 `@HuHoBot /验证码 <验证码>`（命令名可在配置中修改）
4. HuHoBot 消息回报绑定结果
5. 重新进入服务器，正常游玩

## 扩展插件

- **[HuHoSTDAuthMe](https://github.com/kuixuestoopad/HuHoSTDAuthMe)** — AuthMe 联动扩展：QQ 群内强制登陆 & 重置密码

## 编译

```bash
# 将 HuHoBot-Spigot.jar 放入 libs/ 目录
gradle build
# 输出: build/libs/HuHoSTDWhiteList.jar
```

## 许可证

[MIT](LICENSE)
