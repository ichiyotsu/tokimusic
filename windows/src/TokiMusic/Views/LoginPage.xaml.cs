using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media.Imaging;
using Windows.Storage.Streams;
using TokiMusic.Core;
namespace TokiMusic.Views;
/// QR login flow: /login/qr/key -> /login/qr/create (qrimg base64, decoded and shown
/// in QrImage, same as the Android client) -> poll /login/qr/check every 2s.
/// 800 expired / 801 waiting / 802 scanned / 803 success (cookie saved on the client).
public sealed partial class LoginPage : Page
{
    private CancellationTokenSource? _cts;
    public LoginPage() { InitializeComponent(); Loaded += async (_, _) => await PollAsync(); }
    private async Task PollAsync()
    {
        _cts = new CancellationTokenSource();
        try
        {
            var key = await App.Api.QrKeyAsync();
            var qrimg = await App.Api.QrCreateAsync(key);
            await ShowQrImageAsync(qrimg);
            StatusText.Text = "使用网易云音乐 App 扫码";
            while (!_cts.IsCancellationRequested)
            {
                var (code, _) = await App.Api.QrCheckAsync(key);
                StatusText.Text = code switch { 800 => "二维码已过期", 801 => "等待扫码", 802 => "已扫码，请在手机上确认", 803 => "登录成功", _ => StatusText.Text };
                if (code is 800 or 803) break; await Task.Delay(2000, _cts.Token);
            }
        }
        catch (Exception ex) { StatusText.Text = $"无法连接 API 服务：{ex.Message}"; }
    }
    /// Decodes the base64 QR image returned by /login/qr/create into QrImage.
    /// If the payload is an http(s) URL instead of base64, loads it directly.
    private async Task ShowQrImageAsync(string qrimg)
    {
        if (string.IsNullOrWhiteSpace(qrimg)) return;
        var bmp = new BitmapImage();
        if (qrimg.StartsWith("http", StringComparison.OrdinalIgnoreCase)) { bmp.UriSource = new Uri(qrimg); QrImage.Source = bmp; return; }
        var bytes = NeteaseApiClient.DecodeBase64Image(qrimg);
        if (bytes.Length == 0) return;
        using var stream = new InMemoryRandomAccessStream();
        using (var writer = new DataWriter(stream)) { writer.WriteBytes(bytes); await writer.StoreAsync(); writer.DetachStream(); }
        stream.Seek(0);
        await bmp.SetSourceAsync(stream);
        QrImage.Source = bmp;
    }
    private void Back_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) { _cts?.Cancel(); if (Frame.CanGoBack) Frame.GoBack(); }
}
