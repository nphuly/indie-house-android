package house.indie.legacy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Artwork frame, like the web `art` component: without artwork it shows a
 * tinted placeholder whose tone comes from a seed, so an item keeps the same
 * colour everywhere; loaded artwork fades in. The frame has one cut corner
 * (artists: an octagon), like `--ih-clip-art` on the web. Loaded artwork gets
 * the web's hologram layer: faint scanlines, a cyan and pink tint and a thin
 * colour fringe on the edges.
 */
final class ArtView extends FrameLayout implements ArtworkLoader.Target {
    interface Listener { void onArtwork(Bitmap bitmap); }

    private static Bitmap scanlines;

    private final Path cut = new Path();
    private final Paint lines = new Paint();
    private final Paint tint = new Paint();
    private final Paint fringe = new Paint();
    private final ImageView image;
    private final TextView glyph;
    private final ImageView icon;
    private GradientDrawable base;
    private GradientDrawable glow;
    private boolean squircle;
    private String url;
    private int tone;
    private Listener listener;
    private Bitmap bitmap;
    private int measuredFor;

    ArtView(Context context, float radiusDp) {
        super(context);
        glyph = new TextView(context);
        glyph.setTypeface(Theme.mono);
        glyph.setGravity(Gravity.CENTER);
        glyph.setIncludeFontPadding(false);
        addView(glyph, new LayoutParams(-1, -1));
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setVisibility(GONE);
        addView(icon, new LayoutParams(-1, -1));
        image = new ImageView(context);
        addView(image, new LayoutParams(-1, -1));
        lines.setShader(new BitmapShader(scanlines(context), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        setTone(0);
    }

    /** One scanline period, as `--ih-holo-lines`: a 1 px line every 3 px, scaled to the density. */
    private static Bitmap scanlines(Context context) {
        if (scanlines == null) {
            float density = context.getResources().getDisplayMetrics().density;
            int period = Math.max(3, Math.round(3 * density));
            int line = Math.max(1, Math.round(density));
            scanlines = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < line; y++) scanlines.setPixel(0, y, Theme.alpha(0xffd6faff, .07f));
        }
        return scanlines;
    }

    /** Artist portraits are octagons, as `--ih-clip-octagon` on the web. */
    void setSquircle(boolean value) { squircle = value; applyRadius(getWidth()); }

    void setListener(Listener value) {
        listener = value;
        if (bitmap != null && listener != null) listener.onArtwork(bitmap);
    }

    void bind(String seed, String title, String artworkUrl, ArtworkLoader loader) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.length() == 0) showIcon(seed, R.drawable.ic_note);
        else {
            setTone(Theme.tone(seed));
            glyph.setText(trimmed.substring(0, 1).toUpperCase());
            glyph.setVisibility(VISIBLE);
            icon.setVisibility(GONE);
        }
        load(artworkUrl, loader);
    }

    void bindIcon(String seed, int iconRes) {
        showIcon(seed, iconRes);
        load(null, null);
    }

    private void showIcon(String seed, int iconRes) {
        setTone(Theme.tone(seed));
        glyph.setVisibility(GONE);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Theme.toneAccent(tone), android.graphics.PorterDuff.Mode.SRC_IN);
        icon.setVisibility(VISIBLE);
    }

    int tone() { return tone; }

    private void load(String artworkUrl, ArtworkLoader loader) {
        url = artworkUrl == null || artworkUrl.length() == 0 ? null : artworkUrl;
        bitmap = null;
        image.setImageDrawable(null);
        invalidate(); // drops the hologram layer, which draw() adds only over artwork
        if (url != null && loader != null) loader.load(url, this);
    }

    @Override public void onArtwork(String source, Bitmap result, boolean cached) {
        if (url == null || !url.equals(source)) return;
        bitmap = result;
        image.setImageDrawable(new Rounded(result, 0));
        invalidate();
        if (!cached) Widgets.fadeIn(image, 220);
        if (listener != null) listener.onArtwork(result);
    }

    private void setTone(int value) {
        tone = value;
        base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {Theme.toneBase(tone), 0xff080a10});
        glow = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {Theme.alpha(Theme.toneAccent(tone), .42f), Theme.alpha(Theme.toneAccent(tone), 0)});
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(.85f, .08f);
        setBackgroundDrawable(new LayerDrawable(new Drawable[] {base, glow}));
        glyph.setTextColor(Theme.alpha(Theme.mix(Theme.toneAccent(tone), 0xffffffff, .25f), .85f));
        applyRadius(getWidth());
    }

    private void applyRadius(int width) {
        glow.setGradientRadius(Math.max(1, width * 1.1f));
        int height = getHeight() > 0 ? getHeight() : width;
        cut.reset();
        if (width > 0) {
            if (squircle) {
                float c = width * .22f;
                cut.moveTo(c, 0);
                cut.lineTo(width - c, 0);
                cut.lineTo(width, c);
                cut.lineTo(width, height - c);
                cut.lineTo(width - c, height);
                cut.lineTo(c, height);
                cut.lineTo(0, height - c);
                cut.lineTo(0, c);
            } else {
                float c = Math.min(width * .14f, 18 * getResources().getDisplayMetrics().density);
                cut.moveTo(0, 0);
                cut.lineTo(width - c, 0);
                cut.lineTo(width, c);
                cut.lineTo(width, height);
                cut.lineTo(0, height);
            }
            cut.close();
            tint.setShader(new LinearGradient(0, 0, width, height,
                    new int[] {Theme.alpha(Theme.CYAN, .09f), 0, 0, Theme.alpha(Theme.PINK, .09f)},
                    new float[] {0, .42f, .64f, 1}, Shader.TileMode.CLAMP));
        }
        invalidate();
    }

    private void drawHologram(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.drawRect(0, 0, width, height, tint);
        canvas.drawRect(0, 0, width, height, lines);
        float edge = 2 * getResources().getDisplayMetrics().density;
        fringe.setColor(Theme.alpha(Theme.PINK, .24f));
        canvas.drawRect(0, 0, edge, height, fringe);
        fringe.setColor(Theme.alpha(Theme.CYAN, .24f));
        canvas.drawRect(width - edge, 0, width, height, fringe);
    }

    /** API 18 is the first release that clips paths on a hardware canvas. */
    @Override public void draw(Canvas canvas) {
        if (cut.isEmpty()) {
            super.draw(canvas);
            return;
        }
        int saved = canvas.save();
        canvas.clipPath(cut);
        super.draw(canvas);
        if (bitmap != null) drawHologram(canvas);
        canvas.restoreToCount(saved);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (width > 0 && width != measuredFor) {
            // Glyph and icon scale with the frame, like the web's container-query units.
            measuredFor = width;
            float density = getResources().getDisplayMetrics().density;
            glyph.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(width * .38f, 72 * density));
            int inset = Math.round(width * .29f);
            icon.setPadding(inset, inset, inset, inset);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        applyRadius(width);
    }

    /** Draws a bitmap centre-cropped inside a rounded rectangle. */
    static final class Rounded extends Drawable {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final BitmapShader shader;
        private final RectF rect = new RectF();
        private final Matrix matrix = new Matrix();
        private float radius;

        Rounded(Bitmap bitmap, float radius) {
            this.bitmap = bitmap;
            this.radius = radius;
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }

        void setRadius(float value) { radius = value; invalidateSelf(); }

        @Override protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            rect.set(bounds);
            float scale = Math.max(bounds.width() / (float) bitmap.getWidth(), bounds.height() / (float) bitmap.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate(bounds.left + (bounds.width() - bitmap.getWidth() * scale) / 2f,
                    bounds.top + (bounds.height() - bitmap.getHeight() * scale) / 2f);
            shader.setLocalMatrix(matrix);
        }

        @Override public void draw(Canvas canvas) { canvas.drawRoundRect(rect, radius, radius, paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return -1; }
        @Override public int getIntrinsicHeight() { return -1; }
    }
}
