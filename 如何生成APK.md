# 怎么把这个工程变成能装的 APK

> 全程不需要装任何开发工具，也不用碰命令行。
> 唯一需要的是一个 GitHub 账号（免费注册）。

---

## 为什么不能直接给你一个 APK

编译安卓应用需要 **Android SDK**（约 1.5 GB）+ Gradle 构建工具 + 几十个依赖包，
而且必须联网从 Google 的源下载。所以编译这一步得换个地方做。

**最优解是让 GitHub 的服务器替你编译** —— 它们是免费提供的，跑完把 APK 给你下载。

我已经把自动构建的配置写好了（`.github/workflows/build-apk.yml`），
你只要把代码传上去，剩下的它自己完成。

---

## 第一步：注册 GitHub 账号

如果已经有，跳到第二步。

1. 打开 https://github.com/signup
2. 填邮箱、密码、用户名
3. 去邮箱点验证链接

**全程免费，不需要绑卡。**

---

## 第二步：创建一个新仓库

1. 登录后，点右上角 **`+`** → **New repository**
2. **Repository name** 填：`sticker-ferry`
3. 选择 **Public**（公开）或 **Private**（私有）都行
   - 💡 选 Private 的话，Actions 构建**依然免费**
4. **不要**勾选 "Add a README file"（我们已经有自己的）
5. 点 **Create repository**

---

## 第三步：把工程代码传上去

### 📂 你要传的文件在这里

```
C:\Users\工作台\WorkBuddy\2026-11-01-18-38-24\StickerFerry\
```

**要传的是这个文件夹「里面的东西」，一共 12 项：**

```
StickerFerry/           ← 打开这个文件夹，传里面的内容
├── .github/            ⚠️ 隐藏文件夹，最关键！
│   └── workflows/
│       └── build-apk.yml    ← 自动编译的配置
├── app/                ← 主程序代码
├── gradle/             ← Gradle 配置
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties.example
├── README.md
├── 如何生成APK.md
└── .gitignore
```

---

### ⚠️ 最容易踩的坑：不要多套一层

| 拖法 | 结果 |
|---|---|
| ❌ 拖 `StickerFerry` **整个文件夹** | 仓库里多一层 `StickerFerry/`，Actions 找不到工程，**构建失败** |
| ✅ 进到文件夹里，**全选内容**再拖 | 结构正确，构建成功 |

**判断标准**：传完之后，仓库首页第一个文件应该是 **`settings.gradle.kts`**，
而不是先看到一个叫 `StickerFerry` 的文件夹。

---

### 方法 A：网页拖拽上传（最简单，推荐）

**第 1 步：让隐藏文件现形**

`.github` 文件夹是隐藏的，看不到就没法传。

- **Windows**：打开文件资源管理器 → 顶部「查看」→ 勾选「隐藏的项目」
- **macOS**：在 Finder 里按 `Cmd + Shift + .`（句号）

**第 2 步：进到文件夹里面**

双击 `StickerFerry` 文件夹，进到**里面**。这时候你应该能直接看到 `app`、`gradle`、`build.gradle.kts` 这些东西。

**第 3 步：全选**

按 `Ctrl + A`（macOS 是 `Cmd + A`）选中所有项目。
确认 `.github` 也在选中范围里。

**第 4 步：拖进去**

1. 回到 GitHub 刚创建的空仓库页面
2. 点页面上的 **uploading an existing file** 链接
3. 把刚选中的 12 项**拖进**虚线框区域
4. 等所有文件列出来（会看到 `app/xxx`、`.github/xxx` 这样的路径）
5. 拉到底部，**Commit changes** 输入框填：`首次提交`
6. 点绿色按钮 **Commit changes**

**第 5 步：核对**

传完后看仓库首页，应该长这样：

```
✅ 正确
.github/
app/
gradle/
build.gradle.kts
settings.gradle.kts
gradlew
...（共 12 项）
```

```
❌ 错误（多了一层）
StickerFerry/     ← 不该出现这个
```

**如果错了怎么办**：删掉仓库重新建一个，或者把 `StickerFerry` 文件夹重命名后再传。

---

### 方法 B：用 Git 命令行（如果你装了 Git）

```bash
cd "C:/Users/工作台/WorkBuddy/2026-11-01-18-38-24/StickerFerry"
git init
git add .
git commit -m "首次提交"
git branch -M main
git remote add origin https://github.com/你的用户名/sticker-ferry.git
git push -u origin main
```

这个方法**不用管隐藏文件夹**，`git add .` 会自动把 `.github` 一起收进去。

---

### 💡 如果 `.github` 死活传不上去

这是新手最常卡的地方。**有个备用的补救办法**：

1. 先把其他 11 项正常传上去
2. 在仓库页面点 **Add file** → **Create new file**
3. 文件名输入框里填：`.github/workflows/build-apk.yml`
   - 注意：输入 `/` 的时候 GitHub 会自动帮你建文件夹
4. 把 `如何生成APK.md` 隔壁那个 `build-apk.yml` 的内容**全部粘贴**进去
5. 拉到下面点 **Commit changes**

这样也能达到同样效果。

---

## 第四步：等它自动编译

代码一推上去，GitHub 会**自动开始编译**。

1. 进你的仓库页面
2. 点顶部 **Actions** 标签
3. 会看到一条正在跑的记录：**构建 APK**
4. 等它转圈（首次约 **5～10 分钟**，要下载依赖）

**状态说明**
- 🟡 黄点 = 正在跑
- ✅ 绿勾 = 成功
- ❌ 红叉 = 失败（往下看"出错了怎么办"）

---

## 第五步：下载 APK

构建成功后：

1. 点进那条成功的记录
2. 拉到页面**最下方**
3. 找到 **Artifacts** 区域
4. 点 **StickerFerry-APK** 下载
5. 得到一个 zip，解压后里面是 `StickerFerry-debug.apk`

> ⚠️ 下载需要**登录 GitHub**，这是 GitHub 的限制。

---

## 第六步：装到手机上

1. 把 APK 传到手机
   - 数据线拷贝
   - 微信"文件传输助手"发给自己
   - 网盘 / QQ 发送
2. 在手机上找到这个文件，点开
3. 系统会提示**"未知来源应用"**，选择**允许**
   - 这是正常的 —— 自己编译的应用不在应用商店白名单里
4. 点安装
5. 桌面出现 **表情摆渡** 图标 ✅

---

## 出错了怎么办

### 情况一：Actions 页面显示红叉

点进失败的记录，展开报错的那一步，看红字。常见原因：

| 报错内容 | 原因 | 解决 |
|---|---|---|
| `Could not resolve` | 网络拉依赖失败 | 再点一次 **Re-run jobs**，重试即可 |
| `gradlew: Permission denied` | 脚本没执行权限 | workflow 里已处理，重跑一次 |
| `Compilation error` | 代码问题 | 把完整报错发给我，我来修 |

### 情况二：Actions 页面显示「开始使用 GitHub Actions」引导页

这是最常见的情况，意思是**仓库里没有 workflow 文件被识别到**。`Actions` 标签页本身只显示工作流，所以哪怕代码传得好好的，只要少了这一个文件，这页就是空的。

原因通常只有一个：`.github` 是**隐藏文件夹**，网页拖拽上传时被漏掉了。

**最快的修法 —— 在网页上直接建这个文件，不用管隐藏文件夹：**

1. 回到仓库**首页**（`github.com/GZT-0721/apk`），**不要**停在 Actions 页面
2. 点右上方的 **Add file** → **Create new file**
3. 在文件名输入框里，**原样输入**这一行：

   ```
   .github/workflows/build-apk.yml
   ```

   > 每打一个 `/`，GitHub 会自动把它变成一层文件夹 —— 这是正常的，说明路径建对了。
   > 如果打不开头的 `.`，先输 `x.github/workflows/build-apk.yml`，再把开头的 `x` 删掉。

4. 在下方大的编辑区里，粘贴 `粘贴到GitHub的workflow.yml` 的全部内容
5. 拉到最底下，点绿色的 **Commit changes...** → 再点 **Commit changes**
6. 回到 **Actions** 标签页，几秒后会出现一条叫「构建 APK」的记录，正在转圈

**怎么确认建对了？**

点仓库首页，如果能看到一个 `.github` 文件夹，进去是 `workflows`，再进去是 `build-apk.yml` —— 就对了。

**还要确认一件事**：仓库里有没有 `settings.gradle.kts`？

- 有 → 说明代码传上去了，只差这一个文件
- 没有 → 代码也没传上去，得先按第三步把工程文件拖上去

如果代码传成了 `StickerFerry/app/...` 这种多套一层的结构，**不用管** —— 现在的 workflow 会自动找到工程目录，能正常编译。

**其他排查点**
- 文件名后缀必须是 `.yml`，不能是 `.yaml.txt`（Windows 默认隐藏已知扩展名，容易出这个错）
- 文件名大小写敏感，`build-apk.yml` 全部小写

### 情况三：安装时提示"应用未安装"

- 可能是下载的 APK 不完整 → 重新下载
- 可能是手机系统版本太低 → 需要 **Android 8.0** 及以上

### 情况四：装上了但打不开 / 闪退

把手机连电脑，用 `adb logcat` 看日志发给我。
或者直接在手机上抓崩溃日志。

---

## 想改代码怎么办

改完直接推到 GitHub，会自动重新编译，再去 Actions 下载新的 APK。

**每次改完都自动出一个新包，不用手动做任何事。**

---

## 想要"正式版"APK（可选）

上面的流程产出的是 **Debug 版**，能正常用，但：

- 文件名带 `debug`
- 体积略大（约多 30%）
- 不能上架应用商店

**自用的话 Debug 版完全够用**，不用折腾签名。

如果你想要 Release 版（体积更小、运行更快），需要生成一个签名密钥：

```bash
keytool -genkey -v -keystore my-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias stickerferry
```

然后在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 里添加四个密钥：

| Secret 名 | 填什么 |
|---|---|
| `SIGNING_KEY` | `.jks` 文件的 base64 编码 |
| `KEY_ALIAS` | `stickerferry` |
| `KEY_STORE_PASSWORD` | 你设的密码 |
| `KEY_PASSWORD` | 你设的密码 |

这个配置 workflow 里已经预留了位置，配上就会自动产出 Release 包。

> ⚠️ **密钥文件千万别丢**，也别提交到仓库。丢了就没法给同一个应用升级了。

---

## 一句话总结

**传代码 → 等十分钟 → 下 APK → 装上。**

我这边把能自动化的都自动化了，剩下就是上传这一步需要你来。
