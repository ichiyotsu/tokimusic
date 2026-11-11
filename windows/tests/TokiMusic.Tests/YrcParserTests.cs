using TokiMusic.Core; using Xunit;
namespace TokiMusic.Tests;
public class YrcParserTests
{
    [Fact] public void ParsesWordTimings()
    {
        var lines = YrcParser.ParseYrc("[1000,2000](1000,500,0)你(1500,500,0)好(2000,1000,0)世界\n[5000,1000](5000,1000,0)再见");
        Assert.Equal(2, lines.Count); Assert.Equal(new[] { "你", "好", "世界" }, lines[0].Words.Select(w => w.Text)); Assert.Equal("你好世界", lines[0].Text);
    }
    [Fact] public void WordProgressClamps() { var w = new LyricWord(1000, 500, "你"); Assert.Equal(0.5, YrcParser.WordProgress(w, 1250), 3); }
    [Fact] public void LrcFallbackWithTranslation()
    {
        var lines = YrcParser.Parse(null, "[00:01.00]hello\n[00:03.50]world", "[00:01.00]你好");
        Assert.Equal(2500, lines[0].DurationMs); Assert.Equal("你好", lines[0].Translation);
    }
}
