import Foundation
struct Artist: Codable, Identifiable, Hashable { var id: Int64; var name: String }
struct Song: Codable, Identifiable, Hashable {
    var id: Int64; var name: String; var artists: [Artist]; var albumName: String = ""; var picUrl: String = ""; var durationMs: Int64 = 0
    var artistText: String { artists.map(\.name).joined(separator: " / ") }
}
struct PlaylistItem: Codable, Identifiable, Hashable {
    var id: Int64; var name: String; var coverUrl: String = ""; var trackCount: Int = 0
    var creatorName: String = ""; var creatorId: Int64 = 0; var subscribed: Bool = false; var playCount: Int64 = 0
}
struct UserProfile: Codable, Identifiable, Hashable { var id: Int64; var nickname: String; var avatarUrl: String = ""; var level: Int = 0; var follows: Int = 0; var followeds: Int = 0 }
struct RecordItem: Hashable { var song: Song; var playCount: Int }
enum PlaylistGroup: String, CaseIterable { case liked = "我喜欢的音乐", created = "我创建的歌单", subscribed = "我收藏的歌单" }
func groupPlaylists(_ all: [PlaylistItem], myUid: Int64) -> [(PlaylistGroup, [PlaylistItem])] {
    let liked = all.filter { $0.name.contains("喜欢的音乐") }
    let created = all.filter { !liked.contains($0) && !$0.subscribed && (myUid == 0 || $0.creatorId == myUid) }
    let sub = all.filter { !liked.contains($0) && $0.subscribed }
    return [(.liked, liked), (.created, created), (.subscribed, sub)].filter { !$0.1.isEmpty }
}
