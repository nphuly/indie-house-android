package house.indie.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

final class IndieApi {
    static final class ApiError extends Exception {
        final int status;
        ApiError(String message, int status) { super(message); this.status = status; }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final SecureSession session;
    private InstanceProfile profile;
    private String access;
    private String refresh;
    private long expiresAt;
    private boolean verified;
    private String quality = "auto";
    /** This device could not decode FLAC; lossless then streams 320 kbps. */
    private boolean flacUnsupported;

    IndieApi(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("indie_house_native", Context.MODE_PRIVATE);
        session = new SecureSession(context);
        String savedQuality = prefs.getString("audio_quality", "auto");
        if (knownQuality(savedQuality)) quality = savedQuality;
        flacUnsupported = prefs.getBoolean("flac_unsupported", false);
        try {
            String saved = prefs.getString("profile", null);
            if (saved != null) {
                profile = InstanceProfile.restore(this.context, saved);
                String tokens = session.load(profile.id);
                if (tokens != null) readTokens(new JSONObject(tokens));
            }
        } catch (Exception ignored) {
            session.clear();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                prefs.edit().remove("profile").apply();
            }
            profile = null;
        }
    }

    InstanceProfile profile() { return profile; }
    String quality() { return quality; }
    boolean hasSession() { return refresh != null; }

    /** What the stream asks for: lossless on a device that cannot play FLAC asks for 320 kbps. */
    String streamQuality() { return "lossless".equals(quality) && flacUnsupported ? "high" : quality; }

    void setFlacUnsupported(boolean value) {
        flacUnsupported = value;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            prefs.edit().putBoolean("flac_unsupported", value).apply();
        }
    }

    private static boolean knownQuality(String value) {
        return value.equals("auto") || value.equals("standard") || value.equals("high") || value.equals("lossless");
    }

    void setQuality(String value) {
        if (!knownQuality(value)) return;
        quality = value;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            prefs.edit().putString("audio_quality", value).apply();
        }
    }

    InstanceProfile discover(String address) throws Exception {
        String url = InstanceProfile.manifestUrl(context, address);
        JSONObject data = send("GET", url, null, null);
        return InstanceProfile.parse(context, url, data);
    }

    void trust(InstanceProfile candidate) throws Exception {
        if (profile != null && profile.origin.equals(candidate.origin) && !profile.id.equals(candidate.id)) {
            throw new ApiError(context.getString(R.string.api_identity_changed), 0);
        }
        if (profile == null || !profile.id.equals(candidate.id)) session.clear();
        profile = candidate;
        access = refresh = null;
        verified = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            prefs.edit().putString("profile", candidate.save().toString()).apply();
        }
    }

    void verify() throws Exception {
        if (profile == null) throw new ApiError(context.getString(R.string.api_connect_first), 0);
        InstanceProfile latest = discover(profile.origin);
        if (!latest.id.equals(profile.id) || !latest.origin.equals(profile.origin)) {
            verified = false;
            throw new ApiError(context.getString(R.string.api_identity_mismatch), 0);
        }
        verified = true;
    }

    void login(String email, String password) throws Exception {
        verify();
        JSONObject payload = new JSONObject();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("device_name", "BlackBerry Leap");
        payload.put("platform", "blackberry_leap");
        saveTokens(send("POST", apiUrl("auth/token"), payload, null));
    }

    void redeem(String invite, String name, String email, String password) throws Exception {
        verify();
        JSONObject payload = new JSONObject();
        payload.put("invite_code", invite);
        payload.put("name", name);
        payload.put("email", email);
        payload.put("password", password);
        payload.put("password_confirmation", password);
        send("POST", apiUrl("invites/redeem"), payload, null);
        login(email, password);
    }

    private void readTokens(JSONObject tokens) throws Exception {
        access = tokens.getString("access_token");
        refresh = tokens.getString("refresh_token");
        expiresAt = tokens.getLong("expires_at");
    }

    private void saveTokens(JSONObject tokens) throws Exception {
        JSONObject stored = new JSONObject();
        stored.put("access_token", tokens.getString("access_token"));
        stored.put("refresh_token", tokens.getString("refresh_token"));
        stored.put("expires_at", System.currentTimeMillis() + tokens.getLong("expires_in") * 1000L);
        readTokens(stored);
        session.save(profile.id, stored.toString());
    }

    private synchronized void refreshToken() throws Exception {
        if (!verified || refresh == null) throw new ApiError(context.getString(R.string.api_login_again), 401);
        JSONObject payload = new JSONObject();
        payload.put("refresh_token", refresh);
        payload.put("device_name", "BlackBerry Leap");
        payload.put("platform", "blackberry_leap");
        try {
            saveTokens(send("POST", apiUrl("auth/refresh"), payload, null));
        } catch (Exception error) {
            clearSession();
            throw error;
        }
    }

    synchronized String bearer() throws Exception {
        if (!verified || refresh == null) throw new ApiError(context.getString(R.string.api_login_again), 401);
        if (System.currentTimeMillis() >= expiresAt - 30000L) refreshToken();
        return access;
    }

    JSONObject request(String method, String path, JSONObject payload) throws Exception {
        String token = bearer();
        try {
            return send(method, apiUrl(path), payload, token);
        } catch (ApiError error) {
            if (error.status != 401) throw error;
            refreshToken();
            return send(method, apiUrl(path), payload, bearer());
        }
    }

    byte[] artwork(String rawUrl) throws Exception {
        URL url = new URL(rawUrl);
        String origin = "https://" + url.getHost().toLowerCase();
        if (profile == null || !profile.origin.equals(origin) || !"https".equals(url.getProtocol())
                || url.getPort() != -1 && url.getPort() != 443 || url.getUserInfo() != null
                || url.getQuery() != null || url.getRef() != null
                || !url.getPath().matches("/api/v1/mobile/((tracks|artists|albums)/[0-9a-zA-Z-]{1,64}/artwork|account/avatar/[0-9a-f]{12})")) {
            throw new ApiError(context.getString(R.string.api_artwork_bad_url), 0);
        }
        byte[] result = artworkRequest(url, bearer());
        if (result != null) return result;
        refreshToken();
        return artworkRequest(url, bearer());
    }

    private byte[] artworkRequest(URL url, String token) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        LegacyTls.configure(connection, context);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "image/jpeg, image/png, image/webp, image/gif");
        try {
            int status = connection.getResponseCode();
            if (status == 401) return null;
            if (status != 200) throw new ApiError(context.getString(R.string.api_artwork_missing), status);
            String type = connection.getContentType();
            if (type == null || !(type.startsWith("image/jpeg") || type.startsWith("image/png")
                    || type.startsWith("image/webp") || type.startsWith("image/gif"))) {
                throw new ApiError(context.getString(R.string.api_artwork_invalid), 0);
            }
            try (InputStream stream = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    if (output.size() + count > 4_000_000)
                        throw new ApiError(context.getString(R.string.api_artwork_too_large), 0);
                    output.write(chunk, 0, count);
                }
                return output.toByteArray();
            }
        } finally { connection.disconnect(); }
    }

    /** The terms of use or privacy policy. Public, so the invitation form can show it before sign-in. */
    JSONObject legal(String document) throws Exception {
        if (!"terms".equals(document) && !"privacy".equals(document)) {
            throw new IllegalArgumentException(context.getString(R.string.api_bad_path));
        }
        return send("GET", apiUrl("legal/" + document), null, null).getJSONObject("data");
    }

    String streamUrl(String trackId) {
        if (profile == null || !trackId.matches("[0-9a-zA-Z-]{1,64}")) throw new IllegalArgumentException(context.getString(R.string.api_bad_track));
        return profile.origin + "/api/v1/stream/v1/tracks/" + trackId + "/stream?quality=" + streamQuality();
    }

    void logout() {
        String oldRefresh = refresh;
        clearSession();
        if (oldRefresh != null && profile != null && verified) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("refresh_token", oldRefresh);
                send("POST", apiUrl("auth/logout"), payload, null);
            } catch (Exception ignored) { }
        }
    }

    void disconnect() {
        logout();
        profile = null;
        verified = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            prefs.edit().remove("profile").apply();
        }
    }

    private void clearSession() {
        access = refresh = null;
        expiresAt = 0;
        session.clear();
    }

    void forgetSession() {
        clearSession();
        verified = false;
    }

    private String apiUrl(String path) {
        if (profile == null || path.startsWith("/") || path.contains("..") || path.contains(":") || path.contains("//")) {
            throw new IllegalArgumentException(context.getString(R.string.api_bad_path));
        }
        return profile.origin + "/api/v1/mobile/" + path;
    }

    private JSONObject send(String method, String rawUrl, JSONObject payload, String bearer) throws Exception {
        URL url = new URL(rawUrl);
        if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null || url.getPort() != -1 && url.getPort() != 443) {
            throw new ApiError(context.getString(R.string.api_https_required), 0);
        }
        if (profile != null && !profile.origin.equals("https://" + url.getHost().toLowerCase())) {
            throw new ApiError(context.getString(R.string.api_origin_mismatch), 0);
        }
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        LegacyTls.configure(connection, context);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setRequestMethod("PATCH".equals(method) ? "POST" : method);
        if ("PATCH".equals(method)) connection.setRequestProperty("X-HTTP-METHOD-OVERRIDE", "PATCH");
        connection.setRequestProperty("Accept", "application/json");
        // Server messages (sign-in errors, rate limits) come back in the app's language.
        connection.setRequestProperty("Accept-Language", Locales.current(context));
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        if (payload != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = payload.toString().getBytes("UTF-8");
            OutputStream output = connection.getOutputStream();
            try { output.write(bytes); } finally { output.close(); }
        }
        try {
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) throw new ApiError(context.getString(R.string.api_redirect), status);
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = read(stream);
            if (status < 200 || status >= 300) {
                String message = context.getString(R.string.api_failed, status);
                try { message = new JSONObject(body).optString("message", message); } catch (Exception ignored) { }
                throw new ApiError(message, status);
            }
            return body.length() == 0 ? new JSONObject() : new JSONObject(body);
        } finally {
            connection.disconnect();
        }
    }

    private String read(InputStream input) throws Exception {
        if (input == null) return "";
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (output.size() + count > 2_000_000) throw new ApiError(context.getString(R.string.api_too_large), 0);
                output.write(chunk, 0, count);
            }
            return output.toString("UTF-8");
        } finally { input.close(); }
    }
}
