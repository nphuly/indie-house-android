package house.indie.legacy;

import android.content.Context;
import android.net.Uri;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.regex.Pattern;

final class InstanceProfile {
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    final String id;
    final String name;
    final String origin;
    final String mode;

    private InstanceProfile(String id, String name, String origin, String mode) {
        this.id = id;
        this.name = name;
        this.origin = origin;
        this.mode = mode;
    }

    static String manifestUrl(Context context, String input) {
        Uri uri = Uri.parse(input.trim());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null ||
                (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException(context.getString(R.string.profile_enter_address));
        }
        String path = uri.getPath();
        if (!"".equals(path) && !"/".equals(path) && !"/.well-known/indie-house".equals(path)) {
            throw new IllegalArgumentException(context.getString(R.string.profile_not_qr));
        }
        return "https://" + uri.getHost().toLowerCase() + "/.well-known/indie-house";
    }

    static InstanceProfile parse(Context context, String manifestUrl, JSONObject json) throws JSONException {
        String origin = Uri.parse(manifestUrl).buildUpon().path("").build().toString();
        if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
        String id = json.getString("instance_id");
        String name = json.getString("display_name");
        String mode = json.getString("registration_mode");
        String version = json.getString("minimum_mobile_version");
        if (json.getInt("schema_version") != 1 || !UUID.matcher(id).matches() ||
                name.trim().length() == 0 ||
                !("closed".equals(mode) || "invite_only".equals(mode) || "open".equals(mode)) ||
                !supports(version)) {
            throw new IllegalArgumentException(context.getString(R.string.profile_invalid));
        }
        if (!origin.equals(json.getString("jwt_issuer")) ||
                !origin.concat("/api/v1/mobile").equals(json.getString("api_base")) ||
                !origin.concat("/api/v1/stream").equals(json.getString("stream_base"))) {
            throw new IllegalArgumentException(context.getString(R.string.profile_origin));
        }
        return new InstanceProfile(id, name, origin, mode);
    }

    private static boolean supports(String version) {
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        return major < 1 || major == 1 && minor == 0 && patch == 0;
    }

    JSONObject save() throws JSONException {
        JSONObject result = new JSONObject();
        result.put("id", id);
        result.put("name", name);
        result.put("origin", origin);
        result.put("mode", mode);
        return result;
    }

    static InstanceProfile restore(Context context, String data) throws JSONException {
        JSONObject json = new JSONObject(data);
        String origin = json.getString("origin");
        String url = manifestUrl(context, origin);
        JSONObject manifest = new JSONObject();
        manifest.put("schema_version", 1);
        manifest.put("instance_id", json.getString("id"));
        manifest.put("display_name", json.getString("name"));
        manifest.put("jwt_issuer", origin);
        manifest.put("api_base", origin + "/api/v1/mobile");
        manifest.put("stream_base", origin + "/api/v1/stream");
        manifest.put("registration_mode", json.getString("mode"));
        manifest.put("minimum_mobile_version", "1.0.0");
        return parse(context, url, manifest);
    }
}
