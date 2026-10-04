package house.indie.legacy;

import org.json.JSONObject;
import java.util.ArrayList;

/** One screen in a tab's back stack, with its cached data and scroll position. */
final class Page {
    static final String HOME = "home";
    static final String SEARCH = "search";
    static final String BROWSE = "browse";
    /** Every album, newest first: "Xem tất cả" from the album shelves. */
    static final String ALBUMS = "albums";
    /** Every artist, A to Z. */
    static final String ARTISTS = "artists";
    static final String LIBRARY = "library";
    static final String ALBUM = "album";
    static final String ARTIST = "artist";
    static final String PLAYLIST = "playlist";
    static final String HISTORY = "history";
    static final String SETTINGS = "settings";
    /** The terms of use or privacy policy; the id is "terms" or "privacy". */
    static final String LEGAL = "legal";

    final String kind;
    final String id;
    String title;
    JSONObject data;
    String error;
    boolean loading;
    boolean stale;
    int scroll;
    /** Header tint taken from the artwork once it loads. */
    int tint;
    boolean expanded;

    // Search state survives tab switches.
    String query = "";
    String filter = "all";
    JSONObject results;
    String resultsFor;
    boolean searching;
    String searchError;

    // The album and artist lists keep every loaded page.
    final ArrayList<JSONObject> items = new ArrayList<JSONObject>();
    int listTotal;
    int listPage;
    int listLastPage = 1;
    boolean loadingMore;

    Page(String kind, String id, String title) {
        this.kind = kind;
        this.id = id;
        this.title = title;
    }

    boolean isRoot() {
        return HOME.equals(kind) || SEARCH.equals(kind) || BROWSE.equals(kind) || LIBRARY.equals(kind);
    }

    boolean needsData() { return !SEARCH.equals(kind); }

    /** Loaded a page at a time: `GET albums` or `GET artists`. */
    boolean isList() { return ALBUMS.equals(kind) || ARTISTS.equals(kind); }

    String contextId() { return id == null ? kind : kind + ":" + id; }
}
