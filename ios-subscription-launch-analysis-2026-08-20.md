# VoiceDrop iOS 订阅功能上线核实

> 核实时间：2026-08-20（Asia/Shanghai）
> iOS 仓库：`/Users/holly/code/BaiXingAI/voicedrop` @ `24df4437d6ab4fd5a8214e2fc3ec68c3d9de6b2d`
> App Store：中国区，Apple ID `6781565141`

## 结论

**是，iOS 的包月订阅已经正式上线。**

它不再只是“代码已实现”或“已提审”：Apple 中国区官方公开数据当前返回线上版本 **1.12**，发布时间是 `2026-08-19T19:32:37Z`（北京时间 **2026-08-20 03:32:37**），版本说明明确写了“新增「包月算力」订阅：¥19.9/月，每月自动充入 200 算力”。Apple 商品页同时已公开显示 **App 内购买 → 包月算力 ¥19.90**。

因此，按“线上 App 版本已发布，首个订阅商品也已出现在 Apple 公开商品页”的标准，可以判定为已上线。

## 状态分层

| 层级 | 结论 | 关键证据 |
|---|---|---|
| 客户端代码已实现 | 是，2026-07-17 | 提交 `c939f719af2e9d8acad37e6ea57f03a1dbf94182`；StoreKit 2 购买、交易监听、恢复购买、权益同步、服务端 claim 都已实现 |
| 入口已显示 | 是，2026-08-18 开关开启 | `config/iap.json={"enabled":true}` 后，算力页显示订阅卡；已订阅用户不受开关影响 |
| 商品元数据已完成 | 是，2026-08-18 | `monthly_19_9` 已补齐中英文本地化、175 地区价格、审核截图、review note 和 availability |
| 已打包 / TestFlight | 是 | 1.12 / build 318；`testflight/318` 与 `release/1.12` 指向 `ab055418c0634cd3e2e0ef16170257ffcfa4e432` |
| 已提交审核 | 是 | 1.12、订阅组、订阅版本三个条目同单提审；8 月 19 日因描述缺 EULA 链接被自动拒绝后已补齐并重提 |
| App Store 版本已发布 | **是** | Apple Lookup API 实时返回 `version=1.12`、`currentVersionReleaseDate=2026-08-19T19:32:37Z` |
| 订阅商品公开上架 | **是** | Apple 中国区商品页已显示“App 内购买”与“包月算力 ¥19.90” |

## 源码证据

### 1. StoreKit 2 购买链路完整

- 产品 ID 是 `com.wangjianshuo.VoiceDrop.sub.monthly_19_9`，价格显示使用 Apple 返回的 `product.displayPrice`：`VoiceDropApp/StoreService.swift:12-15`。
- App 启动后挂载 `Transaction.updates`，并同步当前权益：`VoiceDropApp/StoreService.swift:28-45`。
- 购买调用 `Product.purchase()`，对已验证交易执行 claim 和 finish：`VoiceDropApp/StoreService.swift:48-78`。
- 换机/重装通过 `AppStore.sync()` 恢复购买：`VoiceDropApp/StoreService.swift:80-93`。
- 交易 ID 上报 `/agent/iap/claim`，由服务端幂等发放算力：`VoiceDropApp/StoreService.swift:102-124`。
- App 根视图启动 `StoreService.shared.start()`，续费到账不依赖用户先进入算力页：`VoiceDropApp/VoiceDropApp.swift:18-24`。

### 2. 付费墙与审核所需信息完整

- 售卖开关开启或用户已订阅时显示订阅卡：`VoiceDropApp/UsageView.swift:41-48`。
- 未订阅状态提供“订阅包月算力”购买按钮：`VoiceDropApp/UsageView.swift:120-139`。
- 已订阅状态显示续费日并提供系统“管理订阅”入口：`VoiceDropApp/UsageView.swift:107-119`。
- 有自动续费披露、恢复购买、隐私政策和 Apple 标准 EULA：`VoiceDropApp/UsageView.swift:146-163`。

### 3. 1.12 就是订阅上架版

- `project.yml:96` 的 `MARKETING_VERSION` 是 `1.12`。
- `fastlane/metadata/zh-Hans/release_notes.txt:1-4` 明确宣布新增 ¥19.9/月、200 算力的包月订阅。
- `fastlane/metadata/zh-Hans/description.txt:13-16` 已包含订阅说明、Apple 标准 EULA 和隐私政策。

## 上架时间线

1. **2026-07-17**：`c939f719` 完成 iOS StoreKit 2 订阅客户端。
2. **2026-08-18**：开启售卖开关；将原先缺元数据的订阅商品补齐；打包 1.12 / build 318；将版本、订阅组和首个订阅同单提审。
3. **2026-08-19**：首审因 App 描述缺 EULA 链接被自动拒绝；`a9cf3af` 补齐中英文描述，随后重建提审单并回到 `WAITING_FOR_REVIEW`。
4. **2026-08-20 03:32（北京时间）**：Apple 公开 API 显示 1.12 已发布，中国区商品页同时已列出包月算力内购。

## 为什么仓库里会看到“还没上线”

`STATE.md:673` 当前仍写着“1.11 在售；1.12 / build 318 `WAITING_FOR_REVIEW`”。这条状态是 **2026-08-18 提审时的快照**，而 Apple 的公开数据显示 1.12 已在之后于北京时间 8 月 20 日凌晨上架。因此：

- `STATE.md` 对当时的提审状态记录是真实的；
- 它不是 App Store 的实时状态；
- 判断“现在是否已上线”应以 Apple 当前公开数据为准。

## 仍未完成与仍需验证的事项

### App Store 服务器通知 V2 尚未配置

`CHANGELOG.md:62-64` 和 `STATE.md:673` 都记录，App Store Connect 中的生产/沙盒服务器通知 V2 URL 仍需手工配置为：

`https://jianshuo.dev/agent/iap/notifications`

这不会阻止用户购买，也不意味着订阅没上线。客户端已通过购买回调、`Transaction.updates` 和启动时 `currentEntitlements` 执行 claim 兜底。差别是：未配通知时，续费/退款等服务端状态可能不能实时感知，需等用户打开 App 后由客户端兜底同步。

### 本次未做真实 Apple ID 扣款实测

Apple 公开商店证据足以证明版本和内购商品已公开上线，但本次是只读研究，没有实际发起付费。若要验证完整生产链路，还应在中国区 Apple ID 真机上检查：

1. 算力页能加载 Apple 返回的 ¥19.90 商品；
2. 购买成功后服务端发放 200 算力；
3. 重装后“恢复购买”可找回权益；
4. 续费、退款和取消的状态与算力处理符合预期。

## 一手来源

- Apple iTunes Lookup API（中国区）：<https://itunes.apple.com/lookup?id=6781565141&country=cn>
- Apple 中国区 VoiceDrop 商品页：<https://apps.apple.com/cn/app/id6781565141>
- iOS 客户端实现：`VoiceDropApp/StoreService.swift`、`VoiceDropApp/UsageView.swift`、`VoiceDropApp/VoiceDropApp.swift`
- iOS 版本配置与商店元数据：`project.yml`、`fastlane/metadata/zh-Hans/release_notes.txt`、`fastlane/metadata/zh-Hans/description.txt`
- iOS 提交历史：`c939f719`、`ab055418`、`a9cf3af`、`24df443`
- iOS 本地发布记录：`CHANGELOG.md:24-64`、`STATE.md:673`

## 最终表述建议

> VoiceDrop iOS 的 ¥19.9/月“包月算力”订阅已随 1.12 在 2026 年 8 月 20 日凌晨于中国区 App Store 正式上线，Apple 商品页已显示该内购与 ¥19.90 价格。但 App Store 服务器通知 V2 仍需手工配置，并建议再用生产 Apple ID 做一次购买、到账、恢复与续费的端到端验证。
