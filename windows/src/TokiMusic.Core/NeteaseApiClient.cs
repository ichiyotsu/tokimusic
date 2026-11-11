using System.Net.Http.Headers;
using System.Text.Json;
namespace TokiMusic.Core;
/// <summary>Isomorphic NeteaseCloudMusicApi client. Base URL configurable, default http://127.0.0.1:3000; cookie persisted by caller.</summary>
public sealed class NeteaseApiClient
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(15) };
    public string BaseUrl { get; set; } = "http://127.0.0.1:3000";
    public string Cookie { get; set; } = "";
    private async Task<JsonDocument> GetAsync(string path, CancellationToken ct = default)
    {
        var sep = path.Contains('?') ? "&" : "?";
        var url = BaseUrl.TrimEnd('/') + path + (string.IsNullOrWhiteSpace(Cookie) ? "" : sep + "cookie=" + Uri.EscapeDataString(Cookie));
        using var req = new HttpRequestMessage(HttpMethod.Get, url);
        if (!string.IsNullOrWhiteSpace(Cookie)) req.Headers.TryAddWithoutValidation("Cookie", Cookie);
        using var res = await _http.SendAsync(req, ct); res.EnsureSuccessStatusCode();
        return JsonDocument.Parse(await res.Content.ReadAsStringAsync(ct));
    }
    // Login: /login/qr/key · /login/qr/create · /login/qr/check (800/801/802/803) · /login/status · /user/account
    public async Task<string> QrKeyAsync() { using var d = await GetAsync("/login/qr/key"); return d.RootElement.GetProperty("data").GetProperty("unikey").GetString() ?? ""; }
    public async Task<(int Code, string Cookie)> QrCheckAsync(string key) { using var d = await GetAsync($"/login/qr/check?key={Uri.EscapeDataString(key)}"); var code = d.RootElement.GetProperty("code").GetInt32(); var cookie = d.RootElement.TryGetProperty("cookie", out var c) ? c.GetString() ?? "" : ""; if (code == 803) Cookie = cookie; return (code, cookie); }
    // Home: /banner /personalized /personalized/newsong /recommend/songs /cloudsearch
    // Playlists: /user/playlist /playlist/detail /playlist/catlist
    // Mine: /user/detail /user/subcount /likelist /user/record?type=0|1 /history/recent/songs
    // Playback: /song/url/v1?level=standard /song/detail ; Lyrics: /lyric/new (YRC) then /lyric fallback
    public async Task<JsonDocument> RawAsync(string path) => await GetAsync(path);
    public async Task<string?> SongUrlAsync(long id) { using var d = await GetAsync($"/song/url/v1?id={id}&level=standard"); var data = d.RootElement.GetProperty("data"); return data.GetArrayLength() > 0 ? data[0].GetProperty("url").GetString() : null; }
    public async Task<(string? Yrc, string? Lrc, string? TLyric)> LyricAsync(long id)
    {
        try { using var d = await GetAsync($"/lyric/new?id={id}"); return (Str(d, "yrc"), Str(d, "lrc"), Str(d, "tlyric")); }
        catch { using var d = await GetAsync($"/lyric?id={id}"); return (null, Str(d, "lrc"), Str(d, "tlyric")); }
        static string? Str(JsonDocument d, string k) => d.RootElement.TryGetProperty(k, out var v) ? v.GetProperty("lyric").GetString() : null;
    }
}
public static class MockData
{
    public static readonly IReadOnlyList<Song> Songs = new[] { new Song(1, "Demo 歌曲 · 晴天练习曲", new[] { new Artist(1, "Toki Demo Band") }, "Demo 专辑", "", 214_000), new Song(2, "Demo 歌曲 · 抹茶与海", new[] { new Artist(2, "Demo Singer") }, "Demo 专辑", "", 187_000) };
    public static readonly IReadOnlyList<Playlist> Playlists = new[] { new Playlist(11, "Demo 用户喜欢的音乐", "", 2, "Demo 用户", 10001), new Playlist(13, "Demo · 收藏的华语精选", "", 50, "其他用户", 20002, true) };
    public const string DemoYrc = "[0,3000](0,600,0)这(600,600,0)是(1200,900,0)逐(2100,900,0)字\n[3000,3000](3000,800,0)歌(3800,800,0)词(4600,1400,0)演示";
}
