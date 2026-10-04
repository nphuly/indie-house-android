package house.indie.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Playback queue around Android MediaPlayer, with the web player's rules:
 * shuffle reorders what is left, repeat cycles off/all/one, autoplay moves
 * to the next song, and a listen is logged once it passes ten seconds or ends.
 */
final class Player {
    interface Listener {
        void onPlayerChanged();
        void onPlayerProgress();
        void onPlayerError(String message);
        void onPlayerNotice(String message);
    }

    static final int REPEAT_OFF = 0;
    static final int REPEAT_ALL = 1;
    static final int REPEAT_ONE = 2;

    private final Context context;
    private final IndieApi api;
    private final ExecutorService background;
    private final Listener listener;
    private final SharedPreferences prefs;
    private final Handler main = new Handler();
    private final ArrayList<JSONObject> source = new ArrayList<JSONObject>();
    private final ArrayList<JSONObject> queue = new ArrayList<JSONObject>();
    private MediaPlayer media;
    private int index = -1;
    private boolean shuffle;
    private int repeat;
    private boolean autoplay = true;
    private String contextId;
    private String contextLabel;
    private boolean ready;
    private boolean preparing;
    private boolean failed;
    private String playbackId;
    private boolean logged;
    private int generation;
    /** Last known place in the song; a broken player cannot report it. */
    private int lastPosition;
    private int retries;
    /** The current load reached playback. */
    private boolean started;
    /** Lossless failed at the start, so this load tries 320 kbps. */
    private boolean flacSuspect;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (isPlaying()) {
                lastPosition = position();
                listener.onPlayerProgress();
                main.postDelayed(this, 500);
            }
        }
    };

    Player(Context context, IndieApi api, ExecutorService background, Listener listener) {
        this.context = context;
        this.api = api;
        this.background = background;
        this.listener = listener;
        prefs = context.getSharedPreferences("indie_house", Context.MODE_PRIVATE);
        shuffle = prefs.getBoolean("player_shuffle", false);
        repeat = prefs.getInt("player_repeat", REPEAT_OFF);
    }

    JSONObject current() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }
    String currentId() { JSONObject track = current(); return track == null ? null : track.optString("id"); }
    boolean isPlaying() { return media != null && ready && media.isPlaying(); }
    boolean isLoading() { return preparing; }
    boolean hasFailed() { return failed; }
    boolean shuffle() { return shuffle; }
    int repeat() { return repeat; }
    String contextId() { return contextId; }
    String contextLabel() { return contextLabel; }
    int index() { return index; }
    List<JSONObject> queue() { return Collections.unmodifiableList(queue); }
    void setAutoplay(boolean value) { autoplay = value; }

    int position() {
        try { return media != null && ready ? media.getCurrentPosition() : 0; }
        catch (IllegalStateException released) { return 0; }
    }

    int duration() {
        try { if (media != null && ready && media.getDuration() > 0) return media.getDuration(); }
        catch (IllegalStateException released) { /* Fall back to the catalogue length. */ }
        JSONObject track = current();
        return track == null ? 0 : track.optInt("duration_seconds") * 1000;
    }

    /** Starts a list from one song; `shuffled` picks a random start, like the web shuffle button. */
    void start(List<JSONObject> tracks, int startIndex, String id, String label, boolean shuffled) {
        if (tracks.isEmpty()) return;
        source.clear();
        source.addAll(tracks);
        contextId = id;
        contextLabel = label;
        if (shuffled) {
            setShuffleFlag(true);
            startIndex = (int) (Math.random() * tracks.size());
        }
        arrange(source.get(Math.max(0, Math.min(startIndex, source.size() - 1))));
        playAt(index);
    }

    /** After a failure, Play carries on from where the song stopped. */
    void toggle() {
        if (current() == null || preparing) return;
        if (failed) { retries = 0; load(lastPosition); return; }
        if (media == null) { playAt(index); return; }
        if (!ready) return;
        if (media.isPlaying()) {
            media.pause();
            main.removeCallbacks(tick);
        } else {
            media.start();
            main.post(tick);
        }
        listener.onPlayerChanged();
    }

    void next() { advance(); }

    void previous() {
        if (current() == null) return;
        if (position() > 3000 || index == 0 && repeat != REPEAT_ALL) { seekTo(0); return; }
        playAt(index > 0 ? index - 1 : queue.size() - 1);
    }

    void seekTo(int millis) {
        if (media != null && ready) {
            lastPosition = Math.max(0, Math.min(millis, duration()));
            media.seekTo(lastPosition);
            listener.onPlayerProgress();
        }
    }

    void jumpTo(int queueIndex) {
        if (queueIndex >= 0 && queueIndex < queue.size()) playAt(queueIndex);
    }

    void setShuffle(boolean value) {
        setShuffleFlag(value);
        JSONObject playing = current();
        if (playing != null) arrange(playing);
        listener.onPlayerChanged();
    }

    void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        prefs.edit().putInt("player_repeat", repeat).apply();
        listener.onPlayerChanged();
    }

    /** Queues a song right after the current one. */
    void playNext(JSONObject track) {
        if (current() == null) {
            ArrayList<JSONObject> single = new ArrayList<JSONObject>();
            single.add(track);
            start(single, 0, null, context.getString(R.string.queue), false);
            return;
        }
        queue.add(index + 1, track);
        int at = indexOf(source, current());
        source.add(at < 0 ? source.size() : at + 1, track);
        listener.onPlayerChanged();
    }

    void stop() {
        logListen(false);
        generation++;
        release();
        queue.clear();
        source.clear();
        index = -1;
        contextId = null;
        contextLabel = null;
        preparing = false;
        failed = false;
        listener.onPlayerChanged();
    }

    private void setShuffleFlag(boolean value) {
        shuffle = value;
        prefs.edit().putBoolean("player_shuffle", value).apply();
    }

    /** Builds the play order around the song that plays now. */
    private void arrange(JSONObject first) {
        queue.clear();
        if (shuffle) {
            ArrayList<JSONObject> rest = new ArrayList<JSONObject>(source);
            rest.remove(indexOf(rest, first));
            Collections.shuffle(rest);
            queue.add(first);
            queue.addAll(rest);
            index = 0;
        } else {
            queue.addAll(source);
            index = Math.max(0, indexOf(queue, first));
        }
    }

    private static int indexOf(List<JSONObject> list, JSONObject item) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) == item) return i;
        return -1;
    }

    boolean hasNext() { return current() != null && (index + 1 < queue.size() || repeat == REPEAT_ALL); }

    /** At the end of the queue the last song stays loaded unless repeat-all wraps around. */
    private void advance() {
        if (current() == null) return;
        if (index + 1 < queue.size()) playAt(index + 1);
        else if (repeat == REPEAT_ALL) {
            if (shuffle) arrange(queue.get((int) (Math.random() * queue.size())));
            else index = 0;
            playAt(index);
        } else listener.onPlayerChanged();
    }

    private void playAt(int position) {
        logListen(false);
        index = position;
        logged = false;
        playbackId = UUID.randomUUID().toString();
        retries = 0;
        load(0);
    }

    /** Opens the current song at `startMillis`, with a fresh token. A retry is the same listen. */
    private void load(final int startMillis) {
        release();
        ready = false;
        preparing = true;
        failed = false;
        started = false;
        lastPosition = startMillis;
        final int attempt = ++generation;
        final JSONObject track = current();
        listener.onPlayerChanged();
        background.execute(new Runnable() {
            @Override public void run() {
                try {
                    final String token = api.bearer();
                    final String url = api.streamUrl(track.optString("id"));
                    main.post(new Runnable() {
                        @Override public void run() { if (attempt == generation) prepare(url, token, attempt, startMillis); }
                    });
                } catch (final Exception error) {
                    Log.e("IndieHouseLeap", "Playback setup failed", error);
                    main.post(new Runnable() {
                        @Override public void run() { if (attempt == generation) fail(error instanceof IndieApi.ApiError ? error.getMessage() : null); }
                    });
                }
            }
        });
    }

    /**
     * MediaPlayer keeps the token it opened the stream with, so a reconnect after
     * the 15-minute access token expires is refused; a dropped connection fails
     * the same way. Either gets two more tries per song, from where it stopped.
     */
    private void recover() {
        if (!started && flacSuspect) {
            // 320 kbps failed too: the network, not FLAC, was the problem.
            flacSuspect = false;
            api.setFlacUnsupported(false);
        } else if (!started && lastPosition == 0 && retries == 0 && "lossless".equals(api.streamQuality())) {
            // A lossless song that fails before it starts may be FLAC this
            // runtime cannot decode (the Leap runs Android apps on BlackBerry
            // 10). Try 320 kbps; if that plays, lossless stays off here until
            // the listener chooses it again.
            flacSuspect = true;
            api.setFlacUnsupported(true);
            load(0);
            return;
        }
        if (retries >= 2) { fail(null); return; }
        retries++;
        final int attempt = ++generation;
        release();
        preparing = true;
        listener.onPlayerChanged();
        main.postDelayed(new Runnable() {
            @Override public void run() { if (attempt == generation) load(lastPosition); }
        }, retries * 1500L);
    }

    private void prepare(String url, String token, final int attempt, final int startMillis) {
        media = new MediaPlayer();
        media.setAudioStreamType(AudioManager.STREAM_MUSIC);
        media.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer player) {
                if (attempt != generation) return;
                if (startMillis <= 0) { begin(player); return; }
                // Start only after the seek, so a resumed song does not play a moment from the start.
                player.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                    @Override public void onSeekComplete(MediaPlayer seeked) {
                        seeked.setOnSeekCompleteListener(null);
                        if (attempt == generation) begin(seeked);
                    }
                });
                player.seekTo(startMillis);
            }
        });
        media.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer player) {
                if (attempt != generation) return;
                // When the connection drops, this MediaPlayer can report the end of
                // the song instead of an error.
                if (lastPosition + 10000 < duration()) { recover(); return; }
                logListen(true);
                main.removeCallbacks(tick);
                if (repeat == REPEAT_ONE) {
                    logged = false;
                    playbackId = UUID.randomUUID().toString();
                    player.seekTo(0);
                    player.start();
                    main.post(tick);
                    listener.onPlayerChanged();
                } else if (autoplay || repeat == REPEAT_ALL) advance();
                else listener.onPlayerChanged();
            }
        });
        media.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer player, int what, int extra) {
                if (attempt == generation) recover();
                return true;
            }
        });
        try {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "Bearer " + token);
            media.setDataSource(context, Uri.parse(url), headers);
            media.prepareAsync();
        } catch (Exception error) {
            Log.e("IndieHouseLeap", "Playback prepare failed", error);
            fail(null);
        }
    }

    private void begin(MediaPlayer player) {
        ready = true;
        preparing = false;
        started = true;
        if (flacSuspect) {
            flacSuspect = false;
            listener.onPlayerNotice(context.getString(R.string.lossless_unsupported));
        }
        player.start();
        main.removeCallbacks(tick);
        main.post(tick);
        listener.onPlayerChanged();
    }

    private void fail(String message) {
        release();
        preparing = false;
        failed = true;
        listener.onPlayerError(message != null ? message : context.getString(R.string.play_failed_network));
        listener.onPlayerChanged();
    }

    private void release() {
        main.removeCallbacks(tick);
        ready = false;
        if (media != null) {
            try { media.release(); } catch (RuntimeException ignored) { }
            media = null;
        }
    }

    /** One log per playback, after ten seconds or at the end, as the web player does. */
    private void logListen(boolean completed) {
        final JSONObject track = current();
        if (track == null || media == null || !ready || logged) return;
        final int seconds = completed ? Math.max(1, duration() / 1000) : position() / 1000;
        if (seconds < (completed ? 1 : 10)) return;
        logged = true;
        final String id = playbackId;
        background.execute(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("track_id", track.optString("id"));
                    payload.put("played_duration_seconds", seconds);
                    payload.put("completed", completed);
                    payload.put("playback_id", id);
                    api.request("POST", "play-logs", payload);
                } catch (Exception ignored) { /* History is best effort, as on the web. */ }
            }
        });
    }
}
