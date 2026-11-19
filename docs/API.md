# 网易云接口（Android 直连）

Android 不再经过自建服务：App 直接对网易的服务器发请求，加密与
请求构造是 `NeteaseCloudMusicApi@4.32.0` 的原生 Kotlin 移植
（`NeteaseCrypto` + `NeteaseClient`），行为与上游 4.32.0 的
`util/request.js` + 各端点模块逐项对齐。iOS / Windows 目前仍走
自建兼容服务（见文末），三端共用的接口语义不变。

两个直连目标：`https://music.163.com`（weapi）与
`https://interface.music.163.com`（eapi）。`encryptResponse=false`，
响应不加密，直接是 JSON。

## 加密方式

- **weapi**：JSON 先后用预置 key（`0CoJUm6Qyw8W8jud`）与随机 16 位
  secretKey 做两次 AES-128-CBC，`params` 为 base64；`encSecKey` 是
  secretKey 反转后做无填充 RSA（上游公钥）的十六进制。请求带
  `Referer: https://music.163.com`、Chrome/Edge UA 与完整 Cookie 头。
- **eapi**：`url-36cd479b6b5-{json}-36cd479b6b5-{md5}` 做一次
  AES-128-ECB（key `e82ckenh8dichen8`），`params` 为大写十六进制。
  请求带 iPhone UA（`NeteaseMusic 9.0.90/5038`），Cookie 头只含
  eapi header 对象里的字段（osver、deviceId、os、appver、versioncode
  140、buildver、resolution、__csrf、channel、requestId、MUSIC_U/MUSIC_A）。
- 两种方式的 JSON 里都带 `e_r: false`；weapi 另带 `csrf_token`。

向量校验：`NeteaseCryptoTest` 里的 9 个用例是直接跑上游 Node 代码
生成的，Kotlin 输出必须与 Node 逐字节一致，否则网易会拒绝请求。

## 匿名身份

没有登录 cookie 时，用匿名身份：设备 id 是 52 位大写十六进制
（生成一次后存在 SharedPreferences），首次需要时调
`POST /api/register/anonimous`（weapi，username =
Base64(deviceId + " " + cloudmusic_dll_encode_id(deviceId))）换
`MUSIC_A`（有效期到 2094 年），存在登录 cookie 串里复用；注册失败
不阻塞请求，只是这次没有 MUSIC_A。登录后只用 `MUSIC_U`，
不再带 MUSIC_A。

Cookie 由 App 内存在内存里管理（不是 OkHttp CookieJar，避免与
手工拼的 Cookie 头重复）：`Set-Cookie` 会合并进来，登录 cookie
（MUSIC_U / __remember_me / __csrf）持久化到 SharedPreferences；
非登录接口每次带一个新生成的 NMTID（32 位十六进制）。

## 登录（二维码登录）

| 上游接口 | 加密 | 用途 |
|---|---|---|
| `POST /api/login/qrcode/unikey`（`{type:3}`） | eapi | 取 unikey |
| 二维码图片 | 本地生成 | 内容 `https://music.163.com/login?codekey={key}`，ZXing 生成 512×512 PNG，拼成 `data:image/png;base64,…` 给登录弹窗显示 |
| `POST /api/login/qrcode/client/login`（`{key, type:3}`） | eapi | 轮询：800 过期 / 801 待扫码 / 802 待确认 / 803 成功（返回 cookie） |
| `POST /api/w/nuser/account/get`（`{}`） | weapi | 登录状态（权威判断：`code` 200 已登录 / 301 未登录或已过期） |
| `POST /api/login/token/refresh`（`{}`） | eapi | 登录续期（启动时调一次，失败忽略） |
| `POST /api/nuser/account/get`（`{}`） | weapi | 当前账号 profile |

流程：unikey → 本地生成二维码 → 每 2s check → 803 保存 cookie → /nuser/account 拉头像昵称。
注意：扫码三步全程不带已存的登录 cookie（旧 cookie 会让扫码后变“已过期”）；`/login/qrcode/unikey` 下发的 `Set-Cookie`（NMTID）必须保留并带回后续轮询。803 时 body 里没有 cookie 且 `Set-Cookie` 里也没有时，算登录失败，不要显示“登录成功”。
未登录时需登录的接口返回响应体 `code: 301`（HTTP 仍是 200），App 据此提示“登录已过期，请重新扫码”，而不是悄悄当成未登录；网络不通时保持旧登录态并提示离线。

## 主页

| 上游接口 | 加密 | 用途 |
|---|---|---|
| `POST /api/v3/discovery/recommend/songs`（`{}`） | weapi | 每日推荐歌曲（登录后） |
| `POST /api/v1/play/record`（`{uid, type:1}`） | weapi | 最近一周听歌排行——私人雷达的来源（接口没有专门的雷达端点，雷达按口味取排行前 20 首） |
| `POST /api/discovery/recommend/songs/history/recent`（`{}`） | weapi | 最近播放（登录后） |
| `POST /api/personalized/playlist`（`{limit, total:true, n:1000}`） | weapi | 推荐歌单 |
| `POST /api/personalized/newsong`（`{type:"recommend", limit:10, areaId:0}`） | weapi | 推荐新歌 |
| `POST /api/cloudsearch/pc`（`{s, type, limit, offset:0, total:true}`） | eapi | 搜索（单曲 type=1 / 歌单 type=1000 等） |

## 歌单

| 上游接口 | 加密 | 用途 |
|---|---|---|
| `POST /api/user/playlist`（`{uid, limit:100, offset:0, includeVideo:true}`） | weapi | 用户歌单 |
| `POST /api/v6/playlist/detail`（`{id, n:100000, s:8}`） | eapi | 歌单详情 + 曲目 |
| `POST /api/playlist/catalogue`（`{}`） | eapi | 歌单分类 |

分组逻辑：`subscribed=false` 且 creator 为本人 → 我创建的；`subscribed=true` → 我收藏的；
名为「我喜欢的音乐」的歌单置顶为「喜欢的音乐」分组。

## 我的

| 上游接口 | 加密 | 用途 |
|---|---|---|
| `POST /api/v1/user/detail/{uid}`（`{}`） | weapi | 等级、关注、粉丝 |
| `GET /user/subcount` | — | 收藏统计（iOS/Windows 经服务；Android 暂未接） |
| `POST /api/song/like/get`（`{uid}`） | eapi | 喜欢的歌曲 id 列表（收藏歌曲数 = 列表长度） |
| `POST /api/v1/play/record`（`{uid, type:0/1}`） | weapi | 听歌排行（全部/最近一周），累计 playCount 展示听歌次数 |
| `POST /api/discovery/recommend/songs/history/recent`（`{}`） | weapi | 最近播放 |

听歌时长说明：网易云接口不直接返回总时长，App 用 record 中歌曲时长 × 播放次数估算，
界面标注「由听歌排行估算」。

## 播放与歌词

| 上游接口 | 加密 | 用途 |
|---|---|---|
| `POST /api/song/enhance/player/url/v1`（`{ids:"[id]", level, encodeType:"flac"}`） | eapi | 播放地址；空地址时再试 `level=exhigh`，仍为空说明无版权或需 VIP，不静默播放 |
| `POST /api/v3/song/detail`（`{c:"[{\"id\":…}]"}`） | weapi | 歌曲详情（封面/歌手/时长） |
| `POST /api/radio/like`（`{alg:"itembased", trackId, like, time:"3"}`） | weapi | 喜欢 / 取消喜欢（播放页星标） |
| `POST /api/song/lyric`（`{id, tv:-1, lv:-1, rv:-1, kv:-1, _nmclfl:1}`） | eapi | LRC 歌词 + 翻译 |
| `POST /api/song/lyric/v1`（`{id, cp:false, tv:0, lv:0, rv:0, kv:0, yv:0, ytv:0, yrv:0}`） | eapi | **YRC 逐字歌词** + LRC + 翻译（优先使用） |

YRC 格式：`[行开始ms,行时长ms](词开始ms,词时长ms,0)词(…)词…`，详见三端 YrcParser 与单元测试。

## 已知限制

- 播放地址接口（`/song/url/v1`）在机房/代理 IP 下会被网易风控
  （body `code: -460`，提示“网络环境存在风险”），此时 App 按无
  版权处理（跳过并提示），与之前经自建服务从同一类 IP 访问时一致；
  真机住宅/移动网络 IP 一般不受影响，仍需真机验证。
- iOS / Windows 仍走自建 `NeteaseCloudMusicApi@4.32.0` 服务（基址
  可配置），直连移植暂未搬过去；`/user/subcount` 在 Android 上暂未接。

## 验证

- 上游代码取自 npm 的 `NeteaseCloudMusicApi@4.32.0` 精确版本包
  （`workspace/work/NeteaseCloudMusicApi-4.32.0/` 仅作对照，不进仓库）。
- 直连可行性已用上游自己的 `util/request.js` 在本机实测：
  banner / personalized / cloudsearch / lyric_new / login_qr_key /
  register_anonimous 均返回 200 真实数据；`/song/url/v1` 在本机
  出口 IP 下返回 -460 风控（见上）。
- `NeteaseClientTest` 用 MockWebServer 挡住两个直连域名，解密收到
  的 eapi 请求体，断言端点、加密方式与请求内容与上游一致。
