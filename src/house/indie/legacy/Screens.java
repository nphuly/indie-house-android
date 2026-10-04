package house.indie.legacy;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds each page into the activity's content column. Layout and copy follow
 * the listener web app (resources/views/listen) scaled to a 360dp phone.
 */
final class Screens {
    private final MainActivity a;

    Screens(MainActivity activity) { a = activity; }

    void build(Page page) {
        if (page.data == null && page.needsData()) {
            if (page.error != null) errorState(page);
            else skeleton(page);
            return;
        }
        if (Page.HOME.equals(page.kind)) home(page);
        else if (Page.SEARCH.equals(page.kind)) search(page);
        else if (Page.BROWSE.equals(page.kind)) browse(page);
        else if (page.isList()) catalogueList(page);
        else if (Page.LIBRARY.equals(page.kind)) library(page);
        else if (Page.ALBUM.equals(page.kind)) album(page);
        else if (Page.ARTIST.equals(page.kind)) artist(page);
        else if (Page.PLAYLIST.equals(page.kind)) playlist(page);
        else if (Page.HISTORY.equals(page.kind)) history(page);
        else if (Page.SETTINGS.equals(page.kind)) settings(page);
        else if (Page.LEGAL.equals(page.kind)) legal(page);
    }

    /* ------------------------------------------------------------------ Home */

    private void home(Page page) {
        JSONObject catalogueResponse = page.data.optJSONObject("catalogue");
        ArrayList<JSONObject> tracks = list(catalogueResponse == null ? null : catalogueResponse.optJSONArray("data"));
        JSONObject library = page.data.optJSONObject("library");
        ArrayList<JSONObject> playlists = list(library == null ? null : library.optJSONArray("playlists"));
        ArrayList<JSONObject> recent = historyTracks(library);

        LinearLayout head = padded(20, 16);
        head.addView(a.display(greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)), 28));
        a.content.addView(head);

        if (!recent.isEmpty() || !playlists.isEmpty()) {
            LinearLayout grid = padded(16, 20);
            ArrayList<View> tiles = new ArrayList<View>();
            if (!recent.isEmpty()) {
                tiles.add(quickTile("history", a.str(R.string.recently_played), null, R.drawable.ic_history, v -> a.open(new Page(Page.HISTORY, null, a.str(R.string.recently_played)))));
            }
            for (int i = 0; i < playlists.size() && tiles.size() < 6; i++) {
                final JSONObject playlist = playlists.get(i);
                tiles.add(quickTile("playlist:" + playlist.optString("id"), playlist.optString("title"), null, 0,
                        v -> a.open(new Page(Page.PLAYLIST, playlist.optString("id"), playlist.optString("title")))));
            }
            for (int i = 0; i < tiles.size(); i += 2) {
                LinearLayout pair = a.row();
                pair.addView(tiles.get(i), a.margins(new LinearLayout.LayoutParams(0, a.dp(56), 1), 0, 0, 4, 0));
                if (i + 1 < tiles.size()) pair.addView(tiles.get(i + 1), a.margins(new LinearLayout.LayoutParams(0, a.dp(56), 1), 4, 0, 0, 0));
                else pair.addView(new View(a), a.margins(new LinearLayout.LayoutParams(0, a.dp(56), 1), 4, 0, 0, 0));
                grid.addView(pair, a.margins(a.fill(), 0, 0, 0, 8));
            }
            a.content.addView(grid);
        }

        if (tracks.isEmpty()) {
            emptyState(R.drawable.ic_disc, a.str(R.string.empty_catalogue_title), a.str(R.string.empty_catalogue_body), null, null);
            footer();
            return;
        }

        spotlight(tracks);

        JSONArray newest = page.data.optJSONArray("albums");
        List<Media> albums = newest == null ? albumsOf(tracks) : albumMedia(list(newest));
        if (!albums.isEmpty()) {
            heading(a.str(R.string.new_albums), null, a.str(R.string.see_all), v -> a.open(new Page(Page.ALBUMS, null, a.str(R.string.all_albums))));
            shelf(albums.subList(0, Math.min(12, albums.size())));
        }
        if (!recent.isEmpty()) {
            heading(a.str(R.string.recently_played), null, recent.size() > 4 ? a.str(R.string.see_all) : null,
                    v -> a.open(new Page(Page.HISTORY, null, a.str(R.string.recently_played))));
            trackList(recent.subList(0, Math.min(4, recent.size())), recent, "home:recent", a.str(R.string.recently_played), false, true, null);
        }
        heading(a.str(R.string.popular), null, null, null);
        List<JSONObject> chart = tracks.subList(0, Math.min(8, tracks.size()));
        trackList(chart, chart, "home:top", a.str(R.string.popular), true, true, null);
        List<Media> artists = artistsOf(tracks);
        if (!artists.isEmpty()) {
            heading(a.str(R.string.artists), null, null, null);
            shelf(artists);
        }
        footer();
    }

    private String greeting(int hour) {
        return a.str(hour >= 5 && hour < 11 ? R.string.greeting_morning : hour >= 11 && hour < 13 ? R.string.greeting_noon
                : hour >= 13 && hour < 18 ? R.string.greeting_afternoon : R.string.greeting_evening);
    }

    private View quickTile(String seed, String title, String artwork, int icon, View.OnClickListener click) {
        LinearLayout tile = a.row();
        tile.setBackgroundDrawable(a.pressable(Theme.RAISED, Theme.HOVER, 6));
        ArtView art = new ArtView(a, 6);
        if (icon != 0) art.bindIcon(seed, icon);
        else art.bind(seed, title, artwork, a.artwork);
        tile.addView(art, a.size(56, 56));
        TextView label = a.text(shorten(title, 34), 13, Theme.TEXT, Theme.bold);
        label.setMaxLines(2);
        label.setPadding(a.dp(10), 0, a.dp(8), 0);
        tile.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        a.registerLive(label, seed);
        tile.setOnClickListener(click);
        tile.setContentDescription(title);
        return tile;
    }

    /** The "Nghe nhiều nhất" card: the most played song over the hero art. */
    private void spotlight(final ArrayList<JSONObject> tracks) {
        final JSONObject top = tracks.get(0);
        FrameLayout card = new FrameLayout(a);
        GradientDrawable frame = a.shape(Theme.SURFACE, 2);
        card.setBackgroundDrawable(frame);
        android.widget.ImageView stage = new android.widget.ImageView(a);
        stage.setImageDrawable(new ArtView.Rounded(a.stagePhoto(), a.dp(2)));
        card.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(a);
        GradientDrawable fade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Theme.alpha(Theme.BG, .15f), Theme.alpha(Theme.BG, .55f), Theme.alpha(Theme.BG, .94f)});
        fade.setCornerRadius(a.dp(2));
        shade.setBackgroundDrawable(fade);
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout words = a.column();
        words.setPadding(a.dp(18), a.dp(18), a.dp(18), a.dp(18));
        words.setGravity(Gravity.BOTTOM);
        words.addView(tag(a.str(R.string.most_played)), a.wrap());
        TextView title = a.display(shorten(top.optString("title"), 40), 30);
        title.setMaxLines(2);
        words.addView(title, a.margins(a.fill(), 0, 6, 0, 0));
        String genre = top.optString("genre");
        words.addView(a.line(a.artistName(top) + (genre.length() > 0 && !"null".equals(genre) ? " · " + genre : ""), 14, Theme.SOFT, Theme.semibold),
                a.margins(a.fill(), 0, 4, 0, 0));
        LinearLayout actions = a.row();
        IconButton play = a.iconButton(R.drawable.ic_play, IconButton.PRIMARY, a.str(R.string.play_label, top.optString("title")),
                v -> a.playContext(tracks, "home:spotlight", a.str(R.string.popular)));
        play.iconSize(24);
        a.registerContextButton(play, "home:spotlight", top.optString("title"));
        actions.addView(play, a.size(52, 52));
        final JSONObject album = firstAlbum(top);
        if (album != null) {
            actions.addView(a.pill(a.str(R.string.open_album), 0, Widgets.PillButton.SECONDARY,
                    v -> a.open(new Page(Page.ALBUM, album.optString("id"), album.optString("title")))),
                    a.margins(new LinearLayout.LayoutParams(-2, a.dp(40)), 12, 0, 0, 0));
        }
        words.addView(actions, a.margins(a.fill(), 0, 16, 0, 0));
        card.addView(words, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams params = a.margins(new LinearLayout.LayoutParams(-1, a.dp(244)), 16, 16, 16, 0);
        a.content.addView(card, params);
    }

    private TextView tag(String value) {
        TextView label = a.text(value.toUpperCase(Locale.getDefault()), 11, Theme.INK, Theme.mono);
        label.setPadding(a.dp(7), a.dp(4), a.dp(7), a.dp(3));
        label.setBackgroundDrawable(a.shape(Theme.PINK, 0));
        return label;
    }

    /* ---------------------------------------------------------------- Search */

    private void search(final Page page) {
        LinearLayout head = padded(20, 12);
        head.addView(a.display(a.str(R.string.nav_search), 28));
        a.content.addView(head);

        FrameLayout box = new FrameLayout(a);
        final EditText field = a.input(a.str(R.string.search_hint), R.drawable.ic_search, false);
        field.setText(page.query);
        field.setSelection(field.getText().length());
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
        field.setPadding(field.getPaddingLeft(), 0, a.dp(48), 0);
        box.addView(field, new FrameLayout.LayoutParams(-1, a.dp(50)));
        final IconButton clear = a.iconButton(R.drawable.ic_close, IconButton.PLAIN, a.str(R.string.clear_query), null);
        clear.setTint(Theme.MUTED);
        clear.iconSize(18);
        clear.setVisibility(page.query.length() > 0 ? View.VISIBLE : View.GONE);
        box.addView(clear, new FrameLayout.LayoutParams(a.dp(44), a.dp(44), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams boxParams = a.margins(a.fill(), 16, 14, 16, 8);
        a.content.addView(box, boxParams);

        final LinearLayout body = a.column();
        a.content.addView(body, a.fill());
        a.searchBody = body;
        searchBody(page, body);

        final Runnable debounced = () -> a.runSearch(page, false);
        clear.setOnClickListener(v -> {
            field.setText("");
            field.requestFocus();
            ((InputMethodManager) a.getSystemService(MainActivity.INPUT_METHOD_SERVICE)).showSoftInput(field, 0);
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                String next = value.toString();
                if (next.equals(page.query)) return;
                page.query = next;
                clear.setVisibility(next.length() > 0 ? View.VISIBLE : View.GONE);
                a.main.removeCallbacks(debounced);
                if (next.trim().length() == 0) {
                    page.results = null;
                    page.resultsFor = null;
                    page.searching = false;
                    page.searchError = null;
                    searchBody(page, body);
                } else a.main.postDelayed(debounced, 420);
            }
        });
        field.setOnEditorActionListener((view, action, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_SEARCH && !enter) return false;
            a.main.removeCallbacks(debounced);
            if (page.query.trim().length() > 0) {
                a.rememberSearch(page.query.trim());
                a.runSearch(page, true);
            }
            ((InputMethodManager) a.getSystemService(MainActivity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(), 0);
            return true;
        });
    }

    void searchBody(final Page page, LinearLayout body) {
        a.rows.clear();
        body.removeAllViews();
        LinearLayout saved = a.content;
        // Section builders append to `content`; point them at the results area.
        a.content = body;
        try {
            if (page.query.trim().length() == 0) searchStart(page);
            else if (page.searchError != null && page.results == null) {
                emptyState(R.drawable.ic_alert, a.str(R.string.search_failed), page.searchError, a.str(R.string.retry), v -> a.runSearch(page, true));
            } else if (page.results == null) skeletonRows(body, 5);
            else searchResults(page);
        } finally {
            a.content = saved;
        }
        a.refreshNowPlaying();
    }

    private void searchStart(final Page page) {
        List<String> recent = a.recentSearches();
        if (!recent.isEmpty()) {
            heading(a.str(R.string.recent_searches), null, a.str(R.string.clear), v -> { a.clearRecentSearches(); searchBody(page, a.searchBody); });
            LinearLayout chips = a.row();
            for (final String query : recent) {
                chips.addView(chip(query, false, v -> a.setSearchQuery(page, query)), a.margins(new LinearLayout.LayoutParams(-2, a.dp(36)), 0, 0, 8, 0));
            }
            a.content.addView(horizontal(chips));
        }
        List<Map.Entry<String, Integer>> genres = a.knownGenres();
        if (genres.isEmpty()) {
            emptyState(R.drawable.ic_search, a.str(R.string.search_start_title), a.str(R.string.search_start_body), null, null);
            return;
        }
        heading(a.str(R.string.genres), null, null, null);
        LinearLayout grid = padded(16, 0);
        for (int i = 0; i < genres.size(); i += 2) {
            LinearLayout pair = a.row();
            pair.addView(genreTile(page, genres.get(i)), a.margins(new LinearLayout.LayoutParams(0, a.dp(92), 1), 0, 0, 5, 0));
            if (i + 1 < genres.size()) pair.addView(genreTile(page, genres.get(i + 1)), a.margins(new LinearLayout.LayoutParams(0, a.dp(92), 1), 5, 0, 0, 0));
            else pair.addView(new View(a), a.margins(new LinearLayout.LayoutParams(0, a.dp(92), 1), 5, 0, 0, 0));
            grid.addView(pair, a.margins(a.fill(), 0, 0, 0, 10));
        }
        a.content.addView(grid);
    }

    private View genreTile(final Page page, final Map.Entry<String, Integer> genre) {
        int tone = Theme.tone(genre.getKey().toLowerCase(Locale.ROOT));
        FrameLayout tile = new FrameLayout(a);
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {Theme.mix(Theme.toneBase(tone), Theme.toneAccent(tone), .28f), Theme.toneBase(tone)});
        fill.setCornerRadius(a.dp(2));
        GradientDrawable pressed = a.shape(Theme.mix(Theme.toneBase(tone), Theme.toneAccent(tone), .4f), 2);
        tile.setBackgroundDrawable(Widgets.states(fill, pressed));
        LinearLayout words = a.column();
        words.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12));
        TextView name = a.text(shorten(genre.getKey(), 28), 17, Theme.TEXT, Theme.extrabold);
        name.setMaxLines(2);
        words.addView(name);
        words.addView(a.text(a.plural(R.plurals.songs, genre.getValue()), 11, Theme.alpha(Theme.TEXT, .7f), Theme.mono));
        tile.addView(words, new FrameLayout.LayoutParams(-1, -1));
        android.widget.ImageView glyph = a.glyph(R.drawable.ic_signal, Theme.alpha(Theme.toneAccent(tone), .8f));
        FrameLayout.LayoutParams glyphParams = new FrameLayout.LayoutParams(a.dp(30), a.dp(30), Gravity.RIGHT | Gravity.BOTTOM);
        glyphParams.setMargins(0, 0, a.dp(12), a.dp(12));
        tile.addView(glyph, glyphParams);
        tile.setContentDescription(a.str(R.string.pair, genre.getKey(), a.plural(R.plurals.songs, genre.getValue())));
        tile.setOnClickListener(v -> a.setSearchQuery(page, genre.getKey()));
        return tile;
    }

    private void searchResults(final Page page) {
        JSONObject data = page.results;
        final ArrayList<JSONObject> tracks = list(data.optJSONArray("tracks"));
        ArrayList<JSONObject> artists = list(data.optJSONArray("artists"));
        ArrayList<JSONObject> albums = list(data.optJSONArray("albums"));
        final String q = page.resultsFor == null ? page.query.trim() : page.resultsFor;
        if (tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
            emptyState(R.drawable.ic_search, a.str(R.string.no_results_title, q), a.str(R.string.no_results_body),
                    a.str(R.string.nav_browse), v -> a.selectTab(MainActivity.BROWSE));
            return;
        }
        LinearLayout chips = a.row();
        String[][] filters = {{"all", a.str(R.string.filter_all), ""}, {"tracks", a.str(R.string.filter_songs), count(tracks)},
                {"artists", a.str(R.string.filter_artists), count(artists)}, {"albums", a.str(R.string.filter_albums), count(albums)}};
        for (final String[] filter : filters) {
            if (!filter[2].equals("") && filter[2].equals("0")) continue;
            chips.addView(chip(filter[1] + (filter[2].length() > 0 ? "  " + filter[2] : ""), filter[0].equals(page.filter), v -> {
                page.filter = filter[0];
                searchBody(page, a.searchBody);
            }), a.margins(new LinearLayout.LayoutParams(-2, a.dp(36)), 0, 0, 8, 0));
        }
        a.content.addView(horizontal(chips), a.margins(a.fill(), 0, 4, 0, 4));

        String context = "search:" + q;
        String label = a.str(R.string.search_context, q);
        if ("all".equals(page.filter)) {
            topResult(q, tracks, artists, albums);
            if (!tracks.isEmpty()) {
                heading(a.str(R.string.filter_songs), null, tracks.size() > 4 ? a.str(R.string.see_all) : null, v -> { page.filter = "tracks"; searchBody(page, a.searchBody); });
                trackList(tracks.subList(0, Math.min(4, tracks.size())), tracks, context, label, false, true, null);
            }
            if (!artists.isEmpty()) { heading(a.str(R.string.filter_artists), null, null, null); shelf(artistMedia(artists)); }
            if (!albums.isEmpty()) { heading(a.str(R.string.filter_albums), null, null, null); shelf(albumMedia(albums)); }
        } else if ("tracks".equals(page.filter)) {
            gapSection();
            trackList(tracks, tracks, context, label, false, true, null);
        } else {
            gapSection();
            List<Media> items = "artists".equals(page.filter) ? artistMedia(artists) : albumMedia(albums);
            LinearLayout listBox = padded(16, 0);
            for (Media item : items) listBox.addView(mediaRow(item), a.fill());
            a.content.addView(listBox);
        }
    }

    /** Same rule as the web: an artist whose name contains the query wins, then a song, album or artist. */
    private void topResult(String q, ArrayList<JSONObject> tracks, ArrayList<JSONObject> artists, ArrayList<JSONObject> albums) {
        String needle = fold(q);
        JSONObject artist = null;
        for (JSONObject item : artists) if (fold(item.optString("name")).contains(needle)) { artist = item; break; }
        final String kind;
        final boolean isArtist;
        final String title;
        String byline = null;
        String artwork;
        final ArrayList<JSONObject> queue = new ArrayList<JSONObject>();
        final View.OnClickListener open;
        String context;
        if (artist != null || tracks.isEmpty() && albums.isEmpty()) {
            final JSONObject chosen = artist != null ? artist : artists.get(0);
            kind = a.str(R.string.artist);
            isArtist = true;
            title = chosen.optString("name");
            artwork = url(chosen, "artwork_url");
            for (JSONObject track : tracks) {
                JSONObject by = track.optJSONObject("artist");
                if (by != null && chosen.optString("id").equals(by.optString("id"))) queue.add(track);
            }
            open = v -> a.open(new Page(Page.ARTIST, chosen.optString("id"), chosen.optString("name")));
            context = "artist:" + chosen.optString("id");
        } else if (!tracks.isEmpty()) {
            final JSONObject track = tracks.get(0);
            kind = a.str(R.string.song);
            isArtist = false;
            title = track.optString("title");
            byline = a.artistName(track);
            artwork = url(track, "artwork_url");
            queue.addAll(tracks);
            context = "search:" + q;
            open = v -> a.playList(queue, 0, "search:" + q, a.str(R.string.search_context, q), false);
        } else {
            final JSONObject album = albums.get(0);
            kind = a.str(R.string.album);
            isArtist = false;
            title = album.optString("title");
            byline = a.artistName(album);
            artwork = url(album, "artwork_url");
            context = null;
            open = v -> a.open(new Page(Page.ALBUM, album.optString("id"), album.optString("title")));
        }
        FrameLayout card = new FrameLayout(a);
        card.setBackgroundDrawable(a.pressable(Theme.SURFACE, Theme.RAISED, 2));
        LinearLayout words = a.column();
        words.setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(16));
        ArtView art = new ArtView(a, 8);
        art.setSquircle(isArtist);
        art.bind(title, title, artwork, a.artwork);
        words.addView(art, a.size(92, 92));
        TextView name = a.text(shorten(title, 48), 24, Theme.TEXT, Theme.extrabold);
        name.setMaxLines(2);
        words.addView(name, a.margins(a.fill(), 0, 14, 64, 0));
        LinearLayout meta = a.row();
        TextView tag = a.text(kind.toUpperCase(Locale.getDefault()), 10, Theme.TEXT, Theme.mono);
        tag.setPadding(a.dp(8), a.dp(3), a.dp(8), a.dp(3));
        tag.setBackgroundDrawable(a.shape(Theme.BG, 0));
        meta.addView(tag);
        if (byline != null) meta.addView(a.line(byline, 14, Theme.SOFT, Theme.regular), a.margins(new LinearLayout.LayoutParams(0, -2, 1), 8, 0, 64, 0));
        words.addView(meta, a.margins(a.fill(), 0, 6, 0, 0));
        card.addView(words);
        if (!queue.isEmpty() && context != null) {
            final String playContext = context;
            final String label = isArtist ? title : a.str(R.string.search_context, q);
            IconButton play = a.iconButton(R.drawable.ic_play, IconButton.PRIMARY, a.str(R.string.play_label, title),
                    v -> a.playContext(queue, playContext, label));
            play.iconSize(24);
            a.registerContextButton(play, playContext, title);
            FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(a.dp(52), a.dp(52), Gravity.RIGHT | Gravity.BOTTOM);
            playParams.setMargins(0, 0, a.dp(16), a.dp(16));
            card.addView(play, playParams);
        }
        card.setOnClickListener(open);
        card.setContentDescription(a.str(R.string.labelled, kind, title));
        heading(a.str(R.string.top_result), null, null, null);
        a.content.addView(card, a.margins(a.fill(), 16, 0, 16, 0));
    }

    static String fold(String value) {
        String plain = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private static String count(List<?> items) { return String.valueOf(items.size()); }

    /**
     * Cuts long text at a word boundary. Multi-line `ellipsize` on API 18 can
     * drop an ellipsis into the middle of a line, so it is only used on single lines.
     */
    static String shorten(String text, int limit) {
        if (text.length() <= limit) return text;
        int cut = text.lastIndexOf(' ', limit);
        return text.substring(0, cut > limit / 2 ? cut : limit).trim() + "…";
    }

    /* ------------------------------------------------------------- Khám phá */

    /** The newest songs and albums and the most played artists; never the whole catalogue. */
    private void browse(final Page page) {
        JSONObject counts = page.data.optJSONObject("counts");
        ArrayList<JSONObject> tracks = list(page.data.optJSONArray("new_tracks"));
        LinearLayout head = padded(20, 8);
        head.addView(a.display(a.str(R.string.nav_browse), 30));
        String summary = counts == null ? a.plural(R.plurals.songs_long, tracks.size())
                : a.plural(R.plurals.songs_long, counts.optInt("tracks")) + " · " + a.plural(R.plurals.albums, counts.optInt("albums"))
                + " · " + a.plural(R.plurals.artists, counts.optInt("artists"));
        head.addView(a.text(summary, 15, Theme.SOFT, Theme.regular), a.margins(a.fill(), 0, 8, 0, 0));
        a.content.addView(head);
        if (tracks.isEmpty()) {
            emptyState(R.drawable.ic_disc, a.str(R.string.empty_catalogue_title), a.str(R.string.empty_catalogue_body), null, null);
            return;
        }
        heading(a.str(R.string.new_tracks), null, null, null);
        trackList(tracks, tracks, "browse:new", a.str(R.string.new_tracks), false, true, null);
        List<Media> albums = albumMedia(list(page.data.optJSONArray("albums")));
        if (!albums.isEmpty()) {
            heading(a.str(R.string.new_albums), null, a.str(R.string.see_all), v -> a.open(new Page(Page.ALBUMS, null, a.str(R.string.all_albums))));
            shelf(albums);
        }
        List<Media> artists = artistMedia(list(page.data.optJSONArray("artists")));
        if (!artists.isEmpty()) {
            heading(a.str(R.string.artists), null, a.str(R.string.see_all), v -> a.open(new Page(Page.ARTISTS, null, a.str(R.string.all_artists))));
            shelf(artists);
        }
        footer();
    }

    /* ------------------------------------------------ Album and artist lists */

    /** "Xem tất cả": every album in two columns, or every artist as rows, 24 more as the end nears. */
    private void catalogueList(final Page page) {
        boolean albums = Page.ALBUMS.equals(page.kind);
        LinearLayout head = padded(20, 8);
        TextView title = a.display(page.title, 30);
        head.addView(title);
        head.addView(a.text(a.plural(albums ? R.plurals.albums : R.plurals.artists, page.listTotal), 15, Theme.SOFT, Theme.regular),
                a.margins(a.fill(), 0, 8, 0, 0));
        a.content.addView(head);
        a.setTitleAnchor(title);
        if (page.items.isEmpty()) {
            emptyState(R.drawable.ic_disc, a.str(albums ? R.string.no_albums : R.string.no_artists), a.str(R.string.empty_catalogue_body), null, null);
            return;
        }
        LinearLayout box = albums ? padded(16, 20) : padded(8, 12);
        a.content.addView(box);
        a.listBox = box;
        appendListItems(page, 0);
        LinearLayout foot = padded(16, 12);
        a.content.addView(foot);
        a.listFooter = foot;
        listFooter(page);
    }

    void appendListItems(final Page page, int from) {
        if (Page.ARTISTS.equals(page.kind)) {
            for (int i = from; i < page.items.size(); i++) {
                JSONObject artist = page.items.get(i);
                a.listBox.addView(mediaRow(new Media(Page.ARTIST, artist.optString("id"), artist.optString("name"),
                        a.plural(R.plurals.songs, artist.optInt("track_count")), url(artist, "artwork_url"))), a.fill());
            }
            return;
        }
        // Two columns; a half-filled last row is rebuilt when the next page arrives.
        int start = from;
        if (start % 2 == 1) {
            a.listBox.removeViewAt(a.listBox.getChildCount() - 1);
            start--;
        }
        List<Media> albums = albumMedia(page.items);
        for (int i = start; i < albums.size(); i += 2) {
            LinearLayout pair = a.row();
            pair.setGravity(Gravity.TOP);
            pair.addView(gridCard(albums.get(i)), a.margins(new LinearLayout.LayoutParams(0, -2, 1), 0, 0, 6, 0));
            if (i + 1 < albums.size()) pair.addView(gridCard(albums.get(i + 1)), a.margins(new LinearLayout.LayoutParams(0, -2, 1), 6, 0, 0, 0));
            else pair.addView(new View(a), a.margins(new LinearLayout.LayoutParams(0, 1, 1), 6, 0, 0, 0));
            a.listBox.addView(pair, a.margins(a.fill(), 0, 0, 0, 18));
        }
    }

    void listFooter(final Page page) {
        LinearLayout foot = a.listFooter;
        foot.removeAllViews();
        if (page.listPage < page.listLastPage) {
            Widgets.PillButton more = a.pill(a.str(R.string.load_more), 0, Widgets.PillButton.SECONDARY, v -> a.loadMore(page));
            more.setBusy(page.loadingMore, a.str(R.string.loading));
            foot.addView(more, new LinearLayout.LayoutParams(-1, a.dp(46)));
            a.nearEnd = () -> a.loadMore(page);
        } else {
            int plural = Page.ALBUMS.equals(page.kind) ? R.plurals.albums : R.plurals.artists;
            TextView end = a.text(a.str(R.string.end_of_list, a.plural(plural, page.items.size())), 11, Theme.FAINT, Theme.mono);
            end.setGravity(Gravity.CENTER);
            foot.addView(end, a.margins(a.fill(), 0, 8, 0, 8));
            a.nearEnd = null;
        }
    }

    /* -------------------------------------------------------------- Thư viện */

    private void library(Page page) {
        JSONObject data = page.data;
        ArrayList<JSONObject> playlists = list(data.optJSONArray("playlists"));
        ArrayList<JSONObject> recent = historyTracks(data);
        LinearLayout head = padded(20, 8);
        head.addView(a.display(a.str(R.string.nav_library), 30));
        a.content.addView(head);

        heading(a.str(R.string.playlists), null, null, null);
        LinearLayout lists = padded(8, 0);
        LinearLayout create = a.row();
        create.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        create.setBackgroundDrawable(a.touch(8));
        FrameLayout plus = new FrameLayout(a);
        GradientDrawable plusFill = a.outline(Theme.ACCENT_WASH, Theme.alpha(Theme.ACCENT, .45f), 2);
        plus.setBackgroundDrawable(plusFill);
        plus.addView(a.glyph(R.drawable.ic_plus, Theme.ACCENT), new FrameLayout.LayoutParams(a.dp(24), a.dp(24), Gravity.CENTER));
        create.addView(plus, a.size(56, 56));
        LinearLayout createWords = a.column();
        createWords.setPadding(a.dp(14), 0, 0, 0);
        createWords.addView(a.line(a.str(R.string.create_playlist), 15, Theme.TEXT, Theme.bold));
        createWords.addView(a.line(a.str(R.string.create_playlist_hint), 13, Theme.MUTED, Theme.regular));
        create.addView(createWords, new LinearLayout.LayoutParams(0, -2, 1));
        create.setOnClickListener(v -> a.createPlaylist(null));
        lists.addView(create, a.fill());
        for (final JSONObject playlist : playlists) lists.addView(playlistRow(playlist), a.fill());
        a.content.addView(lists);

        heading(a.str(R.string.listening_history), null,
                recent.size() > 10 ? a.str(R.string.see_all) : null, v -> a.open(new Page(Page.HISTORY, null, a.str(R.string.recently_played))));
        if (recent.isEmpty()) {
            emptyState(R.drawable.ic_history, a.str(R.string.history_empty_title), a.str(R.string.history_empty_body),
                    a.str(R.string.nav_browse), v -> a.selectTab(MainActivity.BROWSE));
        } else {
            trackList(recent.subList(0, Math.min(10, recent.size())), recent, "library:history", a.str(R.string.listening_history), true, true, null);
        }
        footer();
    }

    private View playlistRow(final JSONObject playlist) {
        LinearLayout row = a.row();
        row.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        row.setBackgroundDrawable(a.touch(8));
        String title = playlist.optString("title");
        ArtView art = new ArtView(a, 6);
        art.bind("playlist:" + playlist.optString("id"), title, null, null);
        row.addView(art, a.size(56, 56));
        LinearLayout words = a.column();
        words.setPadding(a.dp(14), 0, 0, 0);
        TextView name = a.line(title, 15, Theme.TEXT, Theme.bold);
        a.registerLive(name, "playlist:" + playlist.optString("id"));
        words.addView(name);
        LinearLayout meta = a.row();
        boolean isPublic = playlist.optBoolean("is_public");
        if (!isPublic) meta.addView(a.glyph(R.drawable.ic_lock, Theme.MUTED), a.margins(a.size(13, 13), 0, 0, 5, 0));
        meta.addView(a.line(a.str(R.string.pair, a.plural(R.plurals.songs, playlist.optInt("tracks_count")),
                a.str(isPublic ? R.string.public_label : R.string.private_label)), 13, Theme.MUTED, Theme.regular));
        words.addView(meta, a.margins(a.fill(), 0, 2, 0, 0));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.setContentDescription(a.str(R.string.playlist_description, title));
        row.setOnClickListener(v -> a.open(new Page(Page.PLAYLIST, playlist.optString("id"), playlist.optString("title"))));
        return row;
    }

    private void history(Page page) {
        final ArrayList<JSONObject> recent = historyTracks(page.data);
        final Page self = page;
        LinearLayout head = padded(20, 8);
        TextView title = a.display(a.str(R.string.recently_played), 30);
        head.addView(title);
        a.content.addView(head);
        a.setTitleAnchor(title);
        if (recent.isEmpty()) {
            emptyState(R.drawable.ic_history, a.str(R.string.history_empty_title), a.str(R.string.history_empty_body),
                    a.str(R.string.nav_browse), v -> a.selectTab(MainActivity.BROWSE));
            return;
        }
        actionBar(recent, self.contextId(), a.str(R.string.recently_played), null);
        trackList(recent, recent, self.contextId(), a.str(R.string.recently_played), true, true, null);
    }

    /* ----------------------------------------------------------- Chi tiết */

    private void album(final Page page) {
        final JSONObject data = page.data;
        final ArrayList<JSONObject> tracks = list(data.optJSONArray("tracks"));
        final JSONObject artist = data.optJSONObject("artist");
        String title = data.optString("title", page.title);
        int seconds = 0;
        for (JSONObject track : tracks) seconds += track.optInt("duration_seconds");
        LinearLayout hero = hero(page, a.str(R.string.album), title, url(data, "artwork_url"), false, null);
        LinearLayout meta = a.row();
        if (artist != null) {
            ArtView avatar = new ArtView(a, 4);
            avatar.setSquircle(true);
            avatar.bind(artist.optString("name"), artist.optString("name"), a.derivedArtwork("artists", artist.optString("id")), a.artwork);
            meta.addView(avatar, a.margins(a.size(22, 22), 0, 0, 8, 0));
            TextView name = a.line(artist.optString("name"), 14, Theme.TEXT, Theme.bold);
            name.setOnClickListener(v -> a.open(new Page(Page.ARTIST, artist.optString("id"), artist.optString("name"))));
            name.setPadding(0, a.dp(8), 0, a.dp(8));
            meta.addView(name, new LinearLayout.LayoutParams(-2, -2, 0));
        }
        meta.addView(a.line(" · " + a.str(R.string.pair, a.plural(R.plurals.songs, tracks.size()), a.duration(seconds)), 12, Theme.MUTED, Theme.mono),
                new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(meta, a.margins(a.fill(), 0, 6, 0, 0));
        actionBar(tracks, page.contextId(), title, null);
        if (tracks.isEmpty()) {
            emptyState(R.drawable.ic_disc, a.str(R.string.album_empty), null, null, null);
        } else {
            trackList(tracks, tracks, page.contextId(), title, true, false, null);
        }
        String description = data.optString("description");
        if (description.length() > 0 && !"null".equals(description)) {
            heading(a.str(R.string.about), null, null, null);
            a.content.addView(noteCard(description, page), a.margins(a.fill(), 16, 0, 16, 0));
        }
        footer();
    }

    private void artist(final Page page) {
        final JSONObject data = page.data;
        final ArrayList<JSONObject> tracks = list(data.optJSONArray("tracks"));
        String name = data.optString("name", page.title);
        List<Media> releases = albumsOf(tracks);
        LinearLayout hero = hero(page, a.str(R.string.artist), name, url(data, "avatar_url"), true, null);
        hero.addView(a.line(a.str(R.string.pair, a.plural(R.plurals.songs, tracks.size()), a.plural(R.plurals.albums, releases.size())), 12, Theme.MUTED, Theme.mono),
                a.margins(a.fill(), 0, 8, 0, 0));
        actionBar(tracks, page.contextId(), name, null);
        heading(a.str(R.string.popular_heading), null, null, null);
        int visible = page.expanded ? tracks.size() : Math.min(5, tracks.size());
        trackList(tracks.subList(0, visible), tracks, page.contextId(), name, true, true, null);
        if (tracks.size() > 5) {
            TextView toggle = a.text(page.expanded ? a.str(R.string.show_less) : a.plural(R.plurals.show_more_songs, tracks.size() - 5), 14, Theme.SOFT, Theme.bold);
            toggle.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12));
            toggle.setBackgroundDrawable(a.touch(8));
            toggle.setOnClickListener(v -> { page.expanded = !page.expanded; a.showPage(true); });
            a.content.addView(toggle, a.margins(a.wrap(), 4, 4, 0, 0));
        }
        if (!releases.isEmpty()) {
            heading(a.str(R.string.releases), null, null, null);
            shelf(releases);
        }
        String bio = data.optString("bio");
        if (bio.length() > 0 && !"null".equals(bio)) {
            heading(a.str(R.string.about), null, null, null);
            a.content.addView(noteCard(bio, page), a.margins(a.fill(), 16, 0, 16, 0));
        }
        footer();
    }

    private void playlist(final Page page) {
        final JSONObject data = page.data;
        final ArrayList<JSONObject> tracks = list(data.optJSONArray("tracks"));
        String title = data.optString("title", page.title);
        boolean isPublic = data.optBoolean("is_public");
        int seconds = 0;
        for (JSONObject track : tracks) seconds += track.optInt("duration_seconds");
        LinearLayout hero = hero(page, a.str(isPublic ? R.string.playlist_public : R.string.playlist_private), title, null, false, tracks);
        String description = data.optString("description");
        if (description.length() > 0 && !"null".equals(description)) {
            hero.addView(a.text(shorten(description, 160), 14, Theme.SOFT, Theme.regular), a.margins(a.fill(), 0, 6, 0, 0));
        }
        hero.addView(a.line(seconds > 0 ? a.str(R.string.pair, a.plural(R.plurals.songs, tracks.size()), a.duration(seconds))
                : a.plural(R.plurals.songs, tracks.size()), 12, Theme.MUTED, Theme.mono),
                a.margins(a.fill(), 0, 8, 0, 0));
        ArrayList<IconButton> extra = new ArrayList<IconButton>();
        extra.add(a.iconButton(R.drawable.ic_pencil, IconButton.PLAIN, a.str(R.string.edit_playlist), v -> a.editPlaylist(page)));
        extra.add(a.iconButton(R.drawable.ic_trash, IconButton.PLAIN, a.str(R.string.delete_playlist), v -> a.deletePlaylist(page)));
        actionBar(tracks, page.contextId(), title, extra);
        if (tracks.isEmpty()) {
            emptyState(R.drawable.ic_note, a.str(R.string.playlist_empty_title), a.str(R.string.playlist_empty_body),
                    a.str(R.string.find_songs), v -> a.selectTab(MainActivity.SEARCH));
        } else {
            trackList(tracks, tracks, page.contextId(), title, false, true, page.id);
        }
        footer();
    }

    /* ------------------------------------------------------------ Cài đặt */

    private void settings(final Page page) {
        JSONObject preferences = page.data.optJSONObject("preferences");
        ArrayList<JSONObject> devices = list(page.data.optJSONArray("devices"));
        LinearLayout head = padded(20, 8);
        TextView title = a.display(a.str(R.string.settings), 30);
        head.addView(title);
        a.content.addView(head);
        a.setTitleAnchor(title);

        settingsHeading(a.str(R.string.settings_library));
        LinearLayout card = card();
        LinearLayout identity = a.row();
        FrameLayout mark = new FrameLayout(a);
        mark.setBackgroundDrawable(a.shape(Theme.BG, 2));
        android.widget.ImageView logo = new android.widget.ImageView(a);
        logo.setImageResource(R.drawable.brand_mark);
        mark.addView(logo, new FrameLayout.LayoutParams(a.dp(26), a.dp(26), Gravity.CENTER));
        identity.addView(mark, a.size(46, 46));
        LinearLayout words = a.column();
        words.setPadding(a.dp(12), 0, 0, 0);
        words.addView(a.line(a.api.profile().name, 16, Theme.TEXT, Theme.bold));
        words.addView(a.line(a.api.profile().origin.replace("https://", ""), 12, Theme.MUTED, Theme.mono));
        identity.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(identity);
        LinearLayout verified = a.row();
        verified.addView(a.glyph(R.drawable.ic_shield, Theme.ACCENT), a.margins(a.size(16, 16), 0, 0, 8, 0));
        verified.addView(a.text(a.str(R.string.verified_on_connect), 13, Theme.SOFT, Theme.regular), new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(verified, a.margins(a.fill(), 0, 14, 0, 0));
        card.addView(a.pill(a.str(R.string.change_library), R.drawable.ic_globe, Widgets.PillButton.SECONDARY, v -> a.changeLibrary()),
                a.margins(new LinearLayout.LayoutParams(-1, a.dp(44)), 0, 14, 0, 0));
        a.content.addView(card, a.margins(a.fill(), 16, 0, 16, 0));

        settingsHeading(a.str(R.string.settings_playback));
        final boolean losslessAvailable = preferences != null && preferences.optBoolean("lossless_available", false);
        String saved = preferences == null ? a.api.quality() : preferences.optString("audio_quality", "auto");
        // "auto" has always played 128 kbps; a lossless choice whose grant was revoked plays 320.
        final String quality = "auto".equals(saved) ? "standard"
                : "lossless".equals(saved) && !losslessAvailable ? "high" : saved;
        java.util.List<String[]> qualities = new java.util.ArrayList<String[]>();
        qualities.add(new String[] {"standard", a.str(R.string.quality_standard), a.str(R.string.quality_standard_hint)});
        qualities.add(new String[] {"high", a.str(R.string.quality_high), a.str(R.string.quality_high_hint)});
        if (losslessAvailable) {
            qualities.add(new String[] {"lossless", a.str(R.string.quality_lossless), a.str(R.string.quality_lossless_hint)});
        }
        LinearLayout choices = padded(16, 0);
        for (final String[] option : qualities) {
            choices.addView(choice(option[1], option[2], option[0].equals(quality), v -> {
                // Choosing lossless again gives FLAC another try on this device.
                if ("lossless".equals(option[0])) a.api.setFlacUnsupported(false);
                a.savePreference("audio_quality", option[0]);
            }), a.margins(a.fill(), 0, 0, 0, 8));
        }
        a.content.addView(choices);
        LinearLayout switches = card();
        switches.addView(switchRow(a.str(R.string.autoplay), a.str(R.string.autoplay_hint),
                preferences == null || preferences.optBoolean("autoplay", true), "autoplay"));
        switches.addView(a.divider(), a.margins(new LinearLayout.LayoutParams(-1, 1), 0, 12, 0, 12));
        switches.addView(switchRow(a.str(R.string.record_history), a.str(R.string.record_history_hint),
                preferences == null || preferences.optBoolean("record_listening_history", true), "record_listening_history"));
        a.content.addView(switches, a.margins(a.fill(), 16, 4, 16, 0));

        settingsHeading(a.str(R.string.language));
        LinearLayout languages = padded(16, 0);
        final String current = Locales.current(a);
        String[][] locales = {{Locales.VI, a.str(R.string.language_vi)}, {Locales.EN, a.str(R.string.language_en)}};
        for (final String[] option : locales) {
            languages.addView(choice(option[1], null, option[0].equals(current), v -> a.setLanguage(option[0])),
                    a.margins(a.fill(), 0, 0, 0, 8));
        }
        a.content.addView(languages);
        TextView languageHint = a.text(a.str(R.string.language_hint), 13, Theme.MUTED, Theme.regular);
        a.content.addView(languageHint, a.margins(a.fill(), 20, 0, 20, 0));

        settingsHeading(a.str(R.string.settings_devices));
        TextView hint = a.text(a.str(R.string.devices_hint), 14, Theme.MUTED, Theme.regular);
        a.content.addView(hint, a.margins(a.fill(), 20, 0, 20, 10));
        LinearLayout deviceBox = padded(8, 0);
        if (devices.isEmpty()) {
            TextView none = a.text(a.str(R.string.no_devices), 14, Theme.SOFT, Theme.regular);
            none.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8));
            deviceBox.addView(none);
        }
        for (final JSONObject device : devices) deviceBox.addView(deviceRow(device), a.fill());
        a.content.addView(deviceBox);

        settingsHeading(a.str(R.string.settings_legal));
        LinearLayout documents = padded(16, 0);
        documents.addView(legalRow("terms", R.string.legal_terms, R.string.legal_terms_hint, R.drawable.ic_note), a.margins(a.fill(), 0, 0, 0, 8));
        documents.addView(legalRow("privacy", R.string.legal_privacy, R.string.legal_privacy_hint, R.drawable.ic_shield), a.fill());
        a.content.addView(documents);

        settingsHeading(a.str(R.string.settings_account));
        JSONObject account = page.data.optJSONObject("account");
        if (account != null) {
            LinearLayout profile = card();
            LinearLayout who = a.row();
            ArtView avatar = new ArtView(a, 4);
            avatar.setSquircle(true);
            String name = account.optString("name");
            avatar.bind(name, name, account.isNull("avatar_url") ? null : account.optString("avatar_url"), a.artwork);
            who.addView(avatar, a.size(46, 46));
            LinearLayout names = a.column();
            names.setPadding(a.dp(12), 0, 0, 0);
            names.addView(a.line(name, 16, Theme.TEXT, Theme.bold));
            names.addView(a.line(account.optString("email"), 12, Theme.MUTED, Theme.mono));
            who.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            profile.addView(who);
            profile.addView(a.text(a.str(R.string.account_hint), 13, Theme.MUTED, Theme.regular), a.margins(a.fill(), 0, 12, 0, 0));
            a.content.addView(profile, a.margins(a.fill(), 16, 0, 16, 12));
        }
        a.content.addView(a.pill(a.str(R.string.logout), R.drawable.ic_logout, Widgets.PillButton.DANGER, v -> a.signOut()),
                a.margins(new LinearLayout.LayoutParams(-1, a.dp(46)), 16, 0, 16, 0));
        TextView version = a.text(a.str(R.string.version, a.versionName()), 11, Theme.FAINT, Theme.mono);
        version.setGravity(Gravity.CENTER);
        a.content.addView(version, a.margins(a.fill(), 16, 24, 16, 24));
    }

    private View legalRow(final String document, final int title, int hint, int icon) {
        LinearLayout box = a.row();
        box.setPadding(a.dp(14), a.dp(12), a.dp(10), a.dp(12));
        box.setBackgroundDrawable(Widgets.states(a.outline(Theme.SURFACE, Theme.LINE_SOFT, 2), a.outline(Theme.RAISED, Theme.LINE, 2)));
        box.addView(a.glyph(icon, Theme.SOFT), a.margins(a.size(20, 20), 0, 0, 14, 0));
        LinearLayout words = a.column();
        words.addView(a.line(a.str(title), 15, Theme.TEXT, Theme.bold));
        words.addView(a.text(a.str(hint), 13, Theme.MUTED, Theme.regular));
        box.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        View chevron = a.glyph(R.drawable.ic_chevron_down, Theme.FAINT);
        chevron.setRotation(-90);
        box.addView(chevron, a.margins(a.size(16, 16), 8, 0, 0, 0));
        box.setContentDescription(a.str(title));
        box.setOnClickListener(v -> a.open(new Page(Page.LEGAL, document, a.str(title))));
        return box;
    }

    /* ------------------------------------------------------------ Pháp lý */

    /** The terms of use or privacy policy, the same text as the library's /terms and /privacy. */
    private void legal(Page page) {
        LinearLayout head = padded(20, 8);
        TextView title = a.display(page.data.optString("title", page.title), 30);
        head.addView(title);
        head.addView(a.text(page.data.optString("updated"), 12, Theme.MUTED, Theme.mono), a.margins(a.fill(), 0, 6, 0, 0));
        a.content.addView(head);
        a.setTitleAnchor(title);
        a.content.addView(LegalView.build(a, page.data), a.fill());
        footer();
    }

    private void settingsHeading(String title) {
        LinearLayout row = a.row();
        row.setPadding(a.dp(20), a.dp(28), a.dp(20), a.dp(12));
        row.addView(a.text(title, 19, Theme.TEXT, Theme.extrabold));
        a.content.addView(row, a.fill());
    }

    private LinearLayout card() {
        LinearLayout card = a.column();
        card.setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(16));
        card.setBackgroundDrawable(a.outline(Theme.SURFACE, Theme.LINE_SOFT, 2));
        return card;
    }

    private View choice(String title, String hint, boolean selected, View.OnClickListener click) {
        LinearLayout box = a.row();
        box.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12));
        box.setBackgroundDrawable(Widgets.states(
                a.outline(selected ? Theme.ACCENT_WASH : Theme.SURFACE, selected ? Theme.ACCENT : Theme.LINE_SOFT, 2),
                a.outline(Theme.RAISED, selected ? Theme.ACCENT : Theme.LINE, 2)));
        FrameLayout radio = new FrameLayout(a);
        GradientDrawable ring = Widgets.shape(a, 0, 1);
        ring.setStroke(a.dp(2), selected ? Theme.ACCENT : Theme.FAINT);
        radio.setBackgroundDrawable(ring);
        if (selected) {
            View dot = new View(a);
            dot.setBackgroundDrawable(Widgets.block(a, Theme.ACCENT));
            radio.addView(dot, new FrameLayout.LayoutParams(a.dp(8), a.dp(8), Gravity.CENTER));
        }
        box.addView(radio, a.margins(a.size(18, 18), 0, 0, 14, 0));
        LinearLayout words = a.column();
        words.addView(a.line(title, 15, Theme.TEXT, Theme.bold));
        if (hint != null) words.addView(a.text(hint, 13, Theme.MUTED, Theme.regular));
        box.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        box.setSelected(selected);
        box.setContentDescription(selected ? a.str(R.string.selected_suffix, title) : title);
        box.setOnClickListener(click);
        return box;
    }

    private View switchRow(String title, String hint, boolean checked, final String key) {
        LinearLayout row = a.row();
        LinearLayout words = a.column();
        words.addView(a.text(title, 15, Theme.TEXT, Theme.bold));
        words.addView(a.text(hint, 13, Theme.MUTED, Theme.regular));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        final Widgets.Toggle toggle = new Widgets.Toggle(a);
        toggle.setChecked(checked, false);
        toggle.setContentDescription(title);
        toggle.setListener(value -> a.savePreference(key, value));
        row.addView(toggle, a.margins(a.size(44, 28), 12, 0, 0, 0));
        return row;
    }

    private View deviceRow(final JSONObject device) {
        LinearLayout row = a.row();
        row.setPadding(a.dp(12), a.dp(8), a.dp(4), a.dp(8));
        FrameLayout icon = new FrameLayout(a);
        icon.setBackgroundDrawable(a.shape(Theme.RAISED, 2));
        icon.addView(a.glyph(R.drawable.ic_device, Theme.SOFT), new FrameLayout.LayoutParams(a.dp(20), a.dp(20), Gravity.CENTER));
        row.addView(icon, a.size(42, 42));
        LinearLayout words = a.column();
        words.setPadding(a.dp(12), 0, a.dp(8), 0);
        String name = device.optString("device_name");
        words.addView(a.line(name.length() == 0 || "null".equals(name) ? a.str(R.string.mobile_device) : name, 15, Theme.TEXT, Theme.bold));
        words.addView(a.line(a.str(R.string.used_time, a.relativeTime(device.optString("last_used_at"))), 12, Theme.MUTED, Theme.mono));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(a.pill(a.str(R.string.revoke), 0, Widgets.PillButton.GHOST, v -> a.revokeDevice(device)), new LinearLayout.LayoutParams(-2, a.dp(40)));
        return row;
    }

    /* ------------------------------------------------------- Page pieces */

    private LinearLayout padded(float horizontal, float top) {
        LinearLayout box = a.column();
        box.setPadding(a.dp(horizontal), a.dp(top), a.dp(horizontal), 0);
        return box;
    }

    private void gapSection() { a.gap(a.content, 8); }

    private void heading(String title, String note, String action, View.OnClickListener click) {
        LinearLayout row = a.row();
        row.setPadding(a.dp(16), a.dp(28), a.dp(8), a.dp(10));
        TextView name = a.line(title, 20, Theme.TEXT, Theme.extrabold);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (note != null) {
            TextView detail = a.text(note, 11, Theme.MUTED, Theme.mono);
            detail.setPadding(0, 0, a.dp(8), 0);
            row.addView(detail);
        }
        if (action != null && click != null) {
            TextView link = a.text(action, 13, Theme.SOFT, Theme.bold);
            link.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
            link.setBackgroundDrawable(a.touch(6));
            link.setOnClickListener(click);
            row.addView(link);
        }
        a.content.addView(row, a.fill());
    }

    private TextView chip(String label, boolean active, View.OnClickListener click) {
        TextView chip = a.text(label, 13, active ? Theme.INK : Theme.TEXT, active ? Theme.bold : Theme.semibold);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(a.dp(14), 0, a.dp(14), 0);
        chip.setBackgroundDrawable(active ? a.pressable(Theme.ACCENT, Theme.ACCENT_BRIGHT, 999) : a.pressable(Theme.RAISED, Theme.HOVER, 999));
        chip.setSelected(active);
        chip.setOnClickListener(click);
        return chip;
    }

    private HorizontalScrollView horizontal(LinearLayout strip) {
        HorizontalScrollView scroller = new HorizontalScrollView(a);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        strip.setPadding(a.dp(16), 0, a.dp(8), 0);
        scroller.addView(strip);
        return scroller;
    }

    /** One row of songs; the queue is the whole list the row belongs to, as on the web. */
    private void trackList(List<JSONObject> visible, List<JSONObject> queue, String context, String label,
                           boolean numbered, boolean art, String playlistId) {
        LinearLayout box = padded(8, 0);
        for (int i = 0; i < visible.size(); i++) {
            box.addView(row(visible.get(i), i, queue, context, label, numbered ? i + 1 : 0, art, playlistId), a.fill());
        }
        a.content.addView(box);
    }

    private TrackRow row(final JSONObject track, final int index, final List<JSONObject> queue, final String context,
                         final String label, int number, boolean art, final String playlistId) {
        TrackRow row = new TrackRow(a);
        row.setTrack(track.optString("id"), track.optString("title", a.str(R.string.untitled)), a.artistName(track),
                url(track, "artwork_url"), a.artwork);
        row.setNumber(number);
        row.setArtVisible(art);
        row.setActions(v -> a.playList(new ArrayList<JSONObject>(queue), index, context, label, false),
                v -> a.trackMenu(track, playlistId));
        a.registerRow(row);
        return row;
    }

    /** Hero for albums, artists and playlists: artwork over a tint taken from it. */
    private LinearLayout hero(final Page page, String kind, String title, String artwork, boolean artist, List<JSONObject> mosaic) {
        final LinearLayout hero = a.column();
        hero.setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(4));
        // Playlists are seeded by id everywhere (library rows, quick tiles), so the tone matches.
        String seed = Page.PLAYLIST.equals(page.kind) ? page.contextId() : title;
        final int seedTone = Theme.tone(seed);
        boolean pictured = artwork != null || mosaic != null && !mosaic.isEmpty();
        if (!pictured) page.tint = 0;
        int tint = page.tint != 0 ? page.tint : Theme.ambientForTone(seedTone);
        hero.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {tint, Theme.BG}));
        a.setHeaderTint(page, tint);
        View cover;
        if (mosaic != null && mosaic.size() >= 4) {
            LinearLayout grid = a.column();
            for (int r = 0; r < 2; r++) {
                LinearLayout pair = a.row();
                for (int c = 0; c < 2; c++) {
                    JSONObject track = mosaic.get(r * 2 + c);
                    ArtView tile = new ArtView(a, 4);
                    tile.bind(track.optString("title"), track.optString("title"), url(track, "artwork_url"), a.artwork);
                    if (r == 0 && c == 0) listenForTint(tile, page, hero);
                    pair.addView(tile, a.margins(new LinearLayout.LayoutParams(0, a.dp(89), 1), c == 0 ? 0 : 2, 0, c == 0 ? 2 : 0, 0));
                }
                grid.addView(pair, a.margins(a.fill(), 0, r == 0 ? 0 : 2, 0, r == 0 ? 2 : 0));
            }
            cover = grid;
        } else {
            ArtView art = new ArtView(a, 8);
            art.setSquircle(artist);
            if (mosaic != null && !mosaic.isEmpty()) art.bind(seed, title, url(mosaic.get(0), "artwork_url"), a.artwork);
            else art.bind(seed, title, artwork, a.artwork);
            listenForTint(art, page, hero);
            cover = art;
        }
        int size = artist ? 168 : 184;
        LinearLayout.LayoutParams coverParams = a.size(size, size);
        coverParams.gravity = Gravity.CENTER_HORIZONTAL;
        coverParams.topMargin = a.dp(8);
        coverParams.bottomMargin = a.dp(20);
        hero.addView(cover, coverParams);
        hero.addView(a.eyebrow(kind, Theme.SOFT));
        TextView name = a.display(title, 30);
        hero.addView(name, a.margins(a.fill(), 0, 6, 0, 0));
        a.setTitleAnchor(name);
        a.content.addView(hero, a.fill());
        return hero;
    }

    private void listenForTint(ArtView art, final Page page, final LinearLayout hero) {
        art.setListener(new ArtView.Listener() {
            @Override public void onArtwork(Bitmap bitmap) {
                int tint = Theme.ambient(bitmap);
                if (tint == page.tint) return;
                page.tint = tint;
                hero.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {tint, Theme.BG}));
                a.setHeaderTint(page, tint);
            }
        });
    }

    /** Shuffle, page actions and the yellow play button, as `.action-bar` on the web. */
    private void actionBar(final List<JSONObject> tracks, final String context, final String label, List<IconButton> extra) {
        LinearLayout bar = a.row();
        bar.setPadding(a.dp(12), a.dp(8), a.dp(16), a.dp(8));
        IconButton shuffle = a.iconButton(R.drawable.ic_shuffle, IconButton.PLAIN, a.str(R.string.shuffle),
                v -> a.playList(new ArrayList<JSONObject>(tracks), 0, context, label, true));
        shuffle.setTint(Theme.SOFT);
        shuffle.setEnabled(!tracks.isEmpty());
        bar.addView(shuffle, a.size(44, 44));
        if (extra != null) for (IconButton button : extra) {
            button.setTint(Theme.SOFT);
            bar.addView(button, a.margins(a.size(44, 44), 4, 0, 0, 0));
        }
        bar.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        IconButton play = a.iconButton(R.drawable.ic_play, IconButton.PRIMARY, a.str(R.string.play_label, label),
                v -> a.playContext(tracks, context, label));
        play.iconSize(26);
        play.setEnabled(!tracks.isEmpty());
        a.registerContextButton(play, context, label);
        bar.addView(play, a.size(56, 56));
        a.content.addView(bar, a.fill());
    }

    private View noteCard(String text, final Page page) {
        LinearLayout card = card();
        final TextView body = a.text(page.expanded ? text : shorten(text, 280), 15, Theme.SOFT, Theme.regular);
        body.setLineSpacing(0, 1.2f);
        card.addView(body);
        if (text.length() > 280) {
            TextView more = a.text(a.str(page.expanded ? R.string.show_less : R.string.read_more), 14, Theme.TEXT, Theme.bold);
            more.setPadding(0, a.dp(10), 0, 0);
            more.setOnClickListener(v -> { page.expanded = !page.expanded; a.showPage(true); });
            card.addView(more);
        }
        return card;
    }

    /* ------------------------------------------------------------ Shelves */

    /** An album or artist card on a horizontal shelf, with derived artwork. */
    static final class Media {
        final String kind;
        final String id;
        final String title;
        final String subtitle;
        final String artwork;

        Media(String kind, String id, String title, String subtitle, String artwork) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.artwork = artwork;
        }
    }

    private void shelf(List<Media> items) {
        LinearLayout strip = a.row();
        strip.setGravity(Gravity.TOP);
        for (Media item : items) strip.addView(mediaCard(item), a.margins(new LinearLayout.LayoutParams(a.dp(140), -2), 0, 0, 12, 0));
        a.content.addView(horizontal(strip));
    }

    private View mediaCard(final Media item) {
        LinearLayout card = a.column();
        card.setBackgroundDrawable(a.touch(8));
        boolean artist = Page.ARTIST.equals(item.kind);
        ArtView art = new ArtView(a, 8);
        art.setSquircle(artist);
        art.bind(item.title, item.title, item.artwork, a.artwork);
        card.addView(art, a.size(140, 140));
        card.addView(a.line(item.title, 14, Theme.TEXT, Theme.bold), a.margins(a.fill(), 0, 8, 0, 0));
        card.addView(a.line(item.subtitle, 12, Theme.MUTED, Theme.regular), a.margins(a.fill(), 0, 1, 0, 4));
        card.setContentDescription(a.str(R.string.labelled, item.subtitle, item.title));
        card.setOnClickListener(v -> a.open(new Page(item.kind, item.id, item.title)));
        return card;
    }

    /** An album in a two-column grid: square artwork as wide as the column. */
    private View gridCard(final Media item) {
        LinearLayout card = a.column();
        card.setBackgroundDrawable(a.touch(8));
        Widgets.SquareLayout square = new Widgets.SquareLayout(a);
        ArtView art = new ArtView(a, 8);
        art.bind(item.title, item.title, item.artwork, a.artwork);
        square.addView(art, new FrameLayout.LayoutParams(-1, -1));
        card.addView(square, a.fill());
        card.addView(a.line(item.title, 14, Theme.TEXT, Theme.bold), a.margins(a.fill(), 0, 8, 0, 0));
        card.addView(a.line(item.subtitle, 12, Theme.MUTED, Theme.regular), a.margins(a.fill(), 0, 1, 0, 4));
        card.setContentDescription(a.str(R.string.labelled, item.subtitle, item.title));
        card.setOnClickListener(v -> a.open(new Page(item.kind, item.id, item.title)));
        return card;
    }

    private View mediaRow(final Media item) {
        LinearLayout row = a.row();
        row.setPadding(0, a.dp(6), 0, a.dp(6));
        row.setBackgroundDrawable(a.touch(8));
        ArtView art = new ArtView(a, 6);
        art.setSquircle(Page.ARTIST.equals(item.kind));
        art.bind(item.title, item.title, item.artwork, a.artwork);
        row.addView(art, a.size(56, 56));
        LinearLayout words = a.column();
        words.setPadding(a.dp(14), 0, 0, 0);
        words.addView(a.line(item.title, 15, Theme.TEXT, Theme.bold));
        words.addView(a.line(item.subtitle, 13, Theme.MUTED, Theme.regular));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> a.open(new Page(item.kind, item.id, item.title)));
        return row;
    }

    private List<Media> albumsOf(List<JSONObject> tracks) {
        LinkedHashMap<String, Media> albums = new LinkedHashMap<String, Media>();
        for (JSONObject track : tracks) {
            JSONArray list = track.optJSONArray("albums");
            if (list == null) continue;
            for (int i = 0; i < list.length(); i++) {
                JSONObject album = list.optJSONObject(i);
                if (album == null || albums.containsKey(album.optString("id"))) continue;
                albums.put(album.optString("id"), new Media(Page.ALBUM, album.optString("id"), album.optString("title"),
                        a.artistName(track), a.derivedArtwork("albums", album.optString("id"))));
            }
        }
        return new ArrayList<Media>(albums.values()).subList(0, Math.min(12, albums.size()));
    }

    private List<Media> artistsOf(List<JSONObject> tracks) {
        LinkedHashMap<String, Media> artists = new LinkedHashMap<String, Media>();
        for (JSONObject track : tracks) {
            JSONObject artist = track.optJSONObject("artist");
            if (artist == null || artists.containsKey(artist.optString("id"))) continue;
            artists.put(artist.optString("id"), new Media(Page.ARTIST, artist.optString("id"), artist.optString("name"),
                    a.str(R.string.artist), a.derivedArtwork("artists", artist.optString("id"))));
        }
        return new ArrayList<Media>(artists.values()).subList(0, Math.min(12, artists.size()));
    }

    private List<Media> artistMedia(List<JSONObject> artists) {
        ArrayList<Media> result = new ArrayList<Media>();
        for (JSONObject artist : artists) {
            result.add(new Media(Page.ARTIST, artist.optString("id"), artist.optString("name"), a.str(R.string.artist), url(artist, "artwork_url")));
        }
        return result;
    }

    private List<Media> albumMedia(List<JSONObject> albums) {
        ArrayList<Media> result = new ArrayList<Media>();
        for (JSONObject album : albums) {
            result.add(new Media(Page.ALBUM, album.optString("id"), album.optString("title"), a.artistName(album), url(album, "artwork_url")));
        }
        return result;
    }

    /* -------------------------------------------------------------- States */

    private void emptyState(int icon, String title, String message, String action, View.OnClickListener click) {
        LinearLayout box = a.column();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(a.dp(28), a.dp(32), a.dp(28), a.dp(32));
        FrameLayout badge = new FrameLayout(a);
        badge.setBackgroundDrawable(Widgets.cut(a, Widgets.Cut.PLAY, Theme.RAISED));
        badge.addView(a.glyph(icon, Theme.ACCENT), new FrameLayout.LayoutParams(a.dp(26), a.dp(26), Gravity.CENTER));
        box.addView(badge, a.size(60, 60));
        TextView heading = a.text(title, 18, Theme.TEXT, Theme.extrabold);
        heading.setGravity(Gravity.CENTER);
        box.addView(heading, a.margins(a.wrap(), 0, 16, 0, 0));
        if (message != null) {
            TextView detail = a.text(message, 14, Theme.MUTED, Theme.regular);
            detail.setGravity(Gravity.CENTER);
            detail.setLineSpacing(0, 1.15f);
            box.addView(detail, a.margins(a.wrap(), 0, 6, 0, 0));
        }
        if (action != null) {
            box.addView(a.pill(action, 0, Widgets.PillButton.LIGHT, click), a.margins(new LinearLayout.LayoutParams(-2, a.dp(44)), 0, 18, 0, 0));
        }
        a.content.addView(box, a.fill());
    }

    private void errorState(final Page page) {
        a.gap(a.content, 40);
        emptyState(R.drawable.ic_signal, a.str(R.string.error_load_title), page.error, a.str(R.string.retry), v -> a.load(page));
    }

    private void skeleton(Page page) {
        LinearLayout box = a.column();
        box.setPadding(a.dp(20), a.dp(12), a.dp(20), 0);
        if (page.isRoot() || page.isList() || Page.HISTORY.equals(page.kind) || Page.SETTINGS.equals(page.kind)) {
            box.addView(block(), a.size(120, 12));
            box.addView(block(), a.margins(a.size(240, 30), 0, 12, 0, 0));
        } else {
            LinearLayout.LayoutParams cover = a.size(184, 184);
            cover.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(block(), cover);
            box.addView(block(), a.margins(a.size(80, 12), 0, 20, 0, 0));
            box.addView(block(), a.margins(a.size(220, 30), 0, 10, 0, 0));
        }
        a.content.addView(box, a.fill());
        LinearLayout rows = a.column();
        rows.setPadding(a.dp(8), a.dp(24), a.dp(8), 0);
        skeletonRows(rows, 6);
        a.content.addView(rows, a.fill());
    }

    private void skeletonRows(LinearLayout parent, int count) {
        for (int i = 0; i < count; i++) {
            LinearLayout row = a.row();
            row.setPadding(a.dp(8), a.dp(7), a.dp(8), a.dp(7));
            row.addView(block(), a.size(46, 46));
            LinearLayout words = a.column();
            words.setPadding(a.dp(12), 0, 0, 0);
            words.addView(block(), a.size(150 - i % 3 * 24, 12));
            words.addView(block(), a.margins(a.size(96 - i % 2 * 20, 10), 0, 8, 0, 0));
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            parent.addView(row, a.fill());
        }
    }

    private View block() {
        View view = new View(a);
        view.setBackgroundDrawable(a.shape(Theme.RAISED, 6));
        Widgets.pulse(view);
        return view;
    }

    private void footer() {
        LinearLayout row = a.row();
        row.setPadding(a.dp(20), a.dp(36), a.dp(20), a.dp(28));
        android.widget.ImageView mark = new android.widget.ImageView(a);
        mark.setImageResource(R.drawable.brand_mark);
        row.addView(mark, a.margins(a.size(18, 18), 0, 0, 8, 0));
        row.addView(a.line(a.api.profile().origin.replace("https://", ""), 11, Theme.FAINT, Theme.mono),
                new LinearLayout.LayoutParams(0, -2, 1));
        a.content.addView(row, a.fill());
    }

    /* --------------------------------------------------------------- Data */

    static ArrayList<JSONObject> list(JSONArray items) {
        ArrayList<JSONObject> result = new ArrayList<JSONObject>();
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) result.add(item);
        }
        return result;
    }

    /** Recently played songs, once each, newest first (the API already de-duplicates). */
    static ArrayList<JSONObject> historyTracks(JSONObject library) {
        ArrayList<JSONObject> result = new ArrayList<JSONObject>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        JSONArray history = library == null ? null : library.optJSONArray("history");
        if (history != null) for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            JSONObject track = item == null ? null : item.optJSONObject("track");
            if (track != null && seen.add(track.optString("id"))) result.add(track);
        }
        return result;
    }

    private static JSONObject firstAlbum(JSONObject track) {
        JSONArray albums = track.optJSONArray("albums");
        return albums == null || albums.length() == 0 ? null : albums.optJSONObject(0);
    }

    static String url(JSONObject item, String key) {
        String value = item == null ? null : item.optString(key, null);
        return value == null || "null".equals(value) || value.length() == 0 ? null : value;
    }

    /** Genres of every song this session has seen, most common first. */
    static List<Map.Entry<String, Integer>> genres(List<JSONObject> tracks) {
        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        HashMap<String, String> labels = new HashMap<String, String>();
        HashMap<String, Boolean> seen = new HashMap<String, Boolean>();
        for (JSONObject track : tracks) {
            String genre = track.optString("genre").trim();
            if (genre.length() == 0 || "null".equals(genre) || seen.put(track.optString("id"), Boolean.TRUE) != null) continue;
            String key = genre.toLowerCase(Locale.ROOT);
            Integer count = counts.get(key);
            counts.put(key, count == null ? 1 : count + 1);
            if (!labels.containsKey(key)) labels.put(key, genre);
        }
        ArrayList<Map.Entry<String, Integer>> result = new ArrayList<Map.Entry<String, Integer>>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            result.add(new java.util.AbstractMap.SimpleEntry<String, Integer>(labels.get(entry.getKey()), entry.getValue()));
        }
        Collections.sort(result, new Comparator<Map.Entry<String, Integer>>() {
            @Override public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
                int byCount = right.getValue() - left.getValue();
                return byCount != 0 ? byCount : left.getKey().compareToIgnoreCase(right.getKey());
            }
        });
        return result.subList(0, Math.min(12, result.size()));
    }
}
