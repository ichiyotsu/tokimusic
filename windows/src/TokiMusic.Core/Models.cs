namespace TokiMusic.Core;
public record Artist(long Id, string Name);
public record Song(long Id, string Name, IReadOnlyList<Artist> Artists, string AlbumName = "", string PicUrl = "", long DurationMs = 0)
{ public string ArtistText => string.Join(" / ", Artists.Select(a => a.Name)); }
public record Playlist(long Id, string Name, string CoverUrl = "", int TrackCount = 0, string CreatorName = "", long CreatorId = 0, bool Subscribed = false, long PlayCount = 0);
public record UserProfile(long Uid, string Nickname, string AvatarUrl = "", int Level = 0, int Follows = 0, int Followeds = 0);
public record RecordItem(Song Song, int PlayCount);
public enum PlaylistGroup { Liked, Created, Subscribed }
public static class PlaylistGrouping
{
    public static string Title(PlaylistGroup g) => g switch { PlaylistGroup.Liked => "我喜欢的音乐", PlaylistGroup.Created => "我创建的歌单", _ => "我收藏的歌单" };
    public static IReadOnlyDictionary<PlaylistGroup, List<Playlist>> Group(IReadOnlyList<Playlist> all, long myUid)
    {
        var liked = all.Where(p => p.Name.Contains("喜欢的音乐")).ToList();
        var created = all.Where(p => !liked.Contains(p) && !p.Subscribed && (myUid == 0 || p.CreatorId == myUid)).ToList();
        var sub = all.Where(p => !liked.Contains(p) && p.Subscribed).ToList();
        return new Dictionary<PlaylistGroup, List<Playlist>> { [PlaylistGroup.Liked] = liked, [PlaylistGroup.Created] = created, [PlaylistGroup.Subscribed] = sub };
    }
}
