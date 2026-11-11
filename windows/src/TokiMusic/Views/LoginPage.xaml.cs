using Microsoft.UI.Xaml.Controls;
namespace TokiMusic.Views;
/// QR login flow: /login/qr/key -> /login/qr/create -> poll /login/qr/check every 2s.
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
            var key = await App.Api.QrKeyAsync(); _ = await App.Api.RawAsync($"/login/qr/create?key={Uri.EscapeDataString(key)}&qrimg=true");
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
    private void Back_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) { _cts?.Cancel(); if (Frame.CanGoBack) Frame.GoBack(); }
}
