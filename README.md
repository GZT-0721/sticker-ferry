# 表情摆渡 · StickerFerry

> QQ / 微信表情包互通 + AI 造表情 —— Android 自用工具

---

## 🚀 想直接要 APK？看这个

**不想装开发工具、不想碰命令行 —— 走云端自动构建。**

👉 **详细图文步骤：[如何生成APK.md](./如何生成APK.md)**

一句话流程：**上传代码到 GitHub → 等十分钟 → 下载 APK → 装到手机。**

配置我都写好了（`.github/workflows/build-apk.yml`），你只需要上传代码这一步。

---

## 用 Android Studio 本地开发

### 1. 打开工程

```
Android Studio → File → Open → 选择 StickerFerry 文件夹
```

首次同步会自动下载依赖（需联网，几分钟）。

### 2. 补全 gradle wrapper

工程里带了 `gradlew` 脚本，但**没有 `gradle-wrapper.jar`**（二进制文件不便直接写入）。
Android Studio 打开时会提示自动补全，或手动跑一次：

```bash
gradle wrapper --gradle-version 8.9
```

### 3. 运行

连上手机（开启 USB 调试），点绿色三角。
想导出 APK：**Build → Build Bundle(s) / APK(s) → Build APK(s)**

**环境要求**
- Android Studio Ladybug (2024.2) 或更新
- JDK 17
- 手机系统 Android 8.0 (API 26) 及以上

---

## 第一次打开要做什么

1. **授权目录**（必须）
   进「表情库」→ 点「🐧 QQ 表情」统计卡 → 在系统选择器里定位到 QQ 表情目录。
   微信同理。**Android 11+ 必须手动授权，这是系统限制，绕不过。**

2. **配置 AI**（可选，想用 AI 生成才需要）
   进「设置」→「AI 模型配置」→ 填入你自己的 API Key。
   费用按用量计，每张图几毛钱。Key 用系统级加密存在本机。

3. **开始用**
   - 表情库：浏览、筛选、多选
   - 投放：批量送 QQ（全自动）/ 送微信（三步引导）
   - AI 工坊：生成表情 / 找素材 / 图上加字

---

## 工程结构

```
app/src/main/java/com/stickerferry/
├── MainActivity.kt              入口 + 底部导航
├── StickerFerryApp.kt           Application（刻意不做任何 SDK 初始化）
│
├── data/                        数据层
│   ├── Sticker.kt               统一模型 + 扫描报告
│   ├── StickerRepository.kt     并行扫描两个来源
│   ├── QQSource.kt              QQ 目录读取
│   └── WeChatSource.kt          微信目录读取（只读未加密文件）
│
├── ai/                          AI 能力
│   ├── ImageGenClient.kt        文生图接口
│   ├── PromptBuilder.kt         提示词模板 + 敏感词拦截
│   ├── StockSearchClient.kt     双模式图库检索
│   └── TextOverlay.kt           Canvas 图上加字
│
├── transfer/                    跨端投放
│   ├── QQImporter.kt            写入 QQ（全自动 + 逐张容错）
│   └── WeChatGuide.kt           微信三步引导（半自动）
│
├── util/                        工具
│   ├── SafHelper.kt             SAF 目录权限与读写
│   ├── ImageHash.kt             dHash 感知哈希去重
│   ├── ClipboardHelper.kt       剪贴板 + 唤起 App
│   └── SecureStore.kt           Key 加密存储
│
└── ui/                          界面（Compose）
    ├── MainViewModel.kt         全局共享状态
    ├── Components.kt            复用组件
    ├── LibraryScreen.kt         表情库
    ├── StudioScreen.kt          AI 工坊（三模块）
    ├── TransferScreen.kt        投放
    ├── AiConfigScreen.kt        AI 配置
    ├── SettingsScreen.kt        设置
    └── theme/Theme.kt           配色主题
```

---

## 能力边界（诚实版）

| 能力 | 状态 |
|---|---|
| 读取 QQ 本地表情 | ✅ |
| 读取微信本地表情 | ⚠️ 仅未加密缓存，加密文件不做破解 |
| 批量导出 / 去重 / 打包 | ✅ |
| 投放到 QQ | ✅ 自动写入 + 引导导入 |
| 投放到微信 | ⚠️ 半自动，最后两步人工 |
| 相册图片转 QQ 表情 | ✅ |
| 图上加字 | ✅ 纯本地 |
| AI 生成表情 | ✅ 需自配 Key |
| 联网找素材 | ⚠️ 双模式，默认保守 |
| iOS | ❌ 沙盒封闭 |
| 上架应用商店 | ❌ 会违反腾讯服务协议 |

---

## 三条不能碰的红线

1. **不用无障碍服务模拟点击微信界面** —— 外挂行为，风控会限制账号登录
2. **不硬编码 API Key** —— 反编译即泄露，会被盗刷
3. **不静默上传任何数据** —— 表情是私密数据，"不联网"要从架构上保证

---

## 自用声明

本工具仅用于管理你自己设备上的表情文件。

- 自用、不传播，是合理使用
- **不要公开分发、不要收费、不要帮他人绕过限制**
- **不要上架应用商店**（绕过客户端读写违反《腾讯服务协议》）

---

## 已知需要你补充的部分

代码是完整可运行的骨架，但有几处需要你按实际情况填写：

| 位置 | 需要填什么 |
|---|---|
| `ImageGenClient.endpoint()` | 你选的服务商真实接口地址 |
| `ImageGenClient.parseImageUrls()` | 按该服务商的返回格式调整 |
| `StockSearchClient.PEXELS_KEY` | 去 pexels.com 免费申请 |
| `StockSearchClient.PIXABAY_KEY` | 去 pixabay.com 免费申请 |
| `StockSearchClient.searchWeb()` | 如需全网搜索，接入合规聚合 API |

这些地方我都加了注释标明，搜索对应的 `TODO` 或 `⚠️` 就能找到。
