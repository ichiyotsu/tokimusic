import SwiftUI
import TokiMusicCore
struct PlayerView: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        VStack(alignment: .leading) {
            if let song = model.currentSong {
                HStack { AsyncImage(url: URL(string: song.picUrl)).frame(width: 72, height: 72).clipShape(RoundedRectangle(cornerRadius: 18))
                    VStack(alignment: .leading) { Text(song.name).font(.title3.bold()); Text(song.artistText).foregroundStyle(.secondary) } }.padding()
                LyricsView(lines: model.lyrics, positionMs: model.positionMs) // left-aligned, word-by-word
                HStack { Text(formatMs(model.positionMs)); Spacer(); Text(formatMs(Int(song.durationMs))) }.padding(.horizontal)
                HStack(spacing: 32) { Spacer(); Image(systemName: "backward.fill").font(.title2); Button { model.toggle() } label: { Image(systemName: model.isPlaying ? "pause.circle.fill" : "play.circle.fill").font(.system(size: 56)) }; Image(systemName: "forward.fill").font(.title2); Spacer() }.padding()
            } else { Text("暂无播放中的歌曲").padding() }
        }
    }
    func formatMs(_ ms: Int) -> String { String(format: "%d:%02d", ms / 60_000, ms / 1_000 % 60) }
}
/// Lyrics are LEFT-aligned: active line auto-scrolls to viewport centre, text itself stays leading-aligned.
/// Sung words fill with primary; unsung use secondary. Translation sits under the line, also leading-aligned.
struct LyricsView: View {
    var lines: [LyricLine]; var positionMs: Int
    var body: some View {
        let active = YrcParser.activeLineIndex(lines, positionMs)
        ScrollViewReader { proxy in ScrollView { LazyVStack(alignment: .leading, spacing: 14) {
            ForEach(Array(lines.enumerated()), id: \.offset) { i, line in
                VStack(alignment: .leading, spacing: 2) {
                    lyricText(line, isActive: i == active, past: i < active).font(i == active ? .title3.bold() : .body).frame(maxWidth: .infinity, alignment: .leading)
                    if let t = line.translation { Text(t).font(.caption).foregroundStyle(.secondary) }
                }.id(i)
            } }.padding(24) }
            .onChange(of: active) { _, new in if new >= 0 { withAnimation { proxy.scrollTo(new, anchor: .center) } } } }
    }
    func lyricText(_ line: LyricLine, isActive: Bool, past: Bool) -> Text {
        line.words.reduce(Text("")) { acc, word in
            let sung = past ? word.text.count : (isActive ? Int(Double(word.text.count) * YrcParser.wordProgress(word, positionMs)) : 0)
            let sungPart = String(word.text.prefix(sung)); let rest = String(word.text.dropFirst(sung))
            return acc + Text(sungPart).foregroundColor(TokiPalette.primary(dark: false)) + Text(rest).foregroundColor(isActive ? .primary : .secondary)
        }
    }
}
