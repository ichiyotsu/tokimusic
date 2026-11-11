using Microsoft.UI.Xaml.Controls; using Microsoft.UI.Xaml.Navigation;
namespace TokiMusic.Views;
public sealed partial class PlaylistDetailPage : Page
{
    protected override void OnNavigatedTo(NavigationEventArgs e)
    { base.OnNavigatedTo(e); TitleText.Text = $"歌单 {e.Parameter}"; TrackList.ItemsSource = Core.MockData.Songs; /* replaced by /playlist/detail tracks */ }
    private void Back_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) { if (Frame.CanGoBack) Frame.GoBack(); }
    private async void TrackList_ItemClick(object sender, ItemClickEventArgs e)
    { if (e.ClickedItem is Core.Song s) await App.Player.PlayQueueAsync(new[] { s }, 0); }
}
