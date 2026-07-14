# Android 与小程序 Prompt Manager 完整同步设计

## 目标

将 iOS TestFlight 240 中已经上线的 Prompt Manager 完整同步到 Android 与微信小程序，替换已被后端删除的 `/agent/ui-config` 与 `/agent/ui-config/custom` 契约。三个客户端的界面遵循各自平台习惯，但提示词树、编辑语义、分享与导入结果、错误处理和缓存回退保持一致。

本次同步只覆盖最终仍存在于 iOS 的 Prompt Manager 能力。已经回滚的文章编辑流式预览不在范围内。社区瀑布流、轻量录音列表和边缘缩略图属于独立功能或性能项目，也不混入本次提示词迁移。

## 完整功能范围

Android 与小程序都必须提供以下能力：

1. 从 `GET /agent/prompts` 加载两级提示词树。
2. 缓存最近一次成功响应；刷新失败时保留缓存或内置模板，同时显示可见错误。
3. 根据 `appliesTo` 生成正文与图片长按菜单，并继续支持 `{{LINE}}`、`{{QUOTE}}`、`{{KEY}}` 占位符。
4. 展示顶层动作、顶层分组和分组内动作，区分系统项与自建/派生项。
5. 新建提示词，可选择正文、图片或两者，并为图片提示词保存兼容的 `kind`。
6. 编辑动作名称、提示词正文和适用范围。
7. 编辑系统项时先 fork：生成 `p_` 开头的新 ID，记录 `forkedFrom`，原位替换系统引用。
8. 编辑自建或已 fork 项时保持原 ID。
9. 删除顶层项、分组或分组内动作；失败时恢复删除前完整树。
10. 恢复默认，通过 `POST /agent/prompts/restore-defaults` 补回缺失的系统模板项，不删除用户内容。
11. 进入排序模式后支持顶层排序、组内排序和动作跨组移动；层级最多两级，不允许组嵌套组。
12. 新建分组、编辑分组名称和删除分组；删除分组会连同当前子项一起删除，操作前明确确认。
13. 通过 7 位魔法数字预览并导入提示词。
14. 区分逐字输入与粘贴/自动填充；8 位及更长数字中不得截取前 7 位作为分享码。
15. 展示每条提示词的分享状态，支持开启、关闭、复制数字、复制 URL 和平台系统分享。
16. Android 处理 `https://voicedrop.cn/<code>` 与应用内分享码路由；小程序处理启动 query、分享卡 query 和页面内输入。
17. 所有整树写入使用单写入锁、操作前快照和失败回滚；排序提交额外执行基线 ID 冲突检查。

## 后端契约

### 已解析节点

`GET /agent/prompts` 返回：

```json
{
  "schema": 1,
  "items": [
    {
      "id": "sys_rewrite",
      "type": "group",
      "label": "改写这段",
      "origin": "system",
      "children": [
        {
          "id": "sys_concise",
          "type": "action",
          "label": "更简洁",
          "origin": "system",
          "prompt": "把第{{LINE}}行……",
          "appliesTo": ["text"]
        }
      ]
    }
  ]
}
```

客户端读取并保留以下字段：

- 公共字段：`id`、`type`、`label`、`origin`、`forkedFrom`。
- 动作字段：`prompt`、`appliesTo`、`kind`。
- 分组字段：`children`。
- 未识别字段必须被忽略，不能导致整棵树解析失败。

### 整树写入

`PUT /agent/prompts` 请求体固定为 `{ "items": [...] }`。客户端必须把已解析树转换为 raw 形状：

- `origin == system` 的节点写成 `{ "ref": "sys_*" }`。
- 系统分组额外写出 raw `children`，避免清空分组内容。
- 用户实体写出 `id`、`type`、`label`，动作再写 `prompt`、`appliesTo`、可选 `kind` 与 `forkedFrom`，分组写 `children`。
- 响应仍是已解析树，成功后必须用响应替换内存状态并更新缓存。

客户端不发送 `origin`，也不继续发送旧模型的 `default`、`override`、`customLabel` 或 `hidden` 字段。

### 其他端点

- `POST /agent/prompts/restore-defaults`：无请求体，返回完整已解析树。
- `POST /agent/prompts/import`：请求 `{ "code": "1234567" }`，返回 `{ "item": <resolved action> }`；成功后重新加载完整树。
- `GET /agent/prompt-share/<code>`：公开预览，返回名称、提示词、适用范围、作者和导入数。
- `GET /agent/prompt-shares`：返回 `{ "byItem": { "<id>": { "code": "1234567", "sharing": true } } }`。
- `POST /agent/prompt-share`：请求 `{ "id": "<item id>" }`，开启或恢复分享。
- `DELETE /agent/prompt-share/<encoded item id>`：关闭分享。

HTTP 401 显示登录提示；404 导入预览显示分享码无效或已停止分享；429 显示当日分享码额度已用完；网络错误与其他非成功响应使用可重试文案。

## 共享领域模型与纯逻辑

两端分别实现同构的纯逻辑层，UI 和网络层不自行拼装树：

- `PromptNode`：节点模型与递归 children。
- `decodeItems`：验证 schema 并解析已解析树。
- `rawItems`：生成 PUT raw 形状。
- `fork`：系统节点实体化，生成符合 `^p_[a-z0-9]{6,}$` 的 ID。
- `replace`、`remove`、`append`：递归且不可变地修改指定节点。
- `flattenIds`：排序基线与冲突检测。
- `menuFor(text|image)`：按 `appliesTo` 过滤并生成现有长按菜单输入。
- `extractShareCode`：用 `(?<![0-9])[1-9][0-9]{6}(?![0-9])` 提取分享码。
- `mergeCodeInput`：区分单字符尾部编辑与粘贴/自动填充。

新建动作默认 ID 为 `p_` 加 8 位小写字母或数字。新建分组使用同一 ID 规则。名称去除首尾空白后不能为空；动作 prompt 去除首尾空白后不能为空；适用范围至少包含 `text` 或 `image` 之一。

## Android 设计

### 数据层

用新的 `PromptStore` 取代 `UIConfigStore` 的远端职责。旧类如果仍被长按菜单调用，应改为只委托 `PromptStore`，最终不再包含任何 `/ui-config` 路径。

`PromptStore` 负责：

- SharedPreferences 缓存 `promptsCache.v1`。
- 加载、保存、恢复默认、导入、预览和分享接口。
- 单写入锁、快照回滚和排序基线检查。
- 向现有 `RecordingDetailActivity` 提供正文与图片菜单。

网络响应解析、树转换和菜单转换放在可运行 JVM 单元测试的纯 Java 类中，不依赖 Activity。

### 界面

现有 `InstructionSettingsActivity` 升级为管理器列表：

- 顶部提供新建、导入、排序和更多菜单中的恢复默认。
- 列表展示分组及其子项，系统项显示系统标记，用户项显示自建或已修改标记。
- 点击动作进入编辑页；点击分组进入分组编辑或折叠/展开。
- 删除使用确认弹窗。
- 排序使用原生拖动手柄；拖到分组高亮区域表示进入分组，拖出表示回到顶层。
- 网络刷新失败但有缓存时，列表保留并在顶部显示错误条幅。

编辑页覆盖名称、提示词、适用范围和分享卡。系统项在用户首次保存有效变化时 fork；未产生变化时不写网络。分享卡进入页面时读取 `/agent/prompt-shares`，切换分享后刷新当前状态。

导入页先本地提取分享码，再调用公开预览；用户确认后调用导入接口并返回管理器。Android Manifest 与路由层接收 voicedrop.cn 的 7 位路径并打开导入页。

## 微信小程序设计

### 数据层

新增 `utils/prompt-tree.js` 与 `services/prompt-store.js`：

- `prompt-tree.js` 放所有递归纯逻辑和分享码输入逻辑。
- `prompt-store.js` 负责缓存、请求、写入锁、快照和状态订阅。
- `services/ui-config.js` 与 `services/instruction-settings.js` 的调用方迁移完毕后删除旧接口实现，仓库中不得再出现运行时代码访问 `/ui-config`。

长按菜单组件继续接收其熟悉的 groups 结构，但数据改由 `prompt-tree.js` 从新树生成。

### 界面

提示词设置页升级为嵌套管理器：

- 使用微信小程序 movable-area/movable-view 或等价原生拖动实现排序。
- 拖动模式外保持正常页面滚动，拖动模式中提供明确手柄和放置高亮。
- 新建、编辑、删除、分组、恢复默认、导入和分享功能与 Android/iOS 等价。
- 页面刷新失败时保留缓存 rows，并显示非阻断错误。

小程序不能依赖普通 HTTPS Universal Link 唤起。分享卡携带 `promptCode=<code>` query，App 启动路由和提示词页面都解析该参数；从剪贴板或输入框导入仍执行相同 7 位边界校验。

现有未提交的 WXSS 与页面测试属于用户改动。实现时逐文件检查 diff，只对 JS、WXML 和必要测试做增量修改；如果必须修改已变更的测试文件，保留原断言和样式行为后再加入新断言。

## 状态一致性与并发

每个写操作遵循同一事务模式：

1. 检查 `isMutating`，已有写入时忽略重复触发或禁用 UI。
2. 保存完整树快照。
3. 在内存中应用单一操作并立即更新 UI。
4. PUT 整树。
5. 成功时用服务端响应替换内存树并更新缓存。
6. 失败时恢复完整快照并显示错误。

排序模式额外保存进入排序时的扁平 ID 序列。完成排序前，如果当前 store 的 ID 序列与 baseline 不同，拒绝 PUT，保留用户 draft，并提示列表已在其他操作中更新。导入、删除、编辑和排序不得并发覆盖。

分享状态是独立索引，不写进 prompts 树。fork 一个正在分享的系统项后，服务端负责把分享索引 re-key 到新实体 ID；客户端保存成功后重新读取分享状态。

## 缓存与错误处理

启动时的有效数据优先级为：成功解析的缓存、内置模板。刷新成功后替换数据；刷新失败不清空已有树。缓存 schema 高于客户端支持值或 JSON 损坏时跳过缓存并使用内置模板。

错误展示分为：

- 管理器加载错误：顶部可关闭条幅，已有内容继续可用。
- 写入错误：回滚后显示短提示，页面不自动退出。
- 导入错误：保留输入和预览页，允许重试。
- 分享错误：恢复开关原状态，分享码和链接不清空。
- 冲突错误：保留排序草稿，要求用户重新载入后再次提交。

## 测试设计

### Android JVM 测试

- 解析完整节点、未知字段、损坏缓存与高 schema。
- resolved tree 转 raw refs/entities，尤其是系统分组 children。
- fork ID、forkedFrom 与字段保留。
- 递归新增、替换、删除和扁平 ID。
- text/image 菜单过滤与占位符填充。
- 分享码提取，8 位数字拒绝，逐字输入与粘贴差异。
- GET/PUT/import/restore/share 请求 path、method 与 JSON。
- 写入失败快照回滚、重复写入锁和排序冲突。
- Activity 源码或可抽取 presenter 的列表、错误条幅、深链和分享行为。

### 小程序 Node 测试

- 与 Android 相同的 prompt-tree 纯逻辑矩阵。
- 服务请求契约、缓存回退和分享状态合并。
- 管理页加载、新建、删除、恢复默认和错误保留。
- 编辑页系统 fork、自建直接编辑、保存失败不退出。
- 拖动排序、组内移动、跨组移动和冲突拒绝。
- 导入输入边界、预览、确认导入和 query 路由。
- 分享卡携带 code query，复制链接使用 voicedrop.cn URL。
- 现有录音、详情、社区与样式测试保持通过。

### 完成门槛

- Android：`./gradlew testDebugUnitTest` 与 `./gradlew assembleDebug` 均成功。
- 小程序：仓库定义的全量 `npm test` 成功。
- 两端代码搜索不再发现运行时 `/agent/ui-config` 或 `/agent/ui-config/custom` 请求。
- 使用固定 JSON fixture 对比 Android、小程序和 iOS 的 raw PUT 结果与菜单过滤结果。
- 最终交付列出修改文件、验证命令、小程序既有改动的保留情况，以及仍需真机验证的拖动、分享卡和深链步骤。

## iOS 最新功能清单（交付报告使用）

最终报告将把 iOS TestFlight 240 的净新增能力分为：

- Prompt Manager 完整重构与魔法数字分享/导入。
- 社区双列瀑布流及推荐、最新、回应分类。
- `/reco/feed` 聚合社区数据。
- `/recordings` 轻量录音列表。
- 图片磁盘缓存、512px 展示变体和 Cloudflare 边缘缩略图。
- 相册导入保留原始宽高比。

设备配对补送、图片二次裁方、提示词分享 URL、缓存刷新错误提示等归入 Bug 修复，不混入新增功能。已经回滚的编辑流式预览不列为最终新增功能。
