# 网易云接口清单（NeteaseCloudMusicApi 兼容服务）

基址可配置，默认 `http://127.0.0.1:3000`（模拟器默认 `http://10.0.2.2:3000`），在「我的 → 设置 → API 地址」修改，旁边有「测试连接」按钮。
登录后 cookie 持久化（本机存储），后续请求带 `cookie` 查询参数；扫码流程的匿名会话 cookie（NMTID 等）由 OkHttp CookieJar 在请求头里携带，登录凭证本身只走查询参数。

服务端版本钉在 `NeteaseCloudMusicApi@4.32.0`（不要用 `@latest` 裸奔，服务端升级会静默改行为）。

## 登录（二码登录）

| 接口 | 用途 |
|---|---|
| `GET /login/qr/key` | 取 unikey |
| `GET /login/qr/create?key=&qrimg=true` | 生成二维码（含 base64 图） |
| `GET /login/qr/check?key=` | 轮询：800 过期 / 801 待扫码 / 802 待确认 / 803 成功（返回 cookie） |
| `GET /login/status` | 登录状态（权威判断：`data.code` 200 已登录 / 301 未登录或已过期） |
| `GET /login/refresh` | 登录续期（启动时调一次，失败忽略） |
| `GET /user/account` | 当前账号 profile |

流程：key → create 展示二维码 → 每 2s check → 803 保存 cookie → /user/account 拉头像昵称。
注意：扫码三步全程不带已存的登录 cookie（旧 cookie 会让扫码后变“已过期”）；`/login/qr/key` 下发的 `Set-Cookie`（NMTID）必须保留并带回后续轮询。803 时 body 里没有 cookie 且 `Set-Cookie` 里也没有时，算登录失败，不要显示“登录成功”。
未登录时需登录的接口返回响应体 `code: 301`（HTTP 仍是 200），App 据此提示“登录已过期，请重新扫码”，而不是悄悄当成未登录；网络不通时保持旧登录态并提示离线。

## 主页

| 接口 | 用途 |
|---|---|
| `GET /recommend/songs` | 每日推荐歌曲（登录后） |
| `GET /user/record?uid=&type=1` | 最近一周听歌排行——私人雷达的来源（接口没有专门的雷达端点，雷达按口味取排行前 20 首） |
| `GET /history/recent/songs` | 最近播放（登录后） |
| `GET /personalized?limit=` | 推荐歌单 |
| `GET /personalized/newsong` | 推荐新歌 |
| `GET /search?keywords=&type=1/10/100/1000` / `GET /cloudsearch` | 搜索（单曲/专辑/歌手/歌单） |

## 歌单

| 接口 | 用途 |
|---|---|
| `GET /user/playlist?uid=` | 用户歌单 |
| `GET /playlist/detail?id=` | 歌单详情 + 曲目 |
| `GET /playlist/catlist` | 歌单分类 |

分组逻辑：`subscribed=false` 且 creator 为本人 → 我创建的；`subscribed=true` → 我收藏的；
名为「我喜欢的音乐」的歌单置顶为「喜欢的音乐」分组。

## 我的

| 接口 | 用途 |
|---|---|
| `GET /user/detail?uid=` | 等级、关注、粉丝 |
| `GET /user/subcount` | 收藏统计 |
| `GET /likelist?uid=` | 喜欢的歌曲 id 列表（收藏歌曲数 = 列表长度） |
| `GET /user/record?uid=&type=0/1` | 听歌排行（全部/最近一周），累计 playCount 展示听歌次数 |
| `GET /history/recent/songs` | 最近播放 |

听歌时长说明：网易云接口不直接返回总时长，App 用 record 中歌曲时长 × 播放次数估算，
界面标注「由听歌排行估算」。

## 播放与歌词

| 接口 | 用途 |
|---|---|
| `GET /song/url/v1?id=&level=standard` | 播放地址；空地址时再试 `level=exhigh`，仍为空说明无版权或需 VIP，不静默播放 |
| `GET /song/detail?ids=` | 歌曲详情（封面/歌手/时长） |
| `GET /like?id=&like=true/false` | 喜欢 / 取消喜欢（播放页星标） |
| `GET /lyric?id=` | LRC 歌词 + 翻译 |
| `GET /lyric/new?id=` | **YRC 逐字歌词** + LRC + 翻译（优先使用） |

YRC 格式：`[行开始ms,行时长ms](词开始ms,词时长ms,0)词(…)词…`，详见三端 YrcParser 与单元测试。
