# iOS 与后端上游更新审计（2026-08-20）

## 结论摘要

- iOS 本地 `main` 已经与 `upstream/main` 一致，更新前后均为 `24df4437d6ab4fd5a8214e2fc3ec68c3d9de6b2d`，本次没有新增 iOS 提交，因此没有需要同步到 Android、HarmonyOS 或小程序的 iOS 功能或修复。
- 后端从 `d648129e77f63da88517ade0ac9bad9da3ba9ffb` 快进到 `36ec47c0b079f8eacb4e479879ad95916b7149ff`，仅新增 1 个提交：为 VoiceDrop MCP 增加 6 个“书”工具，并补齐公开访问、老书兼容、正文清理和错误提示。
- 后端本次没有修改手机端调用的书架、写书、修书或历史 API；Android、HarmonyOS 和小程序已经直接实现相同契约，无需功能代码同步。
- 建议四端统一补充内置帮助文档的 MCP 描述，加入“读书、写书、修书”。这是文案同步，不影响兼容性。

## 更新过程与仓库状态

| 仓库 | 更新前 | 更新后 | 结果 |
|---|---|---|---|
| iOS `/Users/holly/code/BaiXingAI/voicedrop` | `24df443` | `24df443` | `upstream/main` 无新提交，`--ff-only` 返回 Already up to date |
| 后端 `/Users/holly/code/BaiXingAI/jianshuo.dev` | `d648129` | `36ec47c` | `--ff-only` 快进 1 个提交，7 个文件、+280/-8 |

两个仓库更新前工作区均干净。本次未改写历史、未产生 merge commit，也未修改其他客户端仓库。更新后 iOS 相对其 fork `origin/main` ahead 26，后端 ahead 16；这是本地已合入上游提交但尚未推送 fork 的状态。

## iOS 更新分析

iOS 本次没有 `旧 HEAD..新 HEAD` 差异。当前最新提交 `24df443`（2026-08-19 09:19 +08:00）只是记录 1.12 App Store 首审因 EULA 链接被拒及重提注意事项，而且该提交在本次操作前已经存在于本地并等于 `upstream/main`。

因此：

- Android：无需同步。
- HarmonyOS：无需同步。
- 小程序：无需同步。
- 不存在需要从本次 iOS 更新移植的 bug fix、UI 行为或接口契约。

## 后端更新内容

唯一新增提交：`36ec47c0b079f8eacb4e479879ad95916b7149ff`，提交时间 2026-08-19 13:09 +08:00。

### 1. MCP 新增 6 个书籍工具

来源：[mcp/src/tools.js](/Users/holly/code/BaiXingAI/jianshuo.dev/mcp/src/tools.js)

- `list_books`：列出公开书架，返回 slug、标题、作者、类目、章节数、封面、创建时间和阅读 URL。
- `read_book`：读取目录与梗概。新书优先读取 `_src/book.json`；老书回退解析 `index.html`。
- `read_book_chapter`：读取章节 HTML，并转换为适合模型消费的纯文本。
- `write_book`：调用 `POST https://lab.jianshuo.dev/api/book`，按 320 算力异步写书。
- `revise_book`：调用 `POST https://lab.jianshuo.dev/api/book/revise`，按 40 算力异步修书。
- `book_history`：调用 `GET https://lab.jianshuo.dev/api/book/history?slug=...`，查看逐章写作/修改对话线和运行状态。

前三个工具访问公开内容，不要求 VoiceDrop token；后三个操作用户资产并扣算力，要求 token。

### 2. MCP 客户端新增两个上游

来源：[mcp/src/vd-client.js](/Users/holly/code/BaiXingAI/jianshuo.dev/mcp/src/vd-client.js)

- `lab` 上游：`https://lab.jianshuo.dev/api`，承接写书、修书和历史接口。
- `books` 上游：VoiceDrop 公共书架路径，承接公开书架和书籍文件读取。

### 3. 认证与产品文案更新

来源：[mcp/src/http.js](/Users/holly/code/BaiXingAI/jianshuo.dev/mcp/src/http.js)、[mcp/src/landing.js](/Users/holly/code/BaiXingAI/jianshuo.dev/mcp/src/landing.js)

- `list_books`、`read_book`、`read_book_chapter` 加入免 token 白名单，与网页公开可读语义一致。
- MCP landing page 的能力说明增加“读书写书”。
- Pages Function 注释中的测试数更新为 142。

## 修复与兼容性改进

这次提交没有修复手机 App 现有 bug；修复集中在新 MCP 书籍能力的可用性和兼容性：

1. **公开内容不再被认证门槛挡住**：三项读书工具加入免 token 白名单，匿名 MCP 用户可以像网页访客一样访问公开书架。
2. **兼容没有源数据的老书**：`_src/book.json` 返回 404 时，回退解析 `index.html`，避免老书无法读取目录。
3. **章节文本去噪**：去掉 `script`、`style` 和服务端注入的“听本章”播放器，再将 HTML 转为纯文本，避免模型读到控件和脚本内容。
4. **兼容单位数章号**：调用方传 `1` 时自动读取 `01.html`。
5. **算力不足错误可理解**：把 lab API 的 `402 { error: "no-credit" }` 翻译为明确的 320/40 算力提示，并建议用 `credit_balance` 查余额。
6. **404 排障补齐书籍维度**：提示从只检查 `stem/shareId` 扩展为检查 `stem/shareId/slug`，并引导先调用 `list_books`。

## 跨端同步判断

| 平台 | 是否必须同步代码 | 判断依据 | 建议 |
|---|---|---|---|
| Android | 否 | 已有公开书架、写书、修书、历史轮询；使用相同 `voicedrop.cn/books` 与 `lab.jianshuo.dev/api/book*` 契约；费用已是 320/40 | 仅建议更新帮助文案 |
| HarmonyOS | 否 | `BooksService`、`BookWritingService`、`BookReviseService` 已覆盖同一 API、费用和历史语义 | 仅建议更新帮助文案 |
| 小程序 | 否 | `services/books.js` 已覆盖书架、写书、修书、历史、401/402/403/404/409 和 320/40 费用 | 仅建议更新帮助文案 |
| iOS | 否 | 本次无新提交；当前版本本身已有原生书架、写书和修书能力 | 仅建议更新帮助文案 |

关键客户端证据：

- Android：[BookWritingActivity.java](/Users/holly/code/BaiXingAI/voicedrop-android/app/src/main/java/com/baixingai/voicedrop/BookWritingActivity.java)、[BookReviseBottomSheet.java](/Users/holly/code/BaiXingAI/voicedrop-android/app/src/main/java/com/baixingai/voicedrop/BookReviseBottomSheet.java)、[BooksShelfActivity.java](/Users/holly/code/BaiXingAI/voicedrop-android/app/src/main/java/com/baixingai/voicedrop/BooksShelfActivity.java)
- HarmonyOS：[BookWritingService.ets](/Users/holly/code/BaiXingAI/voicedrop-HarmonyOS/entry/src/main/ets/data/BookWritingService.ets)、[BookReviseService.ets](/Users/holly/code/BaiXingAI/voicedrop-HarmonyOS/entry/src/main/ets/data/BookReviseService.ets)、[BooksService.ets](/Users/holly/code/BaiXingAI/voicedrop-HarmonyOS/entry/src/main/ets/data/BooksService.ets)
- 小程序：[services/books.js](/Users/holly/code/BaiXingAI/voicedrop-mini/services/books.js)
- iOS：[BookWritingSheet.swift](/Users/holly/code/BaiXingAI/voicedrop/VoiceDropApp/BookWritingSheet.swift)、[BookReviseSheet.swift](/Users/holly/code/BaiXingAI/voicedrop/VoiceDropApp/BookReviseSheet.swift)、[BooksShelfView.swift](/Users/holly/code/BaiXingAI/voicedrop/VoiceDropApp/BooksShelfView.swift)

### 建议的文案同步

四端帮助文档当前都把 MCP 描述为“读写文章、改文风、发公众号”，没有提本次新增的书籍能力。建议统一补为类似：

> 在电脑的 Claude 等 AI 客户端里连接 `voicedrop.cn/mcp`，可以读写文章、改文风、发布内容，也可以逛书架、读书、写书和修书。

这不是发布阻塞项；即使不改，三端功能和服务端契约也不会受影响。

### 独立核验发现的既有差异（非本轮同步项）

- iOS 的写书/修书 402 提示仍使用本地固定价格 320/40，只读取服务端的当前余额 `suanli`；Android、HarmonyOS 和小程序会优先使用服务端返回的 `need_suanli`。如果以后服务端调价，iOS 文案可能先失真。建议后续单独改为服务端 `need_suanli` 优先、本地常量兜底，但这不是本次 iOS 上游更新产生的问题。
- MCP 的 `list_books` 会返回 `category`，现有原生客户端主要消费 slug、标题、封面、章节数、作者和创建时间，没有展示类目。这是可选的产品能力差异，不是兼容要求，也不需要为了本次提交立即同步。

## 验证

在后端 `mcp/` 目录运行：

```text
npm test
```

结果：8 个测试文件全部通过，142/142 个测试通过。新增测试覆盖匿名读书、书架字段映射、新书目录、老书回退、章节 HTML 清理、单位数章号、写书、修书和历史查询。

本次没有修改 Android、iOS、HarmonyOS 或小程序功能代码，因此没有执行客户端构建或真机测试。
