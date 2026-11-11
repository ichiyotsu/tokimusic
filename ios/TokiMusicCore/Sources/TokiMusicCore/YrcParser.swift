import Foundation
public struct LyricWord: Equatable { public var startMs: Int; public var durationMs: Int; public var text: String
    public init(_ s: Int, _ d: Int, _ t: String) { startMs = s; durationMs = d; text = t } }
public struct LyricLine: Equatable { public var startMs: Int; public var durationMs: Int; public var words: [LyricWord]; public var translation: String?
    public var text: String { words.map(\.text).joined() }; public var endMs: Int { startMs + durationMs } }
/// Parses NetEase YRC word-by-word lyrics; falls back to LRC whole-line lyrics.
/// YRC line: [startMs,durationMs](wordStartMs,wordDurationMs,0)word(...)word
public enum YrcParser {
    public static func parse(yrc: String?, lrc: String?, tlyric: String? = nil) -> [LyricLine] {
        var lines: [LyricLine]
        if let yrc, !yrc.isEmpty { lines = parseYrc(yrc); if lines.isEmpty { lines = parseLrc(yrc) } }
        else if let lrc { lines = parseLrc(lrc) } else { lines = [] }
        if let tlyric, !tlyric.isEmpty {
            let trans = parseLrc(tlyric)
            for i in lines.indices { lines[i].translation = trans.min(by: { abs($0.startMs - lines[i].startMs) < abs($1.startMs - lines[i].startMs) }).flatMap { abs($0.startMs - lines[i].startMs) <= 500 ? $0.text : nil } }
        }
        return lines.sorted { $0.startMs < $1.startMs }
    }
    public static func parseYrc(_ raw: String) -> [LyricLine] {
        raw.split(separator: "\n").compactMap { rawLine in
            let line = String(rawLine).trimmingCharacters(in: .whitespaces)
            guard let head = match(#"\[(\d+),(\d+)]"#, in: line), let start = Int(head[1]), let dur = Int(head[2]) else { return nil }
            let words = allMatches(#"\((\d+),(\d+),\d+\)([^(]*)"#, in: line).compactMap { m -> LyricWord? in
                guard let s = Int(m[1]), let d = Int(m[2]) else { return nil }; return LyricWord(s, d, m[3]) }
            return words.isEmpty ? nil : LyricLine(startMs: start, durationMs: dur, words: words, translation: nil)
        }
    }
    public static func parseLrc(_ raw: String) -> [LyricLine] {
        var out: [LyricLine] = []
        for rawLine in raw.split(separator: "\n") {
            let line = String(rawLine); let tags = allMatches(#"\[(\d+):(\d+)(?:\.(\d{1,3}))?]"#, in: line); guard let last = tags.last else { continue }
            let text = String(line[line.index(line.startIndex, offsetBy: last.range.upperBound)...]).trimmingCharacters(in: .whitespaces)
            guard !text.isEmpty else { continue }
            for tag in tags { let ms = (Int(tag[1]) ?? 0) * 60_000 + (Int(tag[2]) ?? 0) * 1_000 + Int((tag[3] + "000").prefix(3))!
                out.append(LyricLine(startMs: ms, durationMs: 0, words: [LyricWord(ms, 0, text)], translation: nil)) }
        }
        out.sort { $0.startMs < $1.startMs }
        for i in out.indices { out[i].durationMs = i + 1 < out.count ? out[i + 1].startMs - out[i].startMs : 4_000 }
        return out
    }
    public static func activeLineIndex(_ lines: [LyricLine], _ positionMs: Int) -> Int { lines.lastIndex { $0.startMs <= positionMs } ?? -1 }
    public static func wordProgress(_ word: LyricWord, _ positionMs: Int) -> Double {
        if word.durationMs <= 0 { return positionMs >= word.startMs ? 1 : 0 }
        return min(1, max(0, Double(positionMs - word.startMs) / Double(word.durationMs)))
    }
    private static func match(_ pattern: String, in s: String) -> (range: Range<String.Index>, [String])? { allMatches(pattern, in: s).first.map { ($0.range, $0.groups) } }
    private static func allMatches(_ pattern: String, in s: String) -> [(range: Range<String.Index>, groups: [String])] {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return [] }
        return re.matches(in: s, range: NSRange(s.startIndex..., in: s)).map { m in
            (Range(m.range, in: s)!, (0..<m.numberOfRanges).map { i in m.range(at: i).location == NSNotFound ? "" : String(s[Range(m.range(at: i), in: s)!]) }) }
    }
}
