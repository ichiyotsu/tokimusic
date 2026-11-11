import SwiftUI
struct MineView: View {
    @EnvironmentObject var model: AppModel
    @AppStorage("apiBase") private var apiBase = "http://127.0.0.1:3000"
    @State private var showLogin = false
    var body: some View {
        NavigationStack { List {
            Section { HStack { AsyncImage(url: URL(string: model.profile?.avatarUrl ?? "")).frame(width: 60, height: 60).clipShape(Circle())
                VStack(alignment: .leading) { Text(model.profile?.nickname ?? "未登录").font(.title3.bold())
                    Text(model.profile.map { "Lv.\($0.level) · 关注 \($0.follows) · 粉丝 \($0.followeds)" } ?? "扫码登录网易云").font(.caption).foregroundStyle(.secondary) } }
                if model.profile == nil { Button("二维码登录") { showLogin = true } } }
            Section("统计") {
                HStack { StatBox(label: "收藏歌曲", value: "\(model.likeCount)"); StatBox(label: "听歌时长", value: "\(model.estimatedListeningMinutes / 60) 小时"); StatBox(label: "歌单", value: "\(model.myPlaylists.count)") }
                Text("听歌时长由听歌排行（/user/record）估算").font(.caption).foregroundStyle(.secondary)
            }
            Section { NavigationLink("我的收藏") { SongListView(title: "我的收藏", songs: model.recent) }
                NavigationLink("最近播放") { SongListView(title: "最近播放", songs: model.recent) }
                NavigationLink("听歌排行") { SongListView(title: "听歌排行", songs: model.record.map(\.song)) } }
            Section("设置") {
                TextField("API 地址", text: $apiBase).autocorrectionDisabled()
                Picker("主题", selection: Binding(get: { "跟随系统" }, set: { _ in })) { Text("跟随系统").tag("跟随系统"); Text("浅色").tag("浅色"); Text("深色").tag("深色") }
                // Version read from bundle (Info.plist 0.1.0), never hardcoded in the view body.
                LabeledContent("版本", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "-")
                Text("检查更新：正式发布后读取 GitHub Releases（ichiyotsu/tokimusic）比对版本号").font(.caption).foregroundStyle(.secondary)
            }
        }.navigationTitle("我的").sheet(isPresented: $showLogin) { LoginView() } }
    }
}
struct StatBox: View { var label: String; var value: String
    var body: some View { VStack { Text(value).font(.title3.bold()); Text(label).font(.caption).foregroundStyle(.secondary) }.frame(maxWidth: .infinity).padding(.vertical, 8) } }
struct SongListView: View { var title: String; var songs: [Song]; var body: some View { List(songs) { SongRow(song: $0) }.navigationTitle(title) } }
struct LoginView: View {
    @EnvironmentObject var model: AppModel; @State private var status = "使用网易云音乐 App 扫码"; @State private var qrImg = ""
    var body: some View { VStack(spacing: 16) { Text("扫码登录网易云").font(.title2.bold())
        Text("流程：/login/qr/key → /login/qr/create 展示二维码 → 轮询 /login/qr/check，803 保存 cookie").font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
        Text(status) }.padding().task { await poll() } }
    func poll() async { // 800 expired / 801 waiting / 802 scanned / 803 success
        guard let key = try? await model.api.qrKey() else { status = "无法连接 API 服务"; return }
        for _ in 0..<60 { guard let r = try? await model.api.qrCheck(key) else { return }
            status = [800: "二维码已过期", 801: "等待扫码", 802: "已扫码，请确认", 803: "登录成功"][r.code] ?? status
            if r.code == 803 || r.code == 800 { break }; try? await Task.sleep(nanoseconds: 2_000_000_000) }
    }
}
