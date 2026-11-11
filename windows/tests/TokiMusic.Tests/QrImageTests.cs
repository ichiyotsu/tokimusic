using TokiMusic.Core;
using Xunit;
namespace TokiMusic.Tests;
public class QrImageTests
{
    [Fact]
    public void DecodeStripsDataUriPrefix()
    {
        var payload = Convert.ToBase64String(new byte[] { 1, 2, 3, 250 });
        Assert.Equal(new byte[] { 1, 2, 3, 250 }, NeteaseApiClient.DecodeBase64Image("data:image/png;base64," + payload));
        Assert.Equal(new byte[] { 1, 2, 3, 250 }, NeteaseApiClient.DecodeBase64Image(payload));
        Assert.Empty(NeteaseApiClient.DecodeBase64Image(""));
    }
}
