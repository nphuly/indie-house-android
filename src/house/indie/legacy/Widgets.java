package house.indie.legacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Small reusable Views for the API 18 UI; none of them draw on a Canvas directly. */
final class Widgets {
    private Widgets() { }

    static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
    }

    /** A filled block. Corners stay nearly square: the "Biển hiệu" look has no pills. */
    static GradientDrawable shape(Context context, int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, Math.min(radius, 3)));
        return drawable;
    }

    /** Small square marker for dots, badges and knobs. */
    static GradientDrawable block(Context context, int color) {
        return shape(context, color, 1);
    }

    /**
     * Solid fill, optionally outlined, with cut corners like the web's
     * clip-path shapes: PLAY and PILL cut the top-right and bottom-left
     * corners, OCTAGON cuts all four.
     */
    static final class Cut extends Drawable {
        static final int PLAY = 0;
        static final int PILL = 1;
        static final int OCTAGON = 2;

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path outline = new Path();
        private final int kind;
        private final float pillCut;
        private final int fillAlpha;
        private final int edgeAlpha;

        Cut(Context context, int kind, int color, int stroke) {
            this.kind = kind;
            pillCut = dp(context, 9);
            fillAlpha = Color.alpha(color);
            edgeAlpha = Color.alpha(stroke);
            fill.setColor(color);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1, dp(context, 1)));
            edge.setColor(stroke);
        }

        @Override protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            trace(path, bounds.left, bounds.top, bounds.right, bounds.bottom);
            float inset = edge.getStrokeWidth() / 2f;
            trace(outline, bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        }

        private void trace(Path target, float left, float top, float right, float bottom) {
            float side = Math.min(right - left, bottom - top);
            float c = kind == PILL ? Math.min(pillCut, side / 2f) : side * (kind == OCTAGON ? .22f : .28f);
            target.reset();
            target.moveTo(kind == OCTAGON ? left + c : left, top);
            target.lineTo(right - c, top);
            target.lineTo(right, top + c);
            target.lineTo(right, bottom - (kind == OCTAGON ? c : 0));
            if (kind == OCTAGON) target.lineTo(right - c, bottom);
            target.lineTo(left + c, bottom);
            target.lineTo(left, bottom - c);
            if (kind == OCTAGON) target.lineTo(left, top + c);
            target.close();
        }

        @Override public void draw(Canvas canvas) {
            if (Color.alpha(fill.getColor()) > 0) canvas.drawPath(path, fill);
            if (Color.alpha(edge.getColor()) > 0) canvas.drawPath(outline, edge);
        }

        /**
         * Scales the colours' own alpha instead of replacing it: on API 18
         * StateListDrawable calls setAlpha(255) on every state it shows, which
         * would turn washes such as DANGER_WASH fully opaque.
         */
        @Override public void setAlpha(int alpha) {
            fill.setAlpha(fillAlpha * alpha / 255);
            edge.setAlpha(edgeAlpha * alpha / 255);
            invalidateSelf();
        }
        @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); edge.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static Drawable cut(Context context, int kind, int color) { return new Cut(context, kind, color, 0); }

    static StateListDrawable states(Drawable normal, Drawable pressed) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {android.R.attr.state_pressed}, pressed);
        result.addState(new int[] {android.R.attr.state_focused}, pressed);
        result.addState(new int[] {}, normal);
        return result;
    }

    static Drawable tinted(Context context, int icon, int color) {
        Drawable drawable = context.getResources().getDrawable(icon).mutate();
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return drawable;
    }

    static ImageView icon(Context context, int icon, int color) {
        ImageView view = new ImageView(context);
        view.setImageResource(icon);
        view.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return view;
    }

    static ProgressBar spinner(Context context, boolean onAccent) {
        ProgressBar bar = new ProgressBar(context);
        bar.setIndeterminateDrawable(context.getResources().getDrawable(
                onAccent ? R.drawable.spinner_ink : R.drawable.spinner_accent));
        bar.setIndeterminate(true);
        return bar;
    }

    /** Three bars that bounce while a row's track plays, like `.eq` on the web. */
    static final class Equalizer extends LinearLayout {
        private final View[] bars = new View[3];
        private boolean playing;

        Equalizer(Context context, int color) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            for (int i = 0; i < bars.length; i++) {
                bars[i] = new View(context);
                bars[i].setBackgroundDrawable(shape(context, color, 1));
                LayoutParams params = new LayoutParams(dp(context, 3), -1);
                if (i > 0) params.leftMargin = dp(context, 2);
                addView(bars[i], params);
            }
            setPlaying(false);
        }

        void setPlaying(boolean value) {
            playing = value;
            final float[] rest = {.45f, .8f, .3f};
            final int[] durations = {430, 560, 380};
            for (int i = 0; i < bars.length; i++) {
                Animation animation = value
                        ? new ScaleAnimation(1, 1, .25f, 1, Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 1)
                        : new ScaleAnimation(1, 1, rest[i], rest[i], Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 1);
                animation.setDuration(value ? durations[i] : 0);
                animation.setFillAfter(true);
                if (value) {
                    animation.setRepeatCount(Animation.INFINITE);
                    animation.setRepeatMode(Animation.REVERSE);
                    animation.setStartOffset(i * 90L);
                }
                bars[i].startAnimation(animation);
            }
        }

        boolean isPlaying() { return playing; }
    }

    /** A two-state switch styled like the web settings switches. */
    static final class Toggle extends FrameLayout {
        interface Listener { void onToggle(boolean checked); }

        private final View thumb;
        private final GradientDrawable track;
        private final GradientDrawable knob;
        private boolean checked;
        private Listener listener;

        Toggle(Context context) {
            super(context);
            track = new GradientDrawable();
            track.setCornerRadius(dp(context, 2));
            setBackgroundDrawable(track);
            knob = block(context, Theme.MUTED);
            thumb = new View(context);
            thumb.setBackgroundDrawable(knob);
            LayoutParams params = new LayoutParams(dp(context, 20), dp(context, 20), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            params.leftMargin = dp(context, 4);
            addView(thumb, params);
            setClickable(true);
            setFocusable(true);
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View view) {
                    setChecked(!checked, true);
                    if (listener != null) listener.onToggle(checked);
                }
            });
            setChecked(false, false);
        }

        void setListener(Listener value) { listener = value; }
        boolean isChecked() { return checked; }

        void setChecked(boolean value, boolean animate) {
            checked = value;
            setSelected(value);
            track.setColor(value ? Theme.ACCENT : Theme.HOVER);
            knob.setColor(value ? Theme.INK : Theme.SOFT);
            float x = value ? dp(getContext(), 18) : 0;
            if (animate) thumb.animate().translationX(x).setDuration(160).start();
            else thumb.setTranslationX(x);
        }
    }

    /** ScrollView that reports its offset (API 18 has no scroll listener). */
    static final class WatchedScrollView extends ScrollView {
        interface Listener { void onScrolled(int y); }

        private Listener listener;

        WatchedScrollView(Context context) { super(context); }

        void setListener(Listener value) { listener = value; }

        @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
            super.onScrollChanged(x, y, oldX, oldY);
            if (listener != null) listener.onScrolled(y);
        }
    }

    /**
     * Pull down at the top of a page to reload it, as on iOS. The page
     * follows the finger at half speed while a ring turns in the gap; letting
     * go past the mark keeps a spinner there until {@link #setRefreshing}
     * turns it off. API 18 has no SwipeRefreshLayout without the support
     * library, which this app does not use.
     */
    static final class PullRefresh extends FrameLayout {
        interface Listener { void onRefresh(); }

        private final ScrollView scroll;
        private final FrameLayout badge;
        private final ImageView ring;
        private final ProgressBar spinner;
        private final int slop;
        private final int trigger;
        private final int max;
        private final int badgeSize;
        private Listener listener;
        private boolean pullable = true;
        private boolean pulling;
        private boolean refreshing;
        private float startX;
        private float startY;
        private float offset;
        private android.animation.ValueAnimator settling;

        PullRefresh(Context context, ScrollView scroll) {
            super(context);
            this.scroll = scroll;
            slop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
            trigger = dp(context, 64);
            max = dp(context, 96);
            badgeSize = dp(context, 36);
            addView(scroll, new LayoutParams(-1, -1));
            badge = new FrameLayout(context);
            badge.setBackgroundDrawable(cut(context, Cut.OCTAGON, Theme.RAISED));
            ring = new ImageView(context);
            ring.setImageDrawable(context.getResources().getDrawable(R.drawable.spinner_accent));
            badge.addView(ring, new LayoutParams(dp(context, 22), dp(context, 22), Gravity.CENTER));
            spinner = spinner(context, false);
            spinner.setVisibility(GONE);
            badge.addView(spinner, new LayoutParams(dp(context, 22), dp(context, 22), Gravity.CENTER));
            addView(badge, new LayoutParams(badgeSize, badgeSize, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            setOffset(0);
        }

        void setListener(Listener value) { listener = value; }

        /** Pages without data to reload (the search page) do not pull. */
        void setPullable(boolean value) { pullable = value; }

        boolean isRefreshing() { return refreshing; }

        void setRefreshing(boolean value) {
            if (value == refreshing) return;
            refreshing = value;
            ring.setVisibility(value ? GONE : VISIBLE);
            spinner.setVisibility(value ? VISIBLE : GONE);
            settle(value ? trigger : 0);
        }

        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
            if (!pullable || refreshing || listener == null) return false;
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    pulling = false;
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dy = event.getY() - startY;
                    float dx = Math.abs(event.getX() - startX);
                    if (!pulling && scroll.getScrollY() == 0 && dy > slop && dy > dx * 1.5f) {
                        pulling = true;
                        startY += slop; // So the page does not jump by the slop.
                        if (settling != null) settling.cancel();
                    }
                    break;
                default:
                    break;
            }
            return pulling;
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (!pulling) return false;
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_MOVE:
                    setOffset(Math.max(0, Math.min(max, (event.getY() - startY) * .5f)));
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    pulling = false;
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP && offset >= trigger) {
                        setRefreshing(true);
                        listener.onRefresh();
                    } else settle(0);
                    break;
                default:
                    break;
            }
            return true;
        }

        private void settle(float target) {
            if (settling != null) settling.cancel();
            settling = android.animation.ValueAnimator.ofFloat(offset, target);
            settling.setDuration(180);
            settling.addUpdateListener(animation -> setOffset((Float) animation.getAnimatedValue()));
            settling.start();
        }

        private void setOffset(float value) {
            offset = value;
            scroll.setTranslationY(value);
            // Hidden above the page at rest, then centred in the gap.
            badge.setTranslationY(value < badgeSize ? value - badgeSize : (value - badgeSize) / 2);
            float progress = Math.min(1, value / trigger);
            badge.setAlpha(refreshing ? 1 : progress);
            ring.setImageLevel((int) (progress * 3333)); // One turn of the ring's 1080°.
        }
    }

    /** Keeps its content square and as large as the space allows. */
    static final class SquareLayout extends FrameLayout {
        SquareLayout(Context context) { super(context); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            int size = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? width
                    : MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? height : Math.min(width, height);
            int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
            super.onMeasure(exact, exact);
        }
    }

    /** The thin yellow bar that runs under the top bar while requests are in flight. */
    static final class LoadingStrip extends FrameLayout {
        private final View runner;
        private boolean active;

        LoadingStrip(Context context) {
            super(context);
            runner = new View(context);
            runner.setBackgroundColor(Theme.ACCENT);
            // The strip stays visible so its background continues the top bar tint.
            runner.setVisibility(INVISIBLE);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (runner.getParent() == null) addView(runner, new LayoutParams(Math.max(1, width * 3 / 10), -1));
            else runner.getLayoutParams().width = Math.max(1, width * 3 / 10);
            if (active) run();
        }

        void setActive(boolean value) {
            if (value == active) return;
            active = value;
            if (value) { runner.setVisibility(VISIBLE); run(); }
            else { runner.clearAnimation(); runner.setVisibility(INVISIBLE); }
        }

        private void run() {
            TranslateAnimation slide = new TranslateAnimation(Animation.RELATIVE_TO_SELF, -1, Animation.RELATIVE_TO_PARENT, 1,
                    Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 0);
            slide.setDuration(900);
            slide.setRepeatCount(Animation.INFINITE);
            runner.startAnimation(slide);
        }
    }

    /** Pill button with optional icon and a busy state, matching `.button` on the web. */
    static final class PillButton extends LinearLayout {
        static final int PRIMARY = 0;
        static final int SECONDARY = 1;
        static final int GHOST = 2;
        static final int DANGER = 3;
        static final int LIGHT = 4;

        private final TextView label;
        private final ImageView icon;
        private final ProgressBar spinner;
        private final int kind;
        private final boolean hasIcon;
        private String text;

        PillButton(Context context, String value, int iconRes, int kind, OnClickListener click) {
            super(context);
            this.kind = kind;
            hasIcon = iconRes != 0;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(context, 20), 0, dp(context, 20), 0);
            int fill = kind == PRIMARY ? Theme.ACCENT : kind == LIGHT ? Theme.TEXT : kind == DANGER ? Theme.DANGER_WASH : 0x00000000;
            int pressed = kind == PRIMARY ? Theme.ACCENT_BRIGHT : kind == LIGHT ? 0xffffffff
                    : kind == DANGER ? Theme.alpha(Theme.DANGER, .26f) : Theme.PRESSED;
            boolean outlined = kind == SECONDARY;
            setBackgroundDrawable(states(
                    new Cut(context, Cut.PILL, fill, outlined ? Theme.alpha(Theme.TEXT, .34f) : 0),
                    new Cut(context, Cut.PILL, pressed, outlined ? Theme.TEXT : 0)));
            int color = kind == PRIMARY || kind == LIGHT ? Theme.INK : kind == DANGER ? Theme.DANGER : Theme.TEXT;
            icon = icon(context, hasIcon ? iconRes : R.drawable.ic_plus, color);
            icon.setVisibility(hasIcon ? VISIBLE : GONE);
            LayoutParams iconParams = new LayoutParams(dp(context, 20), dp(context, 20));
            iconParams.rightMargin = dp(context, 8);
            addView(icon, iconParams);
            spinner = spinner(context, kind == PRIMARY || kind == LIGHT);
            spinner.setVisibility(GONE);
            LayoutParams spinnerParams = new LayoutParams(dp(context, 18), dp(context, 18));
            spinnerParams.rightMargin = dp(context, 8);
            addView(spinner, spinnerParams);
            label = new TextView(context);
            label.setTextColor(color);
            label.setTextSize(15);
            label.setTypeface(Theme.semibold);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            addView(label, new LayoutParams(-2, -2));
            setLabel(value);
            setClickable(true);
            setFocusable(true);
            setOnClickListener(click);
        }

        void setLabel(String value) {
            text = value;
            label.setText(value);
            setContentDescription(value);
        }

        void setBusy(boolean busy, String busyLabel) {
            spinner.setVisibility(busy ? VISIBLE : GONE);
            icon.setVisibility(!busy && hasIcon ? VISIBLE : GONE);
            label.setText(busy && busyLabel != null ? busyLabel : text);
            setEnabled(!busy);
        }

        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setAlpha(enabled ? 1f : kind == PRIMARY ? .72f : .5f);
        }
    }

    /** Fades a freshly added view in; content appears without a hard cut. */
    static void fadeIn(View view, long duration) {
        AlphaAnimation fade = new AlphaAnimation(0, 1);
        fade.setDuration(duration);
        view.startAnimation(fade);
    }

    /** Placeholder blocks pulse while a page loads, instead of a spinner. */
    static void pulse(View view) {
        AlphaAnimation pulse = new AlphaAnimation(.45f, 1);
        pulse.setDuration(700);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        view.startAnimation(pulse);
    }
}
