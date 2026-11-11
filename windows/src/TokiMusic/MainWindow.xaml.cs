using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System.Runtime.InteropServices;
using Windows.Graphics;
namespace TokiMusic;
public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        Title = "TokiMusic";
        // Transparent title bar: content extends into it, caption buttons transparent, drag region kept.
        ExtendsContentIntoTitleBar = true;
        AppWindow.TitleBar.ButtonBackgroundColor = Microsoft.UI.Colors.Transparent;
        AppWindow.TitleBar.ButtonInactiveBackgroundColor = Microsoft.UI.Colors.Transparent;
        AppWindow.Resize(new SizeInt32(1100, 760));
        SetMinSize(640, 480); // minimum window 640x480 DIP (Tokinote convention)
        Nav.SelectedItem = Nav.MenuItems[0];
        ContentFrame.Navigate(typeof(Views.HomePage));
    }
    private void Nav_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        var tag = (args.SelectedItem as NavigationViewItem)?.Tag as string;
        var page = tag switch { "Playlists" => typeof(Views.PlaylistsPage), "Mine" => typeof(Views.MinePage), _ => typeof(Views.HomePage) };
        if (ContentFrame.CurrentSourcePageType != page) ContentFrame.Navigate(page);
    }
    private void MiniToggle_Click(object sender, RoutedEventArgs e) => App.Player.Toggle();
    private void MiniLyrics_Click(object sender, RoutedEventArgs e) => ContentFrame.Navigate(typeof(Views.PlayerPage));
    public void ShowMiniPlayer(string title, string artist) { MiniTitle.Text = title; MiniArtist.Text = artist; MiniPlayerBar.Visibility = Visibility.Visible; }

    // --- Min size via WM_GETMINMAXINFO subclass (same approach as Tokinote WindowSizing) ---
    [DllImport("user32.dll")] private static extern bool SetWindowSubclass(IntPtr hWnd, SubclassProc proc, nuint id, IntPtr data);
    private delegate bool SubclassProc(IntPtr hWnd, uint msg, IntPtr w, IntPtr l, nuint id, IntPtr data);
    [StructLayout(LayoutKind.Sequential)] private struct PointL { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] private struct MinMaxInfo { public PointL Reserved, MaxSize, MaxPos, MinTrack, MaxTrack; }
    [DllImport("user32.dll")] private static extern bool DefSubclassProc(IntPtr hWnd, uint msg, IntPtr w, IntPtr l);
    private SubclassProc? _proc; private int _minW = 640, _minH = 480;
    private void SetMinSize(int wDip, int hDip)
    {
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
        var dpi = (int)Microsoft.UI.Win32Interop.GetDpiForWindow(hwnd);
        _minW = wDip * dpi / 96; _minH = hDip * dpi / 96;
        _proc = (h, msg, w, l, id, data) => { if (msg == 0x0024) { var m = Marshal.PtrToStructure<MinMaxInfo>(l); m.MinTrack = new PointL { X = _minW, Y = _minH }; Marshal.StructureToPtr(m, l, false); } return DefSubclassProc(h, msg, w, l); };
        SetWindowSubclass(hwnd, _proc, 1, IntPtr.Zero);
    }
}
