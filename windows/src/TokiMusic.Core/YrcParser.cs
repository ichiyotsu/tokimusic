using System.Text.RegularExpressions;
namespace TokiMusic.Core;
public record LyricWord(long StartMs, long DurationMs, string Text);
public record LyricLine(long StartMs, long DurationMs, IReadOnlyList<LyricWord> Words, string? Translation = null)
{ public string Text => string.Concat(Words.Select(w => w.Text)); public long EndMs => StartMs + DurationMs; }
/// <summary>
/// NetEase YRC word-by-word lyric parser with LRC fallback — isomorphic with Android/iOS.
/// YRC line: [startMs,durationMs](wordStartMs,wordDurationMs,0)word(...)word
/// LRC line: [mm:ss.xx]whole line text
/// </summary>
public static partial class YrcParser
{
    [GeneratedRegex(@"\[(\d+),(\d+)]")] private static partial Regex YrcHead();
    [GeneratedRegex(@"\((\d+),(\d+),\d+\)([^(]*)")] private static partial Regex YrcWord();
    [GeneratedRegex(@"\[(\d+):(\d+)(?:\.(\d{1,3}))?\]")] private static partial Regex LrcTag();
    public static IReadOnlyList<LyricLine> Parse(string? yrc, string? lrc, string? tlyric = null)
    {
        var lines = !string.IsNullOrWhiteSpace(yrc) ? ParseYrc(yrc) : new List<LyricLine>();
        if (lines.Count == 0 && !string.IsNullOrWhiteSpace(yrc)) lines = ParseLrc(yrc);
        if (lines.Count == 0 && !string.IsNullOrWhiteSpace(lrc)) lines = ParseLrc(lrc!);
        if (!string.IsNullOrWhiteSpace(tlyric) && lines.Count > 0)
        {
            var trans = ParseLrc(tlyric);
            lines = lines.Select(l => l with { Translation = trans.OrderBy(t => Math.Abs(t.StartMs - l.StartMs)).FirstOrDefault(t => Math.Abs(t.StartMs - l.StartMs) <= 500)?.Text }).ToList();
        }
        return lines.OrderBy(l => l.StartMs).ToList();
    }
    public static List<LyricLine> ParseYrc(string raw)
    {
        var result = new List<LyricLine>();
        foreach (var rawLine in raw.Split('\n'))
        {
            var line = rawLine.Trim(); var head = YrcHead().Match(line); if (!head.Success) continue;
            var words = YrcWord().Matches(line).Select(m => new LyricWord(long.Parse(m.Groups[1].Value), long.Parse(m.Groups[2].Value), m.Groups[3].Value)).ToList();
            if (words.Count == 0) continue; // skips JSON metadata lines {"t":0,...}
            result.Add(new LyricLine(long.Parse(head.Groups[1].Value), long.Parse(head.Groups[2].Value), words));
        }
        return result;
    }
    public static List<LyricLine> ParseLrc(string raw)
    {
        var result = new List<LyricLine>();
        foreach (var rawLine in raw.Split('\n'))
        {
            var tags = LrcTag().Matches(rawLine); if (tags.Count == 0) continue;
            var text = rawLine[(tags[^1].Index + tags[^1].Length)..].Trim(); if (text.Length == 0) continue;
            foreach (Match tag in tags)
            {
                var ms = long.Parse(tag.Groups[1].Value) * 60_000 + long.Parse(tag.Groups[2].Value) * 1_000 + long.Parse((tag.Groups[3].Value + "000")[..3]);
                result.Add(new LyricLine(ms, 0, new[] { new LyricWord(ms, 0, text) }));
            }
        }
        result = result.OrderBy(l => l.StartMs).ToList();
        for (var i = 0; i < result.Count; i++) result[i] = result[i] with { DurationMs = i + 1 < result.Count ? result[i + 1].StartMs - result[i].StartMs : 4_000 };
        return result;
    }
    public static int ActiveLineIndex(IReadOnlyList<LyricLine> lines, long positionMs)
    { var idx = -1; for (var i = 0; i < lines.Count; i++) if (lines[i].StartMs <= positionMs) idx = i; return idx; }
    public static double WordProgress(LyricWord word, long positionMs)
    {
        if (word.DurationMs <= 0) return positionMs >= word.StartMs ? 1 : 0;
        return Math.Clamp((double)(positionMs - word.StartMs) / word.DurationMs, 0, 1);
    }
}
