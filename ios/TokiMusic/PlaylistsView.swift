import SwiftUI
struct PlaylistsView: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        NavigationStack {
            List {
                if model.profile == nil && !model.isDemo { Text("登录后查看我的歌单").foregroundStyle(.secondary) }
                ForEach(groupPlaylists(model.myPlaylists.isEmpty ? MockData.playlists : model.myPlaylists, myUid: model.profile?.id ?? 0), id: \.0) { group, list in
                    Section("\(group.rawValue) · \(list.count)") {
                        ForEach(list) { pl in NavigationLink { PlaylistDetailView(playlist: pl) } label: {
                            HStack { AsyncImage(url: URL(string: pl.coverUrl)).frame(width: 52, height: 52).clipShape(RoundedRectangle(cornerRadius: 18))
                                VStack(alignment: .leading) { Text(pl.name).lineLimit(1); Text("\(pl.trackCount) 首 · \(pl.creatorName)").font(.caption).foregroundStyle(.secondary) } } } }
                    }
                }
            }.navigationTitle("歌单")
        }
    }
}
struct PlaylistDetailView: View { @EnvironmentObject var model: AppModel; var playlist: PlaylistItem
    var body: some View { List { Section(playlist.name) { ForEach(MockData.songs) { SongRow(song: $0) } } }.navigationTitle(playlist.name) } }
