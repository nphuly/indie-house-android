package house.indie.legacy;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Shared API-18-safe builders so every programmatic screen uses the same tokens. */
abstract class StyledActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        Locales.apply(this);
        super.onCreate(state);
        Theme.load(this);
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        // A system change (rotation, keyboard) resets Resources to the device language.
        Locales.apply(this);
    }

    String str(int id, Object... args) { return getString(id, args); }

    String plural(int id, int count) { return getResources().getQuantityString(id, count, count); }

    int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    GradientDrawable shape(int color, float radius) { return Widgets.shape(this, color, radius); }

    GradientDrawable outline(int fill, int stroke, float radius) {
        GradientDrawable drawable = shape(fill, radius);
        drawable.setStroke(Math.max(1, dp(1)), stroke);
        return drawable;
    }

    /** Transparent at rest, a soft wash when pressed: rows, tiles and links. */
    Drawable touch(float radius) {
        return Widgets.states(new android.graphics.drawable.ColorDrawable(0), shape(Theme.PRESSED, radius));
    }

    Drawable pressable(int color, int pressed, float radius) {
        return Widgets.states(shape(color, radius), shape(pressed, radius));
    }

    TextView text(String value, float size, int color, Typeface face) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(face);
        return view;
    }

    TextView line(String value, float size, int color, Typeface face) {
        TextView view = text(value, size, color, face);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    /** Small monospaced kicker above titles, as `.eyebrow` on the web. */
    TextView eyebrow(String value, int color) {
        return text(value.toUpperCase(java.util.Locale.getDefault()), 11, color, Theme.mono);
    }

    /** Display titles shrink for long names, like `is-long` / `is-xlong` on the web. */
    TextView display(String value, float base) {
        int length = value == null ? 0 : value.length();
        float size = length > 34 ? base * .66f : length > 18 ? base * .8f : base;
        TextView view = text(value, size, Theme.TEXT, Theme.extrabold);
        view.setLineSpacing(0, .95f);
        view.setMaxLines(3);
        return view;
    }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    void gap(ViewGroup layout, float height) {
        layout.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    View divider() {
        View view = new View(this);
        view.setBackgroundColor(Theme.LINE_SOFT);
        return view;
    }

    LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-2, -2); }
    LinearLayout.LayoutParams fill() { return new LinearLayout.LayoutParams(-1, -2); }
    LinearLayout.LayoutParams size(float width, float height) { return new LinearLayout.LayoutParams(dp(width), dp(height)); }

    LinearLayout.LayoutParams margins(LinearLayout.LayoutParams params, float left, float top, float right, float bottom) {
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    ImageView glyph(int icon, int color) { return Widgets.icon(this, icon, color); }

    IconButton iconButton(int icon, int style, String description, View.OnClickListener click) {
        return new IconButton(this, icon, style, description, click);
    }

    Widgets.PillButton pill(String label, int icon, int kind, View.OnClickListener click) {
        return new Widgets.PillButton(this, label, icon, kind, click);
    }

    /** Field styled like `.field` on the web, with an accent focus ring. */
    EditText input(String hint, int icon, boolean password) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setHintTextColor(Theme.FAINT);
        view.setTextColor(Theme.TEXT);
        view.setTextSize(16);
        view.setTypeface(Theme.regular);
        view.setSingleLine(true);
        view.setPadding(dp(icon == 0 ? 16 : 14), 0, dp(16), 0);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackgroundDrawable(Widgets.states(outline(Theme.FIELD, Theme.LINE, 8), outline(Theme.FIELD, Theme.ACCENT, 8)));
        if (icon != 0) {
            Drawable glyph = Widgets.tinted(this, icon, Theme.MUTED);
            glyph.setBounds(0, 0, dp(20), dp(20));
            view.setCompoundDrawables(glyph, null, null, null);
            view.setCompoundDrawablePadding(dp(10));
        }
        if (password) {
            view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            view.setTypeface(Theme.regular);
        }
        return view;
    }

    TextView fieldLabel(String value) {
        TextView label = eyebrow(value, Theme.MUTED);
        label.setPadding(0, 0, 0, dp(8));
        return label;
    }

    void styleSeekBar(SeekBar bar) {
        GradientDrawable track = shape(Theme.alpha(Theme.TEXT, .18f), 2);
        GradientDrawable fill = shape(Theme.ACCENT, 2);
        ClipDrawable clipped = new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {track, clipped});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        // Holo SeekBar limits the track drawable to 13dp; inset it to a 4dp line.
        layers.setLayerInset(0, 0, dp(4.5f), 0, dp(4.5f));
        layers.setLayerInset(1, 0, dp(4.5f), 0, dp(4.5f));
        bar.setProgressDrawable(layers);
        GradientDrawable thumb = shape(Theme.TEXT, 0);
        thumb.setSize(dp(4), dp(14));
        bar.setThumb(thumb);
        bar.setThumbOffset(dp(2));
        bar.setPadding(dp(7), 0, dp(7), 0);
        bar.setMinimumHeight(dp(22));
    }

    /** "3 phút" / "1 hr 4 min", as `Duration::long` on the web. */
    String duration(int seconds) {
        if (seconds < 60) return str(R.string.duration_seconds, seconds);
        int minutes = Math.round(seconds / 60f);
        if (minutes < 60) return str(R.string.duration_minutes, minutes);
        return str(R.string.duration_hours_minutes, minutes / 60, minutes % 60);
    }

    static String clock(int millis) {
        int seconds = Math.max(0, millis / 1000);
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }

    static String two(int value) { return value < 10 ? "0" + value : String.valueOf(value); }
}
