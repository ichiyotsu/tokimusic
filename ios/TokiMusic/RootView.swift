import SwiftUI
enum Dest: String, CaseIterable { case home = "主页", playlists = "歌单", mine = "我的"
    var icon: String { switch self { case .home: return "house"; case .playlists: return "music.note.list"; case .mine: return "person" } } }
/// Root: content + floating capsule tab bar (ultraThinMaterial glass = Android Haze counterpart) + MiniPlayer above it.
struct RootView: View {
    @EnvironmentObject var model: AppModel
    @State private var dest: Dest = .home
    @State private var showPlayer = false
    var body: some View {
        ZStack(alignment: .bottom) {
            Group { switch dest { case .home: HomeView(); case .playlists: PlaylistsView(); case .mine: MineView() } }
                .padding(.bottom, model.currentSong == nil ? 84 : 148)
            VStack(spacing: 8) {
                if let song = model.currentSong { MiniPlayerView(song: song) { showPlayer = true } }
                // FloatingNavBar复刻: 36pt capsule, glass material, selected = solid primary pill + expanded label.
                HStack(spacing: 4) {
                    ForEach(Dest.allCases, id: \.self) { d in
                        Button { dest = d } label: {
                            HStack(spacing: 8) { Image(systemName: d.icon); if dest == d { Text(d.rawValue).font(.subheadline.bold()) } }
                                .padding(.horizontal, 16).frame(height: 48)
                                .background(dest == d ? TokiPalette.primary(dark: false) : Color.clear, in: Capsule())
                                .foregroundStyle(dest == d ? Color.white : Color.secondary)
                        }.buttonStyle(.plain)
                    }
                }
                .padding(8).background(.ultraThinMaterial, in: Capsule())
            }.padding(.bottom, 8)
        }
        .sheet(isPresented: $showPlayer) { PlayerView() }
    }
}
struct MiniPlayerView: View {
    @EnvironmentObject var model: AppModel
    var song: Song; var onOpen: () -> Void
    var body: some View {
        HStack { AsyncImage(url: URL(string: song.picUrl)).frame(width: 40, height: 40).clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading) { Text(song.name).font(.subheadline.bold()).lineLimit(1); Text(song.artistText).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
            Spacer()
            Button { model.toggle() } label: { Image(systemName: model.isPlaying ? "pause.fill" : "play.fill") }
        }.padding(10).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 26)).padding(.horizontal, 16).onTapGesture(perform: onOpen)
    }
}
