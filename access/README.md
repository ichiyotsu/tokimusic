# TokiMusic 远程访问配置（access/blocklist.json）

零后端设计：应用在启动时（且至多每天一次）从仓库 main 分支拉取本
文件，缓存上一份成功副本；拉取失败一律 fail open（应用照常工作）。

## 文件结构

```json
{
  "version": 1,
  "entries": [
    { "hash": "<64 位小写 hex>", "action": "block" }
  ],
  "overrides": [
    { "hash": "<64 位小写 hex>", "tokiId": "<10 位数字>" }
  ]
}
```

## 身份与哈希

- 身份规范化串：`toki|{tokiId}`、`netease|{uid}`、`qq|{uin}`。
- `hash` = 该规范化串的 SHA-256 hex（小写）。文件里**只存哈希**，
  不存任何明文 ID、不写个人备注。

## entries（封禁）

- `action` 当前只有 `"block"`。应用把自己的身份集（设备派生
  Toki ID + 生效 Toki ID + 已登录平台 ID）逐个哈希后比对，任一
  命中 block 即盖全屏受限页。

## overrides（Toki ID 覆盖，owner 专属）

- Toki ID 在应用内只读，用户永远不能改；唯一改写通道是本段。
- 键（hash）可以针对设备派生 Toki ID，也可以针对绑定的平台 ID。
- 值（tokiId）必须是 owner 签发的合法 10 位数字 ID；格式不合法的
  条目会被应用忽略。

## 治理

- entries 与 overrides 的任何条目，**只**在仓库所有者（Ichiyotsu）
  本人亲自给出目标并明确指示时添加；没有自动或第三方来源。
