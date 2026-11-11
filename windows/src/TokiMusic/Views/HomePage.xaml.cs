using Microsoft.UI.Xaml.Controls;
namespace TokiMusic.Views;
public sealed partial class HomePage : Page
{
    public HomePage() { InitializeComponent(); Loaded += async (_, _) => await LoadAsync(); }
    private async Task LoadAsync()
    {
        try { var doc = await App.Api.RawAsync("/personalized?limit=12"); DemoBar.IsOpen = false; _ = doc; }
        catch { DemoBar.IsOpen = true; DailyList.ItemsSource = Core.MockData.Songs; NewSongList.ItemsSource = Core.MockData.Songs; PlaylistGrid.ItemsSource = Core.MockData.Playlists; }
    }
    private void Avatar_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) => Frame.Navigate(typeof(LoginPage));
    private async void SearchBox_QuerySubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args)
    { try { _ = await App.Api.RawAsync($"/cloudsearch?keywords={Uri.EscapeDataString(sender.Text)}&type=1&limit=30"); } catch { DailyList.ItemsSource = Core.MockData.Songs; } }
    private async void SongList_ItemClick(object sender, ItemClickEventArgs e)
    { if (e.ClickedItem is Core.Song s) { await App.Player.PlayQueueAsync(new[] { s }, 0); (App.MainWin as MainWindow)?.ShowMiniPlayer(s.Name, s.ArtistText); } }
    private void PlaylistGrid_ItemClick(object sender, ItemClickEventArgs e)
    { if (e.ClickedItem is Core.Playlist p) Frame.Navigate(typeof(PlaylistDetailPage), p.Id); }
}
