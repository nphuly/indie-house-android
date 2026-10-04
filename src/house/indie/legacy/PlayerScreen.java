package house.indie.legacy;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.List;

/**
 * Full-screen Now Playing, like the expanded mobile player on the web:
 * artwork over a blurred wash of itself, seek bar, shuffle/repeat and queue.
 */
final class PlayerScreen {
    private final MainActivity a;
    private final Dialog dialog;
    private final ImageView backdrop;
    private final View wash;
    private final ArtView art;
    private final TextView contextLabel;
    private final TextView title;
    private final TextView artist;
    private final TextView error;
    private final TextView elapsed;
    private final TextView remaining;
    private final TextView position;
    private final SeekBar seek;
    private final IconButton play;
    private final IconButton shuffle;
    private final IconButton repeat;
    private final IconButton previous;
    private final IconButton next;
    private String boundId;
    private boolean dragging;

    PlayerScreen(final MainActivity activity) {
        a = activity;
        dialog = new Dialog(activity, R.style.IndiePlayer);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Theme.BG);
        backdrop = new ImageView(activity);
        backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        backdrop.setAlpha(.55f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        wash = new View(activity);
        root.addView(wash, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = activity.column();
        page.setPadding(activity.dp(12), activity.dp(6), activity.dp(12), activity.dp(14));

        LinearLayout head = activity.row();
        head.addView(activity.iconButton(R.drawable.ic_chevron_down, IconButton.PLAIN, activity.str(R.string.minimize_player), v -> dialog.dismiss()),
                activity.size(48, 48));
        LinearLayout from = activity.column();
        from.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView kicker = activity.eyebrow(activity.str(R.string.playing_from), Theme.MUTED);
        kicker.setTextSize(10);
        kicker.setGravity(Gravity.CENTER);
        from.addView(kicker, activity.fill());
        contextLabel = activity.line("", 13, Theme.TEXT, Theme.bold);
        contextLabel.setGravity(Gravity.CENTER);
        from.addView(contextLabel, activity.fill());
        head.addView(from, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(activity.iconButton(R.drawable.ic_more, IconButton.PLAIN, activity.str(R.string.track_options), v -> {
            JSONObject track = a.player.current();
            if (track != null) a.trackMenu(track, null);
        }), activity.size(48, 48));
        page.addView(head, activity.fill());

        FrameLayout stage = new FrameLayout(activity);
        Widgets.SquareLayout square = new Widgets.SquareLayout(activity);
        art = new ArtView(activity, 10);
        art.setListener(this::onArtwork);
        square.addView(art, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(square, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        stage.setPadding(activity.dp(12), activity.dp(16), activity.dp(12), activity.dp(16));
        page.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout info = activity.row();
        info.setPadding(activity.dp(12), 0, 0, 0);
        LinearLayout words = activity.column();
        title = activity.line("", 22, Theme.TEXT, Theme.extrabold);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        words.addView(title, activity.fill());
        artist = activity.line("", 15, Theme.SOFT, Theme.regular);
        artist.setPadding(0, activity.dp(2), 0, activity.dp(4));
        artist.setOnClickListener(v -> {
            JSONObject track = a.player.current();
            JSONObject by = track == null ? null : track.optJSONObject("artist");
            if (by != null) a.open(new Page(Page.ARTIST, by.optString("id"), by.optString("name")));
        });
        words.addView(artist, activity.fill());
        info.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        info.addView(activity.iconButton(R.drawable.ic_plus, IconButton.PLAIN, activity.str(R.string.add_to_playlist), v -> {
            JSONObject track = a.player.current();
            if (track != null) a.addToPlaylist(track);
        }), activity.size(48, 48));
        page.addView(info, activity.fill());

        seek = new SeekBar(activity);
        activity.styleSeekBar(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) showTimes(value, a.player.duration());
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                a.player.seekTo(bar.getProgress());
            }
        });
        page.addView(seek, activity.margins(activity.fill(), 5, 14, 5, 0));
        LinearLayout times = activity.row();
        times.setPadding(activity.dp(12), 0, activity.dp(12), 0);
        elapsed = activity.text("0:00", 11, Theme.MUTED, Theme.mono);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
        remaining = activity.text("0:00", 11, Theme.MUTED, Theme.mono);
        times.addView(remaining);
        page.addView(times, activity.fill());

        LinearLayout controls = activity.row();
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, activity.dp(10), 0, activity.dp(6));
        shuffle = activity.iconButton(R.drawable.ic_shuffle, IconButton.PLAIN, activity.str(R.string.shuffle), v -> a.player.setShuffle(!a.player.shuffle()));
        previous = activity.iconButton(R.drawable.ic_previous, IconButton.PLAIN, activity.str(R.string.previous), v -> a.player.previous());
        previous.iconSize(28);
        play = activity.iconButton(R.drawable.ic_play, IconButton.PRIMARY, activity.str(R.string.play), v -> a.player.toggle());
        play.iconSize(30);
        next = activity.iconButton(R.drawable.ic_next, IconButton.PLAIN, activity.str(R.string.next), v -> a.player.next());
        next.iconSize(28);
        repeat = activity.iconButton(R.drawable.ic_repeat, IconButton.PLAIN, activity.str(R.string.repeat), v -> a.player.cycleRepeat());
        controls.addView(shuffle, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        controls.addView(previous, new LinearLayout.LayoutParams(0, activity.dp(56), 1));
        LinearLayout.LayoutParams playParams = activity.size(68, 68);
        playParams.setMargins(activity.dp(6), 0, activity.dp(6), 0);
        controls.addView(play, playParams);
        controls.addView(next, new LinearLayout.LayoutParams(0, activity.dp(56), 1));
        controls.addView(repeat, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        page.addView(controls, activity.fill());

        error = activity.text("", 13, Theme.TEXT, Theme.regular);
        error.setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));
        error.setBackgroundDrawable(activity.outline(Theme.DANGER_WASH, Theme.alpha(Theme.DANGER, .35f), 10));
        android.graphics.drawable.Drawable alert = Widgets.tinted(activity, R.drawable.ic_alert, Theme.DANGER);
        alert.setBounds(0, 0, activity.dp(18), activity.dp(18));
        error.setCompoundDrawables(alert, null, null, null);
        error.setCompoundDrawablePadding(activity.dp(10));
        error.setVisibility(View.GONE);
        page.addView(error, activity.margins(activity.fill(), 8, 4, 8, 0));

        LinearLayout foot = activity.row();
        foot.setPadding(activity.dp(12), 0, 0, 0);
        position = activity.text("", 11, Theme.FAINT, Theme.mono);
        foot.addView(position, new LinearLayout.LayoutParams(0, -2, 1));
        foot.addView(activity.iconButton(R.drawable.ic_queue, IconButton.PLAIN, activity.str(R.string.queue), v -> showQueue()), activity.size(48, 48));
        page.addView(foot, activity.fill());

        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> a.onPlayerClosed(this));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        update();
    }

    boolean isShowing() { return dialog.isShowing(); }
    void dismiss() { if (dialog.isShowing()) dialog.dismiss(); }

    void update() {
        Player player = a.player;
        JSONObject track = player.current();
        if (track == null) { dismiss(); return; }
        String id = track.optString("id");
        if (!id.equals(boundId)) {
            boundId = id;
            String name = track.optString("title", a.str(R.string.untitled));
            title.setText(name);
            artist.setText(a.artistName(track));
            art.bind(name, name, Screens.url(track, "artwork_url"), a.artwork);
            setWash(Theme.ambientForTone(art.tone()));
            backdrop.setImageDrawable(null);
        }
        String label = player.contextLabel();
        contextLabel.setText(label == null || label.length() == 0 ? "Indie House" : label);
        boolean playing = player.isPlaying();
        play.setBusy(player.isLoading());
        play.setIcon(playing ? R.drawable.ic_pause : R.drawable.ic_play, a.str(playing ? R.string.pause : R.string.play));
        shuffle.setActive(player.shuffle());
        shuffle.setContentDescription(a.str(player.shuffle() ? R.string.shuffle_off : R.string.shuffle));
        repeat.setActive(player.repeat() != Player.REPEAT_OFF);
        repeat.setBadge(player.repeat() == Player.REPEAT_ONE ? "1" : null);
        repeat.setContentDescription(a.str(player.repeat() == Player.REPEAT_OFF ? R.string.repeat_off
                : player.repeat() == Player.REPEAT_ALL ? R.string.repeat_all : R.string.repeat_one));
        next.setEnabled(player.hasNext());
        error.setText(R.string.play_failed);
        error.setVisibility(player.hasFailed() ? View.VISIBLE : View.GONE);
        position.setText((player.index() + 1) + " / " + player.queue().size() + " · " + formatLabel(a.api.streamQuality()));
        progress();
    }

    void progress() {
        if (dragging) return;
        int duration = Math.max(0, a.player.duration());
        int current = Math.min(a.player.position(), duration);
        seek.setMax(Math.max(1, duration));
        seek.setProgress(current);
        seek.setEnabled(!a.player.isLoading() && !a.player.hasFailed());
        showTimes(current, duration);
    }

    private void showTimes(int current, int duration) {
        elapsed.setText(StyledActivity.clock(current));
        remaining.setText("-" + StyledActivity.clock(Math.max(0, duration - current)));
        seek.setContentDescription(a.str(R.string.seek_position, StyledActivity.clock(current), StyledActivity.clock(duration)));
    }

    /** "lossless" (FLAC when the release has it), or "MP3 · 320 kbps". */
    private String formatLabel(String quality) {
        if ("lossless".equals(quality)) return a.str(R.string.quality_word_lossless);
        return "MP3 · " + a.str("high".equals(quality) ? R.string.quality_word_high : R.string.quality_word_standard);
    }

    private void onArtwork(Bitmap bitmap) {
        setWash(Theme.ambient(bitmap));
        // A 12px copy scaled up with filtering reads as a soft blur on API 18.
        Bitmap tiny = Bitmap.createScaledBitmap(bitmap, 12, 12, true);
        BitmapDrawable blur = new BitmapDrawable(a.getResources(), tiny);
        blur.setFilterBitmap(true);
        backdrop.setImageDrawable(blur);
        Widgets.fadeIn(backdrop, 300);
    }

    private void setWash(int tint) {
        wash.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Theme.alpha(tint, .82f), Theme.alpha(Theme.BG, .9f), Theme.BG}));
    }

    private void showQueue() {
        final Player player = a.player;
        List<JSONObject> queue = player.queue();
        Sheet sheet = new Sheet(a).title(a.str(R.string.queue), player.contextLabel() == null ? null : a.str(R.string.playing_from_label, player.contextLabel()));
        int start = Math.max(0, player.index());
        for (int i = start; i < Math.min(queue.size(), start + 40); i++) {
            final int target = i;
            JSONObject track = queue.get(i);
            boolean current = i == player.index();
            sheet.item(current ? R.drawable.ic_signal : R.drawable.ic_note, track.optString("title"),
                    current ? a.str(R.string.queue_now, a.artistName(track)) : (i - start) + " · " + a.artistName(track),
                    current ? Theme.ACCENT : Theme.TEXT, current ? null : () -> player.jumpTo(target));
        }
        if (queue.size() - start <= 1) {
            TextView empty = a.text(a.str(R.string.queue_empty), 14, Theme.MUTED, Theme.regular);
            empty.setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(8));
            sheet.view(empty);
        }
        sheet.show();
    }
}
