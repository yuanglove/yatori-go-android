# Yatori Android

Yatori Android 是 `yatori-go-desktop` 的移动端适配仓库。这个仓库只负责 Android 壳层、权限、后台服务、通知栏、WebView/原生桥接、APK 构建与发布。

平台业务逻辑只在 `yatori-go-desktop` 维护，并通过桌面仓库发布的 `mobilecore` AAR 提供给 Android 使用。

## 维护边界

`yatori-go-desktop` 是唯一核心仓库，负责：

- ICVE、学习通等平台接口
- 题库协议和答案解析
- AI 请求
- 答题、保存、提交逻辑
- 任务调度
- 日志格式
- `mobilecore` AAR 产物

`yatori-go-android` 只负责：

- Android WebView / 原生桥接
- 移动端 UI 适配
- Android 权限
- 前台服务和通知栏
- APK 构建和发布
- 消费 `yatori-go-desktop` 发布的 `yatori-mobile.aar`

本仓库禁止复制或单独修改 ICVE、学习通、题库、AI、答题、保存、提交等业务逻辑。

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

`app/libs/*.aar` 不作为源码维护。它是桌面核心仓库的发布产物。

## 统一响应协议

Android 只调用 `mobilecore.Mobilecore` 暴露的方法。所有方法必须返回统一 JSON：

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

## 从桌面仓库生成 Core

```powershell
cd "D:\AI\智能体工作目录\claude工作目录\学习\yatori-go-desktop-V0.3.4-clean"

$env:PATH='D:\AI\智能体工作目录\claude工作目录\学习\YatoriAndroidWork\tools\bin;C:\msys64\ucrt64\bin;C:\Users\35862\go\bin;' + $env:PATH
$env:CGO_ENABLED='1'

.\scripts\build-mobilecore.ps1 -Version 0.3.8 -Target android/arm64 -ApiSchemaVersion 1
```

## 导入 Core 到 Android

```powershell
cd "D:\AI\智能体工作目录\claude工作目录\学习\YatoriAndroidWork\yatori-go-android"

.\scripts\import-desktop-core.ps1 `
  -DesktopReleaseDir "D:\AI\智能体工作目录\claude工作目录\学习\yatori-go-desktop-V0.3.4-clean\release" `
  -Version 0.3.8 `
  -ExpectedApiSchemaVersion 1
```

## 构建 APK

```powershell
cd "D:\AI\智能体工作目录\claude工作目录\学习\YatoriAndroidWork\yatori-go-android"

.\scripts\build-apk.ps1 -ImportCore -Version 0.3.8 -ExpectedApiSchemaVersion 1
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 当前状态

已接入能力：

- 配置读取、保存、导入、导出
- 账号新增、编辑、删除、列表
- 账号数据库导入、导出
- 任务启动、停止、状态查询
- 日志读取
- AI 测试
- 题库测试
- 课程列表接口
- core 版本与 schema 校验

后续优先事项：

1. 将核心 API 继续稳定到 `api-schema.json`。
2. 每次桌面核心改动后重新生成 AAR 与 checksum。
3. Android 只导入新 AAR 并构建 APK。
4. 做移动端裁剪构建，减少 `libgojni.so` 体积。

## License

MIT License。原项目版权归原作者所有。
