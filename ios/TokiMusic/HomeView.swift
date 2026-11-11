import SwiftUI
struct HomeView: View {
    @EnvironmentObject var model: AppModel
    @State private var query = ""; @State private var showLogin = false
    var body: some View {
        NavigationStack {
            ScrollView { VStack(alignment: .leading, spacing: 20) {
                HStack { // capsule search + top-right avatar login (two states)
                    HStack { Image(systemName: "magnifyingglass"); TextField("搜索歌曲、歌手、歌单", text: $query) }.padding(12).background(.thinMaterial, in: Capsule())
                    Button { showLogin = true } label: { AsyncImage(url: URL(string: model.profile?.avatarUrl ?? "")) { $0.resizable() } placeholder: { Image(systemName: "person.circle.fill").resizable().foregroundStyle(.secondary) }.frame(width: 40, height: 40).clipShape(Circle()) }
                }
                if model.profile == nil { Text("未登录 · 点击右上角头像扫码登录").font(.caption).foregroundStyle(.secondary) }
                if model.isDemo { Text("Demo 数据 · 未连接网易云 API 服务").font(.caption).padding(8).background(TokiPalette.primaryContainer(dark: false), in: Capsule()) }
                Text("每日推荐").font(.title2.bold())
                ForEach(model.homeSongs) { song in SongRow(song: song) }
                Text("推荐歌单").font(.title2.bold())
                ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 12) { ForEach(model.homePlaylists) { pl in
                    VStack(alignment: .leading) { AsyncImage(url: URL(string: pl.coverUrl)).frame(width: 140, height: 140).clipShape(tokiCardCorners); Text(pl.name).font(.subheadline).frame(width: 140, alignment: .leading).lineLimit(2); Text("\(pl.trackCount) 首").font(.caption).foregroundStyle(.secondary) }
                } } }
                Text("推荐新歌").font(.title2.bold())
                ForEach(model.homeSongs) { song in SongRow(song: song) }
            }.padding(16) }
            .navigationTitle("TokiMusic")
            .sheet(isPresented: $showLogin) { LoginView() }
        }
    }
}
struct SongRow: View { @EnvironmentObject var model: AppModel; var song: Song
    var body: some View { Button { Task { await model.play(song) } } label: { HStack { AsyncImage(url: URL(string: song.picUrl)).frame(width: 48, height: 48).clipShape(RoundedRectangle(cornerRadius: 12))
        VStack(alignment: .leading) { Text(song.name).foregroundStyle(.primary).lineLimit(1); Text(song.artistText).font(.caption).foregroundStyle(.secondary).lineLimit(1) } } }.buttonStyle(.plain) } }
