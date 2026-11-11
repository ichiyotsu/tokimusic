using Microsoft.UI.Xaml.Controls;
namespace TokiMusic.Views;
public sealed partial class PlaylistsPage : Page
{
    public PlaylistsPage()
    {
        InitializeComponent();
        Loaded += (_, _) =>
        {
            // Real data comes from /user/playlist + PlaylistGrouping.Group; Demo fallback shows the same grouping.
            var groups = Core.PlaylistGrouping.Group(Core.MockData.Playlists, 10001);
            foreach (var (group, list) in groups)
            {
                if (list.Count == 0) continue;
                var expander = new Expander { IsExpanded = true, HorizontalAlignment = Microsoft.UI.Xaml.HorizontalAlignment.Stretch,
                    Header = $"{Core.PlaylistGrouping.Title(group)} · {list.Count}" };
                var lv = new ListView { ItemsSource = list, IsItemClickEnabled = true };
                lv.ItemClick += (_, e) => { if (e.ClickedItem is Core.Playlist p) Frame.Navigate(typeof(PlaylistDetailPage), p.Id); };
                expander.Content = lv; GroupsPanel.Children.Add(expander);
            }
        };
    }
}
