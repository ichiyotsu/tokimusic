import Foundation
import AVFoundation
import TokiMusicCore
@MainActor final class AppModel: ObservableObject {
    @Published var profile: UserProfile?; @Published var isDemo = false
    @Published var homePlaylists: [PlaylistItem] = []; @Published var homeSongs: [Song] = MockData.songs
    @Published var myPlaylists: [PlaylistItem] = []; @Published var likeCount = 0; @Published var record: [RecordItem] = []; @Published var recent: [Song] = []
    @Published var currentSong: Song?; @Published var isPlaying = false; @Published var lyrics: [LyricLine] = []
    @Published var positionMs: Int = 0
    let api = NeteaseAPI.shared
    private var player: AVPlayer?; private var timer: Timer?
    var estimatedListeningMinutes: Int { record.reduce(0) { $0 + Int($1.song.durationMs) * $1.playCount } / 60_000 } // from /user/record, labelled as estimate in UI
    func loadHome() async {
        do { let raw = try await api.personalized(); homePlaylists = raw.map { PlaylistItem(id: $0["id"] as? Int64 ?? 0, name: $0["name"] as? String ?? "", coverUrl: $0["picUrl"] as? String ?? "", trackCount: $0["trackCount"] as? Int ?? 0) }; isDemo = false }
        catch { isDemo = true; homePlaylists = MockData.playlists; homeSongs = MockData.songs }
        profile = try? await api.account()
    }
    func play(_ song: Song) async {
        currentSong = song
        if let urlStr = try? await api.songUrl(id: song.id), let url = URL(string: urlStr) { player = AVPlayer(url: url); player?.play(); isPlaying = true }
        if let l = try? await api.lyric(id: song.id) { lyrics = YrcParser.parse(yrc: l.yrc, lrc: l.lrc, tlyric: l.tlyric) }
        else { lyrics = YrcParser.parse(yrc: MockData.demoYrc, lrc: nil) }
        timer?.invalidate(); timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in guard let self, let p = self.player else { return }; self.positionMs = Int(p.currentTime().seconds * 1000) } }
    }
    func toggle() { guard let p = player else { return }; if isPlaying { p.pause() } else { p.play() }; isPlaying.toggle() }
}
