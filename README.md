# Yatori Android

Yatori Android 是 `yatori-go-desktop` 的移动端适配仓库，用来把桌面版核心能力带到 Android 手机上使用。

本仓库只负责 Android 壳层、权限、前台服务、通知栏、WebView/原生桥接、APK 构建和发布。平台业务逻辑统一在 `yatori-go-desktop` 维护，并通过桌面仓库发布的 `mobilecore` AAR 提供给 Android 调用。

> 本项目仅用于个人已授权账号的学习任务管理和技术交流。请遵守法律法规、平台规则和账号授权范围。

## 当前版本

v0.3.8

## 本项目做了什么

- 使用 Android 原生工程承载 Yatori 移动端界面。
- 通过 WebView 加载移动端前端资源，并通过 `YatoriBridge` 暴露 Android 原生能力。
- 通过 `gomobile` 生成的 `yatori-mobile.aar` 调用桌面版 Go 核心。
- 支持账号、配置、日志、任务状态等数据和桌面核心保持同一套结构。
- 支持导入桌面仓库发布的 core 元数据，并校验版本、schema 和 SHA256。
- 使用 `YatoriForegroundService` 承载后台任务状态和停止能力。
- 提供 APK 构建脚本，方便从桌面版 release 产物同步核心后重新打包。

## 功能状态

| 功能 | 状态 | 说明 |
| --- | --- | --- |
| Android 壳层 | 支持 | 原生 Activity + WebView |
| 移动端 UI | 支持 | 适配手机竖屏使用 |
| Go 核心桥接 | 支持 | 通过 `yatori-mobile.aar` 调用 `mobilecore` |
| 账号列表 | 支持 | 调用 Go core 的账号接口 |
| 账号导入/导出 | 支持 | 支持账号数据库 Base64 导入导出 |
| 配置读取/保存 | 支持 | 统一走 core JSON API |
| 配置导入/导出 | 支持 | 通过 Android 文件选择和分享能力实现 |
| 任务启动/停止 | 接入中 | Android 侧有桥接和前台服务，实际能力取决于 `mobilecore` 暴露范围 |
| 日志读取 | 支持 | 从 Go core 读取日志数据 |
| AI 测试 | 支持 | 调用 core 测试接口 |
| 题库测试 | 支持 | 调用 core 测试接口 |
| 课程列表 | 接入中 | 需要桌面核心继续暴露完整移动端 API |

## 维护边界

`yatori-go-desktop` 是唯一核心仓库，负责维护：

- ICVE、学习通等平台接口
- 题库协议和答案解析
- AI 请求
- 答题、保存、提交逻辑
- 任务调度
- 日志格式
- `mobilecore` AAR、schema 和版本元数据

`yatori-go-android` 只负责：

- Android WebView / 原生桥接
- 移动端 UI 适配
- Android 权限
- 前台服务和通知栏
- 文件导入、导出和分享
- APK 构建与发布
- 消费 `yatori-go-desktop` 发布的 `yatori-mobile.aar`

本仓库禁止复制或单独修改 ICVE、学习通、题库、AI、答题、保存、提交等平台业务逻辑。

## 项目架构

```text
yatori-go-android/
├── app/
│   ├── build.gradle.kts                  Android 应用模块配置
│   ├── libs/
│   │   └── yatori-mobile.aar             桌面仓库发布的 Go mobile core，本地构建产物，不进源码维护
│   └── src/main/
│       ├── AndroidManifest.xml           权限、Activity、Service、FileProvider 声明
│       ├── assets/
│       │   ├── index.html                WebView 入口页面
│       │   ├── assets/                   移动端前端静态资源
│       │   └── core/                     core schema、版本和 checksum 元数据
│       ├── java/app/yatori/android/
│       │   ├── MainActivity.kt           主界面、WebView、文件导入导出
│       │   ├── YatoriBridge.kt           JS 与 Go core 的统一桥接层
│       │   ├── YatoriForegroundService.kt 前台服务和任务控制
│       │   └── EngineRegistry.kt         core 初始化状态
│       └── res/                          图标、主题、FileProvider 路径等资源
├── gradle/wrapper/                       Gradle Wrapper
├── scripts/
│   ├── import-desktop-core.ps1           从桌面 release 导入 AAR 和元数据
│   └── build-apk.ps1                     导入 core 并构建 APK
├── announcement.json                     远程公告
├── app-policy.json                       远程版本策略
└── README.md
```

## 数据路径

Android 应用私有数据目录：

```text
/data/user/0/app.yatori.android/files/yatori/
```

常见数据包括：

```text
config.yaml
yatori.db
assets/log/
```

这些数据由 Android 应用私有目录管理，普通文件管理器通常不能直接访问。需要导入、导出时请通过应用内功能或 Android 调试工具处理。

## Core 产物

Android 构建依赖桌面仓库生成的四个产物：

```text
yatori-go-desktop/release/
├── yatori-mobile-v0.3.8.aar
├── api-schema.json
├── yatori-core-version.json
└── yatori-mobilecore-checksums.json
```

导入 Android 后的位置：

```text
app/libs/yatori-mobile.aar
app/src/main/assets/core/api-schema.json
app/src/main/assets/core/yatori-core-version.json
app/src/main/assets/core/yatori-mobilecore-checksums.json
```

`app/libs/*.aar` 不作为源码维护，它是桌面核心仓库的发布产物。

## 统一响应协议

Android 只调用 `mobilecore.Mobilecore` 暴露的方法。所有方法应返回统一 JSON：

成功：

```json
{
  "ok": true,
  "data": {},
  "error": "",
  "code": ""
}
```

失败：

```json
{
  "ok": false,
  "data": null,
  "error": "错误信息",
  "code": "ERROR_CODE"
}
```

## 版本绑定

每个 APK 都必须携带桌面核心版本信息：

```json
{
  "androidVersion": "0.3.8",
  "desktopCoreVersion": "0.3.8",
  "coreCommit": "5dc05da",
  "apiSchemaVersion": 1,
  "aarFile": "yatori-mobile-v0.3.8.aar",
  "aarSha256": "...",
  "target": "android/arm64",
  "androidApi": 24
}
```

导入脚本会校验：

- `desktopCoreVersion` 是否等于目标版本
- `apiSchemaVersion` 是否等于 Android 支持版本
- `aarFile` 是否匹配
- AAR SHA256 是否匹配
- checksum 文件是否完整

校验失败时禁止继续构建。

## 构建

### 从桌面仓库生成 Core

```powershell
cd "D:\AI\智能体工作目录\claude工作目录\学习\yatori-go-desktop-V0.3.4-clean"

$env:PATH='D:\AI\智能体工作目录\claude工作目录\学习\YatoriAndroidWork\tools\bin;C:\msys64\ucrt64\bin;C:\Users\35862\go\bin;' + $env:PATH
$env:CGO_ENABLED='1'

.\scripts\build-mobilecore.ps1 -Version 0.3.8 -Target android/arm64 -ApiSchemaVersion 1
```

### 导入 Core 到 Android

```powershell
cd "D:\AI\智能体工作目录\claude工作目录\学习\YatoriAndroidWork\yatori-go-android"

.\scripts\import-desktop-core.ps1 `
  -DesktopReleaseDir "D:\AI\智能体工作目录\claude工作目录\学习\yatori-go-desktop-V0.3.4-clean\release" `
  -Version 0.3.8 `
  -ExpectedApiSchemaVersion 1
```

### 构建 APK

```powershell
cd "D:\AI\智能体工作目录\claude工作目录\学习\YatoriAndroidWork\yatori-go-android"

.\scripts\build-apk.ps1 -ImportCore -Version 0.3.8 -ExpectedApiSchemaVersion 1
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
release/Yatori-Android-v0.3.8-arm64-debug-signed.apk
```

## 已知限制

- Android 版当前重点是移动端适配和 core 接入，完整任务能力取决于桌面仓库 `mobilecore` 暴露的 API 范围。
- Android 仓库不维护平台业务逻辑，因此 ICVE、学习通、题库、AI 等问题应优先在 `yatori-go-desktop` 修复。
- `libgojni.so` 来自 gomobile，会显著增加 APK 体积，这是 Go runtime 和核心逻辑打包进 Android 的结果。
- 当前默认构建 `arm64-v8a`，主要面向现代 Android 手机。
- 不提供验证码破解、人脸识别绕过、考试作弊等能力。
- 本项目仅用于个人已授权账号的学习任务管理和技术交流。

## 免责声明

本项目代码已开源，仅供学习与技术交流使用，严禁任何形式的倒卖、贩卖或商业牟利。

请在遵守法律法规、平台规则和账号授权范围的前提下使用本软件。任何个人或组织使用本项目代码或软件进行的违法违规行为，均与项目作者无关，相关责任由使用者自行承担。

如本项目内容对相关公司或平台造成影响，请通过 GitHub 仓库联系，我会及时处理。

## 更新日志

见 [Releases](https://github.com/yuanglove/yatori-go-android/releases)。

## 致谢

- [yatori-go-desktop](https://github.com/yuanglove/yatori-go-desktop) - 桌面版和唯一核心业务仓库
- [yatori-dev/yatori-go-console](https://github.com/yatori-dev/yatori-go-console) - 核心学习逻辑来源
- [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile) - Go 到 Android 的绑定工具
- Android、Kotlin、WebView 和 Gradle 生态

## License

MIT License。原项目版权归原作者所有。
