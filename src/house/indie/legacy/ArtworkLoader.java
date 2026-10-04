package house.indie.legacy;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.LruCache;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded image loader for the API 18 native views. */
final class ArtworkLoader {
    interface Target { void onArtwork(String url, Bitmap bitmap, boolean cached); }

    private final IndieApi api;
    private final Handler main = new Handler();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    /** Longest side kept after decoding: the full-width player art on the Leap's 720 px screen. */
    private static final int MAX_SIDE = 720;

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 6, 32L * 1024 * 1024)) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    // Artist and album artwork addresses are sometimes derived from track data,
    // so remember the ones without an image instead of asking again.
    private final Set<String> missing = Collections.synchronizedSet(new HashSet<String>());

    ArtworkLoader(IndieApi api) { this.api = api; }

    void load(final String url, final Target target) {
        if (url == null || url.length() == 0 || missing.contains(url)) return;
        Bitmap cached = cache.get(url);
        if (cached != null) { target.onArtwork(url, cached, true); return; }
        workers.execute(new Runnable() {
            @Override public void run() {
                try {
                    byte[] bytes = api.artwork(url);
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) { missing.add(url); return; }
                    // Full colour (RGB_565 bands gradients), and never sampled below
                    // MAX_SIDE: halve only while the result stays at least that big,
                    // then scale the rest of the way with filtering.
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    options.inSampleSize = 1;
                    int shortest = Math.min(bounds.outWidth, bounds.outHeight);
                    while (shortest / (options.inSampleSize * 2) >= MAX_SIDE) options.inSampleSize *= 2;
                    Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    if (decoded == null) { missing.add(url); return; }
                    final Bitmap bitmap = fit(decoded);
                    cache.put(url, bitmap);
                    main.post(new Runnable() {
                        @Override public void run() { target.onArtwork(url, bitmap, false); }
                    });
                } catch (IndieApi.ApiError error) {
                    if (error.status == 404 || error.status == 0) missing.add(url);
                } catch (Exception ignored) { /* The placeholder remains; a later screen may retry. */ }
            }
        });
    }

    private static Bitmap fit(Bitmap source) {
        int shortest = Math.min(source.getWidth(), source.getHeight());
        if (shortest <= MAX_SIDE) return source;
        float scale = MAX_SIDE / (float) shortest;
        Bitmap scaled = Bitmap.createScaledBitmap(source, Math.round(source.getWidth() * scale),
                Math.round(source.getHeight() * scale), true);
        if (scaled != source) source.recycle();
        return scaled;
    }

    void clear() { cache.evictAll(); missing.clear(); }
    void close() { workers.shutdownNow(); cache.evictAll(); }
}
