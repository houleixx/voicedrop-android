# VoiceDrop Android

**把口述录音自动变成文章的 Android 客户端。**

VoiceDrop Android 是 VoiceDrop 的 Android 版本。它参考 iOS 版本实现，复用同一套后端接口、文章格式和分享链路。

- iOS 项目：https://github.com/jianshuo/voicedrop
- English: [README.en.md](README.en.md)

---

## 功能

- 录音：在“我的录音”中开始录音，停止后生成 `VoiceDrop-*.m4a`。
- 自动上传：录音完成后上传到后端，并触发转写与文章生成。
- 文章列表：显示待处理、听录音、挖文章、已成文、无语音等状态。
- 文章详情：阅读文章、播放原录音、删除录音或文章、分享公开链接。
- 语音修改文章：长按说话提出修改要求，文章会被重新改写并刷新。
- 插入图片：从相册选择图片或拍照上传，让 AI 把图片插入文章正文。
- 文风设置：保存个人写作文风，用于后续文章生成。
- 公众号草稿：配置公众号后，可发布或更新微信公众号草稿。
- VD 社区：浏览社区内容，分享自己的文章，也支持举报和屏蔽。
- 导出：打包导出文章、音频、字幕和索引。

---

## 与 iOS 版本的关系

Android 版本以 iOS 版本作为功能和接口参考：

- 后端 API、文章 schema、照片 marker、公众号发布结果等行为保持一致。
- Android 交互会按平台习惯调整，例如 tab、权限、弹窗和键盘处理。
- 如果实现细节不同，以用户可见行为和后端契约一致为目标。

iOS 项目地址：

```text
https://github.com/jianshuo/voicedrop
```

---

## 工作流程

```text
录音
  -> 上传音频
  -> 后端转写
  -> AI 生成文章
  -> App 展示文章
  -> 分享、编辑、插图或发布到公众号
```

文章生成状态会实时同步到列表中。文章详情页支持继续用语音提出修改要求，也支持上传图片后让 AI 插入到合适的位置。

---

## 后端接口

Android 与 iOS 共用后端服务：

| 服务 | 用途 |
|---|---|
| Files API | 录音、文章、图片、分享、设置、公众号配置 |
| Agent Worker | 文章生成、文章编辑、实时状态 |
| WebSocket | 文章生成状态与语音编辑 |
| Public share page | 公开文章预览与分享卡片 |

主要能力包括：

- 上传录音和图片
- 获取录音列表和文章详情
- 生成公开分享链接
- 保存文风与应用配置
- 发布或更新微信公众号草稿
- 读取和提交 VD 社区内容

---

## 文件名规则

录音文件遵循 iOS 约定：

```text
VoiceDrop-2026-06-18-143052-0m33s-Thu-Afternoon.m4a
```

其中包含：

- 录音开始时间
- 录音时长
- 星期
- 时间段

`VoiceDrop-` 前缀和 `.m4a` 后缀是后端识别录音的约定，不应随意更改。

---

## 图片 marker

文章中的图片使用 marker 表示：

```text
[[photo:photos/<sessionTs>/<offset>-<rand>.jpg]]
```

App 会把 marker 渲染为内联图片。公众号发布、公开分享页和社区详情也按同一套规则处理图片。

---

## 公众号发布

在设置中填写公众号 AppID 和 AppSecret 后，文章详情页可以发布到微信公众号草稿箱。

发布行为：

- 如果文章还没有草稿，会创建新的公众号草稿。
- 如果文章已经有草稿，会更新已有草稿。
- 公众号接口错误会显示为用户可读的中文提示。
- 如果公众号配置缺失，会引导回设置页。

公众号后台需要把服务端出口 IP 加入白名单。具体 IP 以应用设置页展示为准。

---

## 技术栈

- Java
- Android SDK 原生 View
- Android Gradle Plugin
- ViewPager
- OkHttp
- Bouncy Castle
- DialogX
- JUnit

---

## 构建

要求：

- JDK 17
- Android SDK
- Gradle Wrapper

运行单测：

```bash
./gradlew testDebugUnitTest
```

打 debug 包：

```bash
./gradlew assembleDebug
```

打 release 包：

```bash
./gradlew assembleRelease
```

release APK 默认输出到：

```text
app/build/outputs/apk/release/app-release.apk
```

---

## Release 签名

Release 包可以通过 Gradle signing config 签名。项目支持两种方式提供签名配置：

- 本地签名配置文件
- 环境变量

可用环境变量：

```text
ANDROID_KEYSTORE_PATH
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

如果签名配置完整，`assembleRelease` 会输出已签名 APK。

---

## 友盟统计

友盟 AppKey 通过构建参数注入，不写入 Git 仓库。

本地开发可以在已被 `.gitignore` 忽略的 `local.properties` 中配置：

```properties
umeng.appKey=你的友盟 AppKey
umeng.channel=local
```

GitHub Actions release 打包需要在仓库 Settings -> Secrets and variables -> Actions 中添加：

```text
UMENG_APP_KEY
```

AppKey 会被编译进 APK，这是 Android 客户端统计 SDK 的正常工作方式；它不是服务端私钥，但不应硬编码到公开仓库或输出到日志。

---

## 注意事项

- 真机更适合测试完整录音链路。
- 模拟器适合验证 UI、列表、弹窗和接口流程。
- 公众号发布依赖公众号配置、服务端 relay 和公众号后台白名单。
- 后端接口、文章格式和跨平台行为应优先参考 iOS 项目。
