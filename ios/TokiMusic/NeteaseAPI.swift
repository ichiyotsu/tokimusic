import Foundation
/// Isomorphic NeteaseCloudMusicApi client. Base URL configurable (default http://127.0.0.1:3000), cookie persisted in UserDefaults.
final class NeteaseAPI {
    static let shared = NeteaseAPI()
    var base: String { get { UserDefaults.standard.string(forKey: "apiBase") ?? "http://127.0.0.1:3000" } set { UserDefaults.standard.set(newValue, forKey: "apiBase") } }
    var cookie: String { get { UserDefaults.standard.string(forKey: "cookie") ?? "" } set { UserDefaults.standard.set(newValue, forKey: "cookie") } }
    func get(_ path: String) async throws -> [String: Any] {
        var url = base + path
        if !cookie.isEmpty { url += (path.contains("?") ? "&" : "?") + "cookie=" + (cookie.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? cookie) }
        var req = URLRequest(url: URL(string: url)!); req.setValue(cookie, forHTTPHeaderField: "Cookie")
        let (data, _) = try await URLSession.shared.data(for: req)
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
    // Endpoints: /login/qr/key, /login/qr/create, /login/qr/check, /login/status, /user/account,
    // /banner, /personalized, /personalized/newsong, /recommend/songs, /cloudsearch,
    // /user/playlist, /playlist/detail, /playlist/catlist, /user/detail, /user/subcount,
    // /likelist, /user/record, /history/recent/songs, /song/url/v1, /song/detail, /lyric/new, /lyric
    func qrKey() async throws -> String { ((try await get("/login/qr/key"))["data"] as? [String: Any])?["unikey"] as? String ?? "" }
    /// /login/qr/create with qrimg=true returns data.qrimg as a "data:image/png;base64,..." string (same field Android decodes).
    func qrCreate(_ key: String) async throws -> String { ((try await get("/login/qr/create?key=\(key)&qrimg=true"))["data"] as? [String: Any])?["qrimg"] as? String ?? "" }
    func qrCheck(_ key: String) async throws -> (code: Int, cookie: String) {
        let o = try await get("/login/qr/check?key=\(key)"); let c = o["cookie"] as? String ?? ""
        if (o["code"] as? Int) == 803 { cookie = c }; return ((o["code"] as? Int) ?? 0, c)
    }
    func account() async throws -> UserProfile? {
        guard !cookie.isEmpty, let p = (try await get("/user/account"))["profile"] as? [String: Any] else { return nil }
        return UserProfile(id: p["userId"] as? Int64 ?? 0, nickname: p["nickname"] as? String ?? "", avatarUrl: p["avatarUrl"] as? String ?? "")
    }
    func personalized() async throws -> [[String: Any]] { (try await get("/personalized?limit=12"))["result"] as? [[String: Any]] ?? [] }
    func lyric(id: Int64) async throws -> (yrc: String?, lrc: String?, tlyric: String?) {
        let o = (try? await get("/lyric/new?id=\(id)")) ?? (try await get("/lyric?id=\(id)"))
        return ((o["yrc"] as? [String: Any])?["lyric"] as? String, (o["lrc"] as? [String: Any])?["lyric"] as? String, (o["tlyric"] as? [String: Any])?["lyric"] as? String)
    }
    func songUrl(id: Int64) async throws -> String? { ((try await get("/song/url/v1?id=\(id)&level=standard"))["data"] as? [[String: Any]])?.first?["url"] as? String }
}
enum MockData {
    static let songs = [Song(id: 1, name: "Demo 歌曲 · 晴天练习曲", artists: [Artist(id: 1, name: "Toki Demo Band")], albumName: "Demo 专辑", durationMs: 214_000),
                        Song(id: 2, name: "Demo 歌曲 · 抹茶与海", artists: [Artist(id: 2, name: "Demo Singer")], albumName: "Demo 专辑", durationMs: 187_000)]
    static let playlists = [PlaylistItem(id: 11, name: "Demo 用户喜欢的音乐", trackCount: 2, creatorName: "Demo 用户", creatorId: 10001),
                            PlaylistItem(id: 13, name: "Demo · 收藏的华语精选", trackCount: 50, creatorName: "其他用户", creatorId: 20002, subscribed: true)]
    static let demoYrc = "[0,3000](0,600,0)这(600,600,0)是(1200,900,0)逐(2100,900,0)字\n[3000,3000](3000,800,0)歌(3800,800,0)词(4600,1400,0)演示"
}
