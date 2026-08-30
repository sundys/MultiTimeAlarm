# 多时闹钟（MultiTimeAlarm）

一款 Android 闹钟应用，核心特色是**一个定时任务可设置多个提醒时间**，专为需要"按日程多点提醒"的场景设计（服药、打卡、会议前多次提醒等）。

## 功能特性

### 核心特色
- **一个闹钟，多个时间点**：一个任务可添加任意多个提醒时间，统一管理、可单独启停
- **五种提醒类型**：
  - 每天重复：如吃药 8:00 / 12:00 / 18:00
  - 按星期：周一至周日任意组合（工作日 / 周末 / 每天快捷预设）
  - 每月：每月某日提醒，该月无此日期自动跳过
  - 间隔循环：每隔 N 小时 M 分钟持续循环提醒
  - 仅一次：指定日期多点提醒，全部触发后自动关闭
- **时间精确到秒**：时/分/秒三列数字滚动选择器
- **小憩倒计时**：一键"N 分钟后响铃"

### 提醒可靠性
- `AlarmManager.setAlarmClock()` 精确调度：免"闹钟和提醒"特殊权限，Doze 休眠下准时触发
- 高优先级通知 + 全屏 Intent，锁屏直接弹出响铃页（铃声 + 振动）
- 贪睡可配置：间隔（分钟）与次数上限（0 = 不限）
- 重启后开机广播自动恢复全部闹钟；进程被杀有冷启动兜底重调度
- 设置页提供"忽略电池优化"与"自启动设置"入口，适配 MIUI/EMUI 等国产 ROM

### 界面
- 闹钟 / 小憩双 Tab 首页，滑动切换，胶囊形指示器
- 浮动圆形 + 按钮，列表滑动时自动隐藏、停下显示
- 主题颜色三模式：跟随系统 / 浅色 / 暗色（状态栏与导航栏同步跟随）

## 技术栈

| 项 | 版本/说明 |
|----|-----------|
| 语言 | Kotlin 2.0.20 |
| UI | Jetpack Compose（Material 3，BOM 2024.09.03） |
| 数据库 | Room 2.6.1（KSP），平滑版本迁移 |
| 调度 | AlarmManager `setAlarmClock` |
| 构建 | Gradle 8.7 + AGP 8.5.2，JDK 17 |
| 兼容 | minSdk 26（Android 8.0）～ targetSdk 34 |

## 项目结构

```
app/src/main/java/com/example/multitimealarm/
├── data/          # Room：AlarmTask(任务) + AlarmTime(时间点) 双表与迁移
├── scheduler/     # 调度核心：AlarmScheduler / AlarmReceiver / BootReceiver
├── notify/        # 高优先级全屏 Intent 通知
├── ring/          # 响铃页（铃声、振动、贪睡）
├── ui/
│   ├── list/      # 闹钟 / 小憩 Tab 页
│   ├── edit/      # 闹钟编辑页 + 时分秒数字选择器
│   └── settings/  # 设置页（主题、电池优化）
└── util/          # 下一次触发时间计算（含单元测试覆盖）
```

## 本地构建

1. 克隆仓库：`git clone git@wapxw:sundys/MultiTimeAlarm.git`
2. 用 Android Studio（Koala+，自带 JDK 17）打开工程根目录
3. 在 `local.properties` 中配置 SDK 路径：`sdk.dir=<你的 Android SDK 路径>`（该文件不入库）
4. Run ▶ 安装到设备；或命令行：`./gradlew assembleDebug`

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

## 自动编译发布（部署摘要）

项目使用 GitHub Actions 自动编译并发布 Release，流程如下：

1. **触发**：推送 `v*` 格式的 tag（如 `v1.1.0`）即自动触发；也支持在 Actions 页面手动触发
2. **编译**：Ubuntu 环境使用 Gradle Wrapper 执行 `assembleDebug assembleRelease`，产出：
   - `MultiTimeAlarm-<tag>-debug.apk`（可直接安装）
   - `MultiTimeAlarm-<tag>-release-unsigned.apk`（未签名 release，需自行签名后安装）
3. **发布**：自动创建 GitHub Release，附件为上述 APK；**更新摘要自动取自 `CHANGELOG.md` 最顶部的一个小节**，方便区分每个版本改了什么

发版操作：

```bash
# 1. 在 CHANGELOG.md 顶部添加新版本小节（## vX.Y.Z 开头）
# 2. 提交并推送
git add CHANGELOG.md && git commit -m "release: vX.Y.Z" && git push
# 3. 打 tag 触发自动发布
git tag vX.Y.Z && git push origin vX.Y.Z
```

## APK 签名（自动发布）

release 签名通过**环境变量**注入，本地与 CI（GitHub Actions）通用，未配置时自动降级为不签名（产物文件名带 `-unsigned`）。

| 环境变量 | 说明 |
|----------|------|
| `SIGNING_STORE_FILE` | keystore 文件路径（CI 中固定为 `keystore.jks`） |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | 签名 key 别名 |
| `SIGNING_KEY_PASSWORD` | key 密码 |
| `SIGNING_KEYSTORE_BASE64` | **仅 CI**：keystore 文件的 Base64 内容（存 GitHub Secrets） |

**CI 配置**：仓库 Settings → Secrets and variables → Actions，添加以上 5 个 Secret。工作流会先把 `SIGNING_KEYSTORE_BASE64` 解码为 `keystore.jks`，再注入其余 4 个变量完成签名，产物为已签名的 `MultiTimeAlarm-<tag>-release.apk`。

**本地签名**：生成/准备好 keystore 后导出前 4 个变量再编译即可：

```bash
export SIGNING_STORE_FILE=/path/to/keystore.jks
export SIGNING_STORE_PASSWORD=你的keystore密码
export SIGNING_KEY_ALIAS=你的key别名
export SIGNING_KEY_PASSWORD=你的key密码
./gradlew assembleRelease
```

生成新 keystore：`keytool -genkeypair -v -keystore keystore.jks -alias 别名 -keyalg RSA -validity 10000`
生成 CI 用的 Base64：`base64 -w0 keystore.jks`（内容填入 `SIGNING_KEYSTORE_BASE64`）

## 首次使用授权说明

- **通知权限**（Android 13+）：首次启动自动发起授权请求
- 调度不需要"闹钟和提醒"特殊权限，安装即可用
- 建议在 设置 → 忽略电池优化 中授权，并在自启动设置中允许本应用，避免国产 ROM 后台管控拦截闹钟
