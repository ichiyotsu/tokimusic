using Windows.Media.Core; using Windows.Media.Playback;
namespace TokiMusic.Services;
/// MediaPlayer wrapper: queue, play/pause, next/previous, position for the lyrics ticker.
public sealed class PlayerService
{
    public MediaPlayer Player { get; } = new();
    public IReadOnlyList<Core.Song> Queue { get; private set; } = Array.Empty<Core.Song>();
    public int Index { get; private set; } = -1;
    public Core.Song? Current => Index >= 0 && Index < Queue.Count ? Queue[Index] : null;
    public long PositionMs => (long)Player.PlaybackSession.Position.TotalMilliseconds;
    public bool IsPlaying => Player.PlaybackSession.PlaybackState == MediaPlaybackState.Playing;
    public event Action? StateChanged;
    public PlayerService() => Player.PlaybackSession.PlaybackStateChanged += (_, _) => StateChanged?.Invoke();
    public async Task PlayQueueAsync(IReadOnlyList<Core.Song> songs, int index)
    {
        Queue = songs; Index = index;
        var url = await App.Api.SongUrlAsync(songs[index].Id);
        if (url != null) { Player.Source = MediaSource.CreateFromUri(new Uri(url)); Player.Play(); }
        StateChanged?.Invoke();
    }
    public void Toggle() { if (IsPlaying) Player.Pause(); else Player.Play(); }
    public async Task NextAsync() { if (Index + 1 < Queue.Count) await PlayQueueAsync(Queue, Index + 1); }
    public async Task PreviousAsync() { if (Index - 1 >= 0) await PlayQueueAsync(Queue, Index - 1); }
    public void Seek(long ms) => Player.PlaybackSession.Position = TimeSpan.FromMilliseconds(ms);
}
