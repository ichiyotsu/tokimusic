using Microsoft.UI.Xaml;
namespace TokiMusic;
public partial class App : Application
{
    public static MainWindow? MainWin { get; private set; }
    public static Services.PlayerService Player { get; } = new();
    public static Core.NeteaseApiClient Api { get; } = new();
    public App() => InitializeComponent();
    protected override void OnLaunched(LaunchActivatedEventArgs args) { MainWin = new MainWindow(); MainWin.Activate(); }
}
