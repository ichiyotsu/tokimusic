using Microsoft.UI.Xaml.Controls;
namespace TokiMusic.Views;
public sealed partial class MinePage : Page
{
    public MinePage()
    {
        InitializeComponent();
        // About: version from assembly, never hardcoded.
        AboutText.Text = $"关于 TokiMusic · 版本 {Core.AppVersion.Display}（{Core.AppVersion.Informational}）· 检查更新：正式发布后读取 GitHub Releases（ichiyotsu/tokimusic）比对版本号";
        ApiBox.Text = App.Api.BaseUrl;
    }
    private void Login_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) => Frame.Navigate(typeof(LoginPage));
    private void Entry_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) { /* fav/recent/record lists open here; data via /likelist, /history/recent/songs, /user/record */ }
    private void SaveApi_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) => App.Api.BaseUrl = ApiBox.Text.Trim().TrimEnd('/');
}
