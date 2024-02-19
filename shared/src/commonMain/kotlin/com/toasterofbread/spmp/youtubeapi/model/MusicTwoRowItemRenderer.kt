package com.toasterofbread.spmp.youtubeapi.model

import com.toasterofbread.spmp.model.mediaitem.MediaItemData
import com.toasterofbread.spmp.model.mediaitem.artist.Artist
import com.toasterofbread.spmp.model.mediaitem.artist.ArtistData
import com.toasterofbread.spmp.model.mediaitem.enums.MediaItemType
import com.toasterofbread.spmp.model.mediaitem.enums.PlaylistType
import com.toasterofbread.spmp.model.mediaitem.enums.SongType
import com.toasterofbread.spmp.model.mediaitem.playlist.RemotePlaylist
import com.toasterofbread.spmp.model.mediaitem.playlist.RemotePlaylistData
import com.toasterofbread.spmp.model.mediaitem.song.SongData
import com.toasterofbread.spmp.model.settings.Settings
import com.toasterofbread.spmp.model.settings.category.FeedSettings
import com.toasterofbread.spmp.youtubeapi.radio.YoutubeiNextResponse

class MusicTwoRowItemRenderer(
    val navigationEndpoint: NavigationEndpoint,
    val title: TextRuns,
    val subtitle: TextRuns?,
    val thumbnailRenderer: ThumbnailRenderer,
    val menu: YoutubeiNextResponse.Menu?,
    val subtitleBadges: List<MusicResponsiveListItemRenderer.Badge>?
) {
    private fun getArtist(host_item: MediaItemData): Artist? {
        for (run in subtitle?.runs ?: emptyList()) {
            val browse_endpoint: BrowseEndpoint? = run.navigationEndpoint?.browseEndpoint
            if (browse_endpoint?.browseId == null) {
                continue
            }

            if (browse_endpoint.getMediaItemType() == MediaItemType.ARTIST) {
                return ArtistData(browse_endpoint.browseId).apply {
                    title = run.text
                }
            }
        }

        if (host_item is SongData) {
            val index = if (host_item.song_type == SongType.VIDEO) 0 else 1
            subtitle?.runs?.getOrNull(index)?.also {
                return ArtistData(Artist.getForItemId(host_item)).apply {
                    title = it.text
                }
            }
        }

        return null
    }
    
    fun toMediaItem(hl: String): MediaItemData? {
        // Video
        if (navigationEndpoint.watchEndpoint?.videoId != null) {
            val first_thumbnail = thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails.first()
            return SongData(navigationEndpoint.watchEndpoint.videoId).also { data ->
                data.song_type = if (first_thumbnail.height == first_thumbnail.width) SongType.SONG else SongType.VIDEO
                data.title = this@MusicTwoRowItemRenderer.title.first_text
                data.thumbnail_provider = thumbnailRenderer.toThumbnailProvider()
                data.artist = getArtist(data)
                data.explicit = subtitleBadges?.any { it.isExplicit() } == true

                for (item in menu?.menuRenderer?.items ?: emptyList()) {
                    val browse_endpoint: BrowseEndpoint = item.menuNavigationItemRenderer?.navigationEndpoint?.browseEndpoint ?: continue
                    if (browse_endpoint.browseId != null && browse_endpoint.getMediaItemType() == MediaItemType.PLAYLIST_REM) {
                        data.album = RemotePlaylistData(browse_endpoint.browseId)
                        break
                    }
                }
            }
        }

        val item: MediaItemData

        if (navigationEndpoint.watchPlaylistEndpoint != null) {
            if (!Settings.get<Boolean>(FeedSettings.Key.SHOW_RADIOS)) {
                return null
            }

            item = RemotePlaylistData(navigationEndpoint.watchPlaylistEndpoint.playlistId).also { data ->
                data.playlist_type = PlaylistType.RADIO
                data.title = title.first_text
                data.thumbnail_provider = thumbnailRenderer.toThumbnailProvider()
            }
        }
        else {
            // Playlist or artist
            val browse_id: String = navigationEndpoint.browseEndpoint?.browseId ?: return null
            val page_type: String = navigationEndpoint.browseEndpoint.getPageType() ?: return null

            item = when (MediaItemType.fromBrowseEndpointType(page_type)) {
                MediaItemType.SONG -> SongData(browse_id)
                MediaItemType.ARTIST -> ArtistData(browse_id)
                MediaItemType.PLAYLIST_REM -> {
                    if (RemotePlaylist.formatYoutubeId(browse_id).startsWith("RDAT") && !Settings.get<Boolean>(FeedSettings.Key.SHOW_RADIOS)) {
                        return null
                    }

                    RemotePlaylistData(browse_id).also { data ->
                        data.playlist_type = PlaylistType.fromBrowseEndpointType(page_type)
                        data.artist = getArtist(data)
//                        is_editable = menu?.menuRenderer?.items
//                            ?.any { it.menuNavigationItemRenderer?.icon?.iconType == "DELETE" } == true
                    }
                }
                MediaItemType.PLAYLIST_LOC -> throw IllegalStateException("$page_type ($browse_id)")
            }

            item.title = title.first_text
            item.thumbnail_provider = thumbnailRenderer.toThumbnailProvider()
        }

        return item
    }
}
