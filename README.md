# Yatori Android

Yatori Android 是基于 [yatori-dev/yatori-go-console](https://github.com/yatori-dev/yatori-go-console) 和 [Yatori Go Desktop](https://github.com/yuanglove/yatori-go-desktop) 改造的 Android 版学习任务管理工具。

原项目提供命令行和网页模式的学习任务执行能力，桌面版在此基础上增加了 React 图形界面、账号管理、任务控制、日志中心、课程进度和全局设置。本项目继续把桌面版界面与 Go 核心逻辑移植到 Android，通过 WebView + gomobile 提供手机端可用的操作入口。

> 本项目由 yatori-go-console 和 Yatori Go Desktop 改造而来。感谢原项目作者和相关开源依赖。

## 当前版本

v0.3.7

## v0.3.7 更新重点

- 新建独立 Android 仓库，用于保存 Android 工程、公告、远程策略和 APK Release。
- 移动端界面适配：优化仪表盘、账号管理、日志中心、课程进度、全局设置和关于页面。
- 支持 Android 深色/浅色主题，并跟随系统深色模式。
- 修复账号导入、配置导入导出、课程进度刷新卡顿、日志读取、账号编辑弹窗位置等移动端问题。
- 使用自定义启动器图标，替换默认 Android 图标。
- Release 包体积相较 debug 包明显减小，并仅保留 Android 需要的资源。
- 公告、版本检查和远程策略均切换到本仓库。

## 本项目做了什么

- 使用 Android WebView 承载 React + TypeScript 前端界面。
- 使用 gomobile 将 Go 侧核心逻辑编译为 Android 可调用的 AAR。
- 提供账号管理、任务控制、日志中心、课程进度、全局设置、关于本应用等页面。
- 支持配置导入/导出、账号数据库导入/导出，方便在桌面版和 Android 版之间迁移数据。
- 支持 GitHub Release 自动检测新版本。
- 支持远程公告、远程停用应用、强制更新和普通更新提醒。
- 配置、数据库和日志默认保存到 Android 应用私有目录：

```text
/data/user/0/app.yatori.android/files/yatori
```

普通用户通常无法直接从文件管理器访问该私有目录，请通过应用内导入、导出和分享功能处理数据。

## 功能状态

| 功能 | 状态 | 说明 |
| --- | --- | --- |
| Android 界面 | 支持 | WebView + React 移动端界面 |
| 账号管理 | 支持 | 增删改账号、导入桌面版账号数据库 |
| 任务控制 | 支持 | 启动、停止、状态展示 |
| 日志中心 | 支持 | 实时日志、历史日志、等级过滤 |
| 课程进度 | 支持 | 支持已接入平台的课程进度查看和刷新 |
| 全局设置 | 支持 | AI、题库、邮件、日志、主题等配置 |
| 配置导入导出 | 支持 | Android 文件选择器/分享能力 |
| 账号数据导入导出 | 支持 | 导入/导出 `yatori.db` |
| 深色/浅色主题 | 支持 | Android 端跟随系统夜间模式 |
| 远程公告 | 支持 | 从本仓库公告文件获取 |
| 远程策略 | 支持 | 支持远程停用、强制更新和普通更新提醒 |
| 学习通 | 已实测可用 | 支持任务执行、日志、停止、章节测验答题等流程 |
| 英华学堂 | 已实测可用 | 可正常登录并进入课程流程 |
| 海旗科技 | 已实测可用 | worker 运行正常 |
| WeLearn 随行课堂 | 已实测可用 | 支持学时模式和完成度模式 |
| 智慧职教（ICVE） | 已接入并持续完善 | 支持 Cookie 登录、课程进度、测验/作业识别、题库/AI 答题和保存/提交诊断 |
| 其他原项目平台 | 支持入口 | 复用原项目 logic，具体效果需按账号继续实测 |

## 项目结构

```text
yatori-go-android/
├── app/
│   ├── build.gradle.kts
│   ├── libs/
│   │   └── mobileapi.aar              本地生成，不提交到仓库
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                    React 前端构建产物
│       ├── java/app/yatori/android/
│       │   ├── MainActivity.kt         WebView、文件选择、初始化入口
│       │   ├── YatoriBridge.kt         JS 与 gomobile API 桥接
│       │   ├── EngineRegistry.kt       Go engine 持有
│       │   └── YatoriForegroundService.kt
│       └── res/                        主题、图标、FileProvider 配置
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew.bat
```

## 从源码构建

本仓库不提交 `app/libs/mobileapi.aar`，因为生成后的 AAR 超过 GitHub 普通文件大小限制。需要先从桌面版/共享 Go 代码生成 AAR，再构建 Android APK。

常用构建入口在工作区脚本中：

```powershell
powershell -ExecutionPolicy Bypass -NoProfile -File "D:\AI\智能体工作目录\claude工作目录\学习\YatoriAndroidWork\scripts\build-release-apk.ps1"
```

构建产物：

```text
D:\AI\智能体工作目录\claude工作目录\学习\YatoriAndroidWork\release\app-release-debugsigned.apk
```

## 数据路径

Android 默认数据目录：

```text
/data/user/0/app.yatori.android/files/yatori/
├── config.yaml
├── yatori.db
└── assets/log/
```

该目录是 Android 应用私有目录，普通文件管理器一般无法直接访问。请使用应用内“导入配置”“导出配置”“导入账号”“导出账号”等功能迁移数据。

## 已知限制

- Android 版依赖 WebView，个别系统 WebView 版本过旧时可能出现白屏或渲染异常。
- APK 体积主要来自 Go/gomobile 生成的 `libgojni.so` 和核心依赖。
- Android 无法静默安装更新包，强制更新只能阻止旧版本继续使用，并引导用户前往 Release 下载新版。
- 各平台实际任务执行能力取决于上游 yatori-go-console 的实现和平台接口状态。
- 不提供验证码破解、人脸绕过、考试作弊等能力。

## 免责声明

本项目代码已开源，仅供学习与技术交流使用，严禁任何形式的倒卖、贩卖或商业牟利。

请在遵守法律法规、平台规则和账号授权范围的前提下使用本软件。任何个人或组织使用本项目代码或软件进行的违法违规行为，均与项目作者无关，相关责任由使用者自行承担。

如本项目内容对相关公司或平台造成影响，请通过 GitHub 仓库联系，我会及时处理。

## 致谢

- [yatori-dev/yatori-go-console](https://github.com/yatori-dev/yatori-go-console) - 核心学习逻辑来源
- [yuanglove/yatori-go-desktop](https://github.com/yuanglove/yatori-go-desktop) - 桌面版界面与功能来源
- [React](https://react.dev/) + [TypeScript](https://www.typescriptlang.org/)
- [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)

## License

本项目遵循 MIT License。原项目版权归原作者所有。
