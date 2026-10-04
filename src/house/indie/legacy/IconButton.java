package house.indie.legacy;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Touch target around one of the shared web icons. PLAIN buttons are bare
 * glyphs with a square pressed wash; filled styles use the cut-corner play shape.
 */
final class IconButton extends FrameLayout {
    static final int PLAIN = 0;
    static final int PRIMARY = 1;
    static final int RAISED = 2;
    static final int LIGHT = 3;

    private final ImageView image;
    private final View dot;
    private final TextView badge;
    private final int style;
    private ProgressBar spinner;
    private int tint;
    private boolean active;

    IconButton(Context context, int icon, int style, String description, OnClickListener click) {
        super(context);
        this.style = style;
        int fill = style == PRIMARY ? Theme.ACCENT : style == RAISED ? Theme.RAISED : style == LIGHT ? Theme.TEXT : 0;
        int pressed = style == PRIMARY ? Theme.ACCENT_BRIGHT : style == RAISED ? Theme.HOVER
                : style == LIGHT ? 0xffffffff : Theme.PRESSED;
        setBackgroundDrawable(fill == 0
                ? Widgets.states(new ColorDrawable(0), Widgets.shape(context, pressed, 2))
                : Widgets.states(Widgets.cut(context, Widgets.Cut.PLAY, fill), Widgets.cut(context, Widgets.Cut.PLAY, pressed)));
        tint = style == PRIMARY || style == LIGHT ? Theme.INK : Theme.TEXT;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = Widgets.dp(context, 22);
        addView(image, new LayoutParams(size, size, Gravity.CENTER));
        dot = new View(context);
        dot.setBackgroundDrawable(Widgets.block(context, Theme.ACCENT));
        dot.setVisibility(GONE);
        LayoutParams dotParams = new LayoutParams(Widgets.dp(context, 4), Widgets.dp(context, 4),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotParams.bottomMargin = Widgets.dp(context, 3);
        addView(dot, dotParams);
        badge = new TextView(context);
        badge.setTypeface(Theme.bold);
        badge.setTextSize(9);
        badge.setTextColor(Theme.INK);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setBackgroundDrawable(Widgets.block(context, Theme.ACCENT));
        badge.setVisibility(GONE);
        int badgeSize = Widgets.dp(context, 13);
        LayoutParams badgeParams = new LayoutParams(badgeSize, badgeSize, Gravity.CENTER);
        badgeParams.leftMargin = Widgets.dp(context, 12);
        badgeParams.bottomMargin = Widgets.dp(context, 12);
        addView(badge, badgeParams);
        setIcon(icon, description);
        setOnClickListener(click);
        setClickable(true);
        setFocusable(true);
    }

    IconButton iconSize(int dp) {
        int size = Widgets.dp(getContext(), dp);
        image.setLayoutParams(new LayoutParams(size, size, Gravity.CENTER));
        return this;
    }

    void setIcon(int icon, String description) {
        image.setImageResource(icon);
        image.setColorFilter(active ? Theme.ACCENT : tint, PorterDuff.Mode.SRC_IN);
        setContentDescription(description);
    }

    void setTint(int color) {
        tint = color;
        image.setColorFilter(active ? Theme.ACCENT : tint, PorterDuff.Mode.SRC_IN);
    }

    /** Shuffle and repeat: accent glyph plus a dot, as Spotify-style toggles. */
    void setActive(boolean value) {
        active = value;
        setSelected(value);
        dot.setVisibility(value ? VISIBLE : GONE);
        image.setColorFilter(value ? Theme.ACCENT : tint, PorterDuff.Mode.SRC_IN);
    }

    /** Play/pause buttons for a list keep naming the list they control. */
    void setPlaying(boolean playing, String subject) {
        setIcon(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                getContext().getString(playing ? R.string.pause_label : R.string.play_label, subject));
    }

    void setBadge(String value) {
        badge.setText(value == null ? "" : value);
        badge.setVisibility(value == null ? GONE : VISIBLE);
    }

    void setBusy(boolean busy) {
        if (busy && spinner == null) {
            spinner = Widgets.spinner(getContext(), style == PRIMARY || style == LIGHT);
            int size = Widgets.dp(getContext(), 24);
            addView(spinner, new LayoutParams(size, size, Gravity.CENTER));
        }
        if (spinner != null) spinner.setVisibility(busy ? VISIBLE : GONE);
        image.setVisibility(busy ? INVISIBLE : VISIBLE);
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : .38f);
    }
}
