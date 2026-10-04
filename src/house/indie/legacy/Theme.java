package house.indie.legacy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.zip.CRC32;

/**
 * Design tokens shared with the listener web app (`--ih-*` in
 * indie-house-server/src/control-plane/resources/css/app.css) and the bundled typefaces.
 */
final class Theme {
    static final int BG = 0xff07090e;
    static final int SURFACE = 0xff0e1118;
    static final int RAISED = 0xff151a25;
    static final int HOVER = 0xff1d2432;
    static final int FIELD = 0xff0b0e14;
    static final int TEXT = 0xffeef1f6;
    static final int SOFT = 0xffbcc3d0;
    static final int MUTED = 0xff858ea0;
    static final int FAINT = 0xff566074;
    /** Sign yellow: play, progress and the current item only. */
    static final int ACCENT = 0xfff7e23b;
    static final int ACCENT_BRIGHT = 0xfffff07a;
    static final int INK = 0xff0a0b0f;
    /** Karaoke pink: avatar and secondary signals. */
    static final int PINK = 0xffff3d81;
    static final int CYAN = 0xff27e3ec;
    static final int DANGER = 0xffff5a4f;
    static final int LINE = 0x1feef1f6;
    static final int LINE_SOFT = 0x12eef1f6;
    static final int PRESSED = 0x14eef1f6;
    static final int ACCENT_WASH = 0x24f7e23b;
    static final int DANGER_WASH = 0x29ff5a4f;
    static final int SCRIM = 0xb3030408;

    /** Placeholder tones, as `.art[data-tone]` on the web: {accent, base}. */
    private static final int[][] TONES = {
            {0xfff7e23b, 0xff23200a}, {0xffff3d81, 0xff2c0f1c}, {0xff27e3ec, 0xff0a2628},
            {0xff9d7cff, 0xff1d1638}, {0xffff8a3d, 0xff2c170a}, {0xff5b8cff, 0xff0e1a33},
    };

    static Typeface regular = Typeface.DEFAULT;
    static Typeface semibold = Typeface.DEFAULT_BOLD;
    static Typeface bold = Typeface.DEFAULT_BOLD;
    static Typeface extrabold = Typeface.DEFAULT_BOLD;
    static Typeface mono = Typeface.MONOSPACE;
    private static boolean loaded;

    private Theme() { }

    /**
     * Loads Chakra Petch and JetBrains Mono once; system faces remain if a
     * runtime rejects them. Chakra Petch stops at Bold, so "extrabold" (the
     * display weight in layouts) uses Bold too.
     */
    static synchronized void load(Context context) {
        if (loaded) return;
        loaded = true;
        regular = face(context, "chakra-petch-regular", Typeface.DEFAULT);
        semibold = face(context, "chakra-petch-semibold", Typeface.DEFAULT_BOLD);
        bold = face(context, "chakra-petch-bold", Typeface.DEFAULT_BOLD);
        extrabold = bold;
        mono = face(context, "jetbrains-mono-regular", Typeface.MONOSPACE);
    }

    private static Typeface face(Context context, String name, Typeface fallback) {
        try { return Typeface.createFromAsset(context.getAssets(), "fonts/" + name + ".ttf"); }
        catch (RuntimeException missing) { return fallback; }
    }

    /**
     * Applies bundled faces to inflated XML, where API 18 cannot name asset
     * fonts. A TextView's tag picks the face: mono, semibold, bold or extrabold.
     */
    static void applyFonts(View view) {
        if (view instanceof TextView) {
            Object tag = view.getTag();
            String name = tag instanceof String ? (String) tag : "";
            Typeface face = "mono".equals(name) ? mono : "semibold".equals(name) ? semibold
                    : "bold".equals(name) ? bold : "extrabold".equals(name) ? extrabold : regular;
            ((TextView) view).setTypeface(face);
            ((TextView) view).setPaintFlags(((TextView) view).getPaintFlags()
                    | android.graphics.Paint.SUBPIXEL_TEXT_FLAG);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyFonts(group.getChildAt(i));
        }
    }

    /** Same seed-to-tone mapping as `crc32($seed) % 6` in the web art component. */
    static int tone(String seed) {
        CRC32 crc = new CRC32();
        try { crc.update((seed == null ? "indie-house" : seed).getBytes("UTF-8")); }
        catch (java.io.UnsupportedEncodingException impossible) { crc.update(0); }
        return (int) (crc.getValue() % TONES.length);
    }

    static int toneAccent(int tone) { return TONES[tone][0]; }
    static int toneBase(int tone) { return TONES[tone][1]; }

    static int alpha(int color, float amount) {
        return (Math.round(Math.max(0, Math.min(1, amount)) * 255) << 24) | (color & 0x00ffffff);
    }

    static int mix(int from, int to, float amount) {
        float keep = 1 - amount;
        return Color.rgb(
                Math.round(Color.red(from) * keep + Color.red(to) * amount),
                Math.round(Color.green(from) * keep + Color.green(to) * amount),
                Math.round(Color.blue(from) * keep + Color.blue(to) * amount));
    }

    /**
     * A dark tint taken from artwork for headers and the player backdrop,
     * similar to the blurred backdrops of the web app.
     */
    static int ambient(Bitmap bitmap) {
        Bitmap pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
        int color = pixel.getPixel(0, 0);
        if (pixel != bitmap) pixel.recycle();
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(hsv[1], .55f);
        hsv[2] = Math.min(Math.max(hsv[2], .28f), .38f);
        return Color.HSVToColor(hsv);
    }

    /** The tint a placeholder contributes before its artwork arrives. */
    static int ambientForTone(int tone) { return mix(toneBase(tone), toneAccent(tone), .18f); }
}
