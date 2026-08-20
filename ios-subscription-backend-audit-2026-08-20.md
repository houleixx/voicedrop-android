# VoiceDrop iOS 订阅后端与支付回调审计

> 审计日期：2026-08-20（Asia/Shanghai）
> 一手来源：`jianshuo.dev` 当前源码、D1 migration、单元测试、iOS 发布记录和生产端点冒烟
> 边界：本次只读审计，未修改产品代码，也未执行真实购买、续费或退款。

## 结论

**有订阅交易账本和权益发放系统，但不是完整的商业订单系统。**

后端已经有：

- Apple 每笔首购/续费交易的幂等记录 `iap_txn`；
- 订阅链与 VoiceDrop 账号的绑定及状态缓存 `iap_sub`；
- 客户端主动 claim、Apple 服务器通知 webhook、订阅状态查询三个端点；
- 通过 App Store Server API 回查交易，按产品发放 200 算力；
- 同一 `transaction_id` 不重复发放，续费新交易可再发一桶，退款/撤销可清空该笔交易尚未使用的算力。

但数据库不保存实付金额、币种、店面、购买时间、订单原始凭据/通知、退款金额、发票、对账状态等商业订单字段，也没有订单管理或对账后台。Apple 是真正的收款与订单主体；VoiceDrop 后端目前是**交易校验 + 订阅权益账本**。表结构证据见 [0004_iap.sql](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/migrations/0004_iap.sql:5)。

**支付回调代码和生产路由存在且已部署，但现有证据不能证明 App Store Connect 已把生产/沙盒通知 URL 配好。**

- `POST /agent/iap/notifications` 已实现并接入 Worker 总路由，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:165) 和 [index.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/index.js:1611)。
- 2026-08-20 对生产 URL 发送空 JSON，返回 `{"ok":true,"skipped":"bad-payload"}`。由于代码会先检查 `iapReady`，之后才解析 payload，该响应可证明当时生产路由在线，且 `USAGE` 与三个 ASC key secret 均已注入，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:62) 和 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:167)。
- 但这只证明“接收端在线”，不证明“Apple 已被配置为向它推送”。iOS 仓库截至 2026-08-18 的最新操作记录仍明确写着这是“仍需手工”的步骤，见 [CHANGELOG.md](/Users/holly/code/BaiXingAI/voicedrop/CHANGELOG.md:62) 和 [STATE.md](/Users/holly/code/BaiXingAI/voicedrop/STATE.md:673)。因此当前应按**尚未证实已配置，很可能仍未配置**处理，需在 App Store Connect 界面最终确认。

## 后端现有链路

### 1. 客户端 claim：首购和启动兜底

`POST /agent/iap/claim` 要求 VoiceDrop 用户 token，接收 `transaction_id`，然后用后端 ES256 JWT 调 Apple 的 `/inApps/v1/transactions/{id}`。生产查不到时再查 sandbox，只有 Apple 回查结果的 bundle ID 和 product ID 匹配才发放。见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:50)、[iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:74) 和 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:149)。

产品档位目前只有 `com.wangjianshuo.VoiceDrop.sub.monthly_19_9 -> 200 算力`，每桶有 Apple 周期末 + 6 小时宽限，见 [usage.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/usage.js:84)。

### 2. 账号绑定与幂等

- `iap_sub.original_txn_id` 是订阅链主键，首个成功 claim 的 VoiceDrop 账号抢到绑定；其他账号再 claim 同一链会得到 409，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:102)。
- `iap_txn.transaction_id` 是每周期交易的唯一主键；`INSERT OR IGNORE` 保证同一笔交易只发放一次，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:113)。
- 过期或已撤销交易会记录，但不发算力；正常新交易会新建订阅算力桶并将 `bucket_id` 写回交易记录，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:119)。

### 3. App Store Server Notifications V2

webhook 从 `signedPayload` 中解出 `notificationType` 和 `transactionId`：

- 非退款/撤销通知会再回查 Apple，然后复用同一条 `processTransaction`入账路径；续费的新 transaction ID 因此可以再发 200 算力，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:180)。
- `REFUND` / `REVOKE` 会根据 transaction ID 找到已发桶，将未使用余额清零并标记订阅链 `revoked`；已经消耗的算力不追回，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:135)。

### 4. 状态查询

`GET /agent/iap/status` 按当前 VoiceDrop 账号查 `iap_sub`，只在 `status=active` 且未过期时认为生效，并返回当月订阅桶剩余算力。见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:186)。

## 覆盖到的 Bug/风险场景

当前 20 个 IAP 单测全部通过，覆盖：

- 同一交易重放不重复发放；
- 续费新交易再发一桶；
- 同一订阅换 VoiceDrop 账号报 409；
- 历史过期交易不补发；
- 生产 404 后回退 sandbox；
- 错误 bundle/product、缺 secret、缺 token；
- `DID_RENEW`、`SUBSCRIBED` 早于绑定、伪造的非退款通知、`REFUND`、坏 payload；
- 订阅状态、剩余算力和 R2 售卖开关。

对应测试见 [iap.test.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/test/iap.test.js:79)、[iap.test.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/test/iap.test.js:160) 和 [iap.test.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/test/iap.test.js:216)。本次实际运行：

```text
cd /Users/holly/code/BaiXingAI/jianshuo.dev/agent
npm test -- --run test/iap.test.js

Test Files  1 passed (1)
Tests       20 passed (20)
```

发布记录还明确记载 Worker 已部署、D1 `0004` 已 apply、ASC secrets 已 put 且做过线上冒烟，见 [CHANGELOG.md](/Users/holly/code/BaiXingAI/voicedrop/CHANGELOG.md:835)。Worker 为手动 `wrangler deploy`，不在 Pages 自动部署 workflow 内，见 [deploy-pages.yml](/Users/holly/code/BaiXingAI/jianshuo.dev/.github/workflows/deploy-pages.yml:15)。

## 审计发现的缺口

### 高优先级：退款/撤销回调未验签也未回查 Apple

文件头声称客户端和 Apple 通知都只是“线索”，入账前统一回查 Apple，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:5)。这对首购/续费基本成立，但对 `REFUND` / `REVOKE` 不成立：

1. `signedPayload` 只 base64 decode，未校验 Apple JWS 签名/x509 链，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:28) 和 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:170)。
2. 退款/撤销分支不调 App Store Server API，直接使用未验签 payload 里的 transaction ID 清空桶，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:175)。

这意味着公开 webhook 会相信伪造的退款类型；攻击者还需知道一个已入账 transaction ID，所以不是“任意账号一键清零”，但仍是明确的信任边界缺口。建议修正为：对 ASN V2 做 Apple JWS 签名链验证，或在所有发生状态变更前用 App Store Server API 查询并确认撤销/退款事实。同时补一个“伪造 REFUND 不能改变状态”的测试；当前测试反而直接认可了未验签 REFUND 的清零行为，见 [iap.test.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/test/iap.test.js:195)。

### 中优先级：生产 ASN V2 配置未证实

如果 App Store Connect 尚未配通知 URL：

- 首购以及续费可由 iOS `currentEntitlements -> claim` 在 App 下次启动时兜底，但用户不启动 App 就不会实时入账；
- 退款/撤销没有对等的客户端 claim 兜底，已发的余额将直到订阅桶自然过期才失效，后端 `iap_sub` 也不会及时得到 `revoked`。

因此“没配 webhook 也不丢续费”只是最终一致的部分兜底，不能代替生产回调。应在 App Store Connect 确认生产和 sandbox URL 均为 `https://jianshuo.dev/agent/iap/notifications`，并用 Apple 的 Test Notification/沙盒续费/退款做端到端验证。

### 高优先级：交易幂等记录与算力发放不是原子操作

新交易的处理顺序是先 `INSERT OR IGNORE INTO iap_txn`，再调用 `grantBucket` 发放算力，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:113)。如果交易记录插入成功后，算力桶创建或后续更新失败：

- 本次请求会返回 500；
- Apple 或客户端重试时，同一 `transaction_id` 已经存在，会被判为 `already`；
- `isNew=false` 后不会再次执行 `grantBucket`，可能形成“Apple 已扣款、后端永久未发算力”的漏发。

建议把“认领交易 + 创建算力桶 + 回写 bucket_id”改为可恢复的状态机或原子批处理：例如给 `iap_txn` 增加 `processing/granted/failed` 状态，只有确认桶存在后才标记完成，并允许没有 `bucket_id` 的交易安全补偿重试。还应增加“插入成功后 grantBucket 失败，第二次重试最终到账”的故障注入测试。

### 中优先级：依赖降级时返回 200 会丢失 Apple 重试

当 ASC secrets 或 `USAGE` 绑定缺失时，通知端点返回 HTTP 200 `skipped:"degraded"`，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:167)。Apple 会把 2xx 视为已接收，不会因这次临时故障重试，可能永久漏掉续费、退款或撤销通知。

建议对临时依赖故障返回 5xx，让 Apple 按 Notifications V2 策略重试；只有不可恢复的坏 payload 才返回 2xx skipped。

### 中优先级：历史周期退款会撤销整条订阅链

`revokeTransaction` 无论退款的是哪个周期，都会把该 `original_txn_id` 对应的 `iap_sub.status` 直接改成 `revoked`，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:135)。如果用户已经续费到新周期，Apple 后续又退掉较早周期，这可能暂时把仍未到期的新周期显示成未订阅。

建议退款时只回收该 transaction 对应的桶，再根据 Apple 当前订阅状态或该订阅链最新有效交易重算 `iap_sub`，不要用任意历史交易的退款直接覆盖整条链状态。

### 中优先级：只是权益账本，缺少商业对账数据

`iap_txn` 足以回答“这个 Apple 交易是否已发过算力”，但不足以回答“实收多少、哪个区、Apple 抽成/税后多少、退款了多少、通知是否漏了、与 Apple 财务报表是否一致”。如果业务开始需要运营、客服和财务对账，建议增加不可变的通知/交易事件存档，保存至少 notification UUID/type/subtype、signed transaction 验证结果、purchase/expires/revocation 时间、storefront、currency/price，并增加对账查询。

### 其他边界

- 账号绑定不用 StoreKit `appAccountToken`，而是 first-claim-wins，这是有意设计，见 [实施计划](/Users/holly/code/BaiXingAI/jianshuo.dev/docs/superpowers/plans/2026-07-17-voicedrop-subscription-p3p4.md:14)。它简单有效，但缺少转移/解绑的正式业务流程。
- `iap_sub.status` 只有 `active/revoked`，不表达 billing retry、grace period、自动续费开关、expired 等完整生命周期；过期是查询时用 `expires_date` 推导。
- 退款只清未使用余额，不追回已花掉的算力，这是当前明确策略，见 [iap.js](/Users/holly/code/BaiXingAI/jianshuo.dev/agent/src/iap.js:135)。
- 生产 D1 的实际行数和当前订阅用户未在本次审计中读取；本地非交互环境没有 `CLOUDFLARE_API_TOKEN`，无法直接执行 `wrangler secret list` 或远程 D1 查询。这不影响对代码、已记录 migration 和生产路由的判断，但意味着“现在已有多少真实订单”仍未审计。

## 给产品决策的一句话

> 用户购买后，Apple 负责收款；VoiceDrop 后端已能校验 Apple 交易、幂等发放每月 200 算力并处理续费/退款通知。但它目前是“订阅权益账本”，不是“完整订单/财务对账系统”；同时必须去 App Store Connect 确认生产回调 URL 已配，并优先修复退款/撤销分支未验签也未回查 Apple 的信任边界缺口。
