package house.indie.legacy;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One song in a list, like `track-row` on the web: tap to play, the more
 * button (or a long press) opens the track menu. The current song turns yellow
 * and its number becomes an equalizer.
 */
public final class TrackRow extends LinearLayout {
    private final FrameLayout lead;
    private final TextView number;
    private final Widgets.Equalizer leadEqualizer;
    private final Widgets.Equalizer inlineEqualizer;
    private final ArtView art;
    private final TextView title;
    private final TextView subtitle;
    private final IconButton more;
    private String trackId;
    private boolean numbered;

    public TrackRow(Context context, AttributeSet attrs) {
        this(context);
        setTrack("tr-preview", context.getString(R.string.preview_track), context.getString(R.string.artist), null, null);
        setNumber(1);
    }

    public TrackRow(Context context) {
        super(context);
        Theme.load(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = Widgets.dp(context, 8);
        setPadding(pad, Widgets.dp(context, 6), 0, Widgets.dp(context, 6));
        setMinimumHeight(Widgets.dp(context, 56));
        setBackgroundDrawable(Widgets.states(new android.graphics.drawable.ColorDrawable(0),
                Widgets.shape(context, Theme.PRESSED, 6)));

        lead = new FrameLayout(context);
        number = new TextView(context);
        number.setTypeface(Theme.mono);
        number.setTextSize(12);
        number.setTextColor(Theme.MUTED);
        number.setGravity(Gravity.CENTER);
        lead.addView(number, new FrameLayout.LayoutParams(-1, -1));
        leadEqualizer = new Widgets.Equalizer(context, Theme.ACCENT);
        leadEqualizer.setVisibility(GONE);
        lead.addView(leadEqualizer, new FrameLayout.LayoutParams(Widgets.dp(context, 13), Widgets.dp(context, 13), Gravity.CENTER));
        LayoutParams leadParams = new LayoutParams(Widgets.dp(context, 28), Widgets.dp(context, 40));
        leadParams.rightMargin = Widgets.dp(context, 6);
        addView(lead, leadParams);
        lead.setVisibility(GONE);

        art = new ArtView(context, 4);
        LayoutParams artParams = new LayoutParams(Widgets.dp(context, 46), Widgets.dp(context, 46));
        artParams.rightMargin = Widgets.dp(context, 12);
        addView(art, artParams);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(VERTICAL);
        LinearLayout titleLine = new LinearLayout(context);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        inlineEqualizer = new Widgets.Equalizer(context, Theme.ACCENT);
        inlineEqualizer.setVisibility(GONE);
        LayoutParams eqParams = new LayoutParams(Widgets.dp(context, 11), Widgets.dp(context, 11));
        eqParams.rightMargin = Widgets.dp(context, 6);
        titleLine.addView(inlineEqualizer, eqParams);
        title = new TextView(context);
        title.setTypeface(Theme.semibold);
        title.setTextSize(15);
        title.setTextColor(Theme.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleLine.addView(title, new LayoutParams(0, -2, 1));
        words.addView(titleLine);
        subtitle = new TextView(context);
        subtitle.setTypeface(Theme.regular);
        subtitle.setTextSize(13);
        subtitle.setTextColor(Theme.MUTED);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams subtitleParams = new LayoutParams(-1, -2);
        subtitleParams.topMargin = Widgets.dp(context, 1);
        words.addView(subtitle, subtitleParams);
        addView(words, new LayoutParams(0, -2, 1));

        more = new IconButton(context, R.drawable.ic_more, IconButton.PLAIN, context.getString(R.string.options), null);
        more.setTint(Theme.MUTED);
        more.iconSize(20);
        addView(more, new LayoutParams(Widgets.dp(context, 44), Widgets.dp(context, 44)));
    }

    void setTrack(String id, String value, String artist, String artworkUrl, ArtworkLoader loader) {
        trackId = id;
        title.setText(value);
        subtitle.setText(artist);
        art.bind(value, value, artworkUrl, loader);
        more.setContentDescription(getContext().getString(R.string.options_for, value));
        setContentDescription(value + ", " + artist);
    }

    /** Numbers are shown in albums and charts; zero hides the column. */
    void setNumber(int value) {
        numbered = value > 0;
        lead.setVisibility(numbered ? VISIBLE : GONE);
        number.setText(value < 10 ? "0" + value : String.valueOf(value));
    }

    void setArtVisible(boolean visible) { art.setVisibility(visible ? VISIBLE : GONE); }

    String trackId() { return trackId; }

    void setState(boolean current, boolean playing) {
        title.setTextColor(current ? Theme.ACCENT : Theme.TEXT);
        Widgets.Equalizer equalizer = numbered ? leadEqualizer : inlineEqualizer;
        number.setVisibility(numbered && current ? INVISIBLE : VISIBLE);
        leadEqualizer.setVisibility(numbered && current ? VISIBLE : GONE);
        inlineEqualizer.setVisibility(!numbered && current ? VISIBLE : GONE);
        if (current && equalizer.isPlaying() != playing) equalizer.setPlaying(playing);
        if (!current && leadEqualizer.isPlaying()) leadEqualizer.setPlaying(false);
        if (!current && inlineEqualizer.isPlaying()) inlineEqualizer.setPlaying(false);
    }

    void setActions(OnClickListener play, final OnClickListener menu) {
        setOnClickListener(play);
        more.setOnClickListener(menu);
        setOnLongClickListener(menu == null ? null : new OnLongClickListener() {
            @Override public boolean onLongClick(View view) { menu.onClick(view); return true; }
        });
    }
}
