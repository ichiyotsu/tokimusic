using Microsoft.UI; using Microsoft.UI.Xaml.Controls; using Microsoft.UI.Xaml.Documents; using Microsoft.UI.Xaml.Media;
namespace TokiMusic.Views;
/// <summary>
/// Player + lyrics. Lyrics are LEFT-aligned (HorizontalAlignment=Left on every line):
/// sung words are primary-coloured Runs, unsung words use the variant colour, the active
/// line is bold/larger, translations sit under the line — also left-aligned.
/// </summary>
public sealed partial class PlayerPage : Page
{
    private IReadOnlyList<Core.LyricLine> _lines = Array.Empty<Core.LyricLine>();
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromMilliseconds(250) };
    private static readonly SolidColorBrush Primary = new(Colors.OliveDrab); // replaced by TokiPrimaryBrush from palette at runtime in Loaded
    public PlayerPage()
    {
        InitializeComponent();
        Loaded += async (_, _) =>
        {
            var song = App.Player.Current; if (song == null) { TitleText.Text = "暂无播放中的歌曲"; return; }
            TitleText.Text = song.Name; ArtistText.Text = song.ArtistText;
            try { var (yrc, lrc, t) = await App.Api.LyricAsync(song.Id); _lines = Core.YrcParser.Parse(yrc, lrc, t); }
            catch { _lines = Core.YrcParser.Parse(Core.MockData.DemoYrc, null); }
            RenderLyrics(0); _timer.Tick += (_, _) => RenderLyrics(App.Player.PositionMs); _timer.Start();
        };
        Unloaded += (_, _) => _timer.Stop();
    }
    private void RenderLyrics(long positionMs)
    {
        var active = Core.YrcParser.ActiveLineIndex(_lines, positionMs);
        LyricsPanel.Children.Clear();
        var primary = (SolidColorBrush)Application.Current.Resources["TokiPrimaryBrush"];
        for (var i = 0; i < _lines.Count; i++)
        {
            var line = _lines[i]; var tb = new TextBlock { HorizontalAlignment = Microsoft.UI.Xaml.HorizontalAlignment.Left, FontSize = i == active ? 22 : 17, FontWeight = i == active ? Microsoft.UI.Text.FontWeights.Bold : Microsoft.UI.Text.FontWeights.Normal, TextWrapping = Microsoft.UI.Xaml.TextWrapping.Wrap };
            foreach (var word in line.Words)
            {
                var progress = i == active ? Core.YrcParser.WordProgress(word, positionMs) : (i < active ? 1 : 0);
                var sung = (int)(word.Text.Length * progress);
                if (sung > 0) tb.Inlines.Add(new Run { Text = word.Text[..sung], Foreground = primary });
                if (sung < word.Text.Length) tb.Inlines.Add(new Run { Text = word.Text[sung..] });
            }
            LyricsPanel.Children.Add(tb);
            if (!string.IsNullOrEmpty(line.Translation)) LyricsPanel.Children.Add(new TextBlock { Text = line.Translation, Opacity = 0.6, FontSize = 12, HorizontalAlignment = Microsoft.UI.Xaml.HorizontalAlignment.Left });
            if (i == active) tb.StartBringIntoView(new Microsoft.UI.Xaml.BringIntoViewOptions { VerticalAlignmentRatio = 0.5 }); // scroll only; text stays left
        }
        if (App.Player.Current is { } s && s.DurationMs > 0) ProgressSlider.Value = positionMs * 100.0 / s.DurationMs;
    }
    private void Toggle_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) => App.Player.Toggle();
    private async void Next_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) => await App.Player.NextAsync();
    private async void Prev_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) => await App.Player.PreviousAsync();
}
