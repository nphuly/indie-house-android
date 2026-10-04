package house.indie.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Calendar;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/** API 18-compatible encrypted token storage. If BlackBerry's keystore fails, sign-in lasts this process only. */
final class SecureSession {
    private static final String ALIAS = "indie_house_leap_session";
    private final Context context;
    private final SharedPreferences prefs;

    SecureSession(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("indie_house_native", Context.MODE_PRIVATE);
    }

    private KeyStore keyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            Calendar from = Calendar.getInstance();
            Calendar until = Calendar.getInstance();
            until.add(Calendar.YEAR, 20);
            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(ALIAS).setSubject(new X500Principal("CN=Indie House Leap"))
                    .setSerialNumber(java.math.BigInteger.ONE)
                    .setStartDate(from.getTime()).setEndDate(until.getTime()).build();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(spec);
            generator.generateKeyPair();
        }
        return store;
    }

    void save(String instanceId, String json) {
        try {
            KeyStore store = keyStore();
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey aes = generator.generateKey();
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            Cipher body = Cipher.getInstance("AES/CBC/PKCS5Padding");
            body.init(Cipher.ENCRYPT_MODE, aes, new IvParameterSpec(iv));
            byte[] encrypted = body.doFinal(json.getBytes("UTF-8"));
            byte[] mac = authenticate(aes.getEncoded(), iv, encrypted);
            PublicKey publicKey = store.getCertificate(ALIAS).getPublicKey();
            Cipher wrapping = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            wrapping.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] wrapped = wrapping.doFinal(aes.getEncoded());
            prefs.edit().putString("session_id", instanceId)
                    .putString("session_key", Base64.encodeToString(wrapped, Base64.NO_WRAP))
                    .putString("session_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString("session_mac", Base64.encodeToString(mac, Base64.NO_WRAP))
                    .putString("session_body", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) {
            clear();
        }
    }

    String load(String instanceId) {
        if (!instanceId.equals(prefs.getString("session_id", ""))) return null;
        try {
            KeyStore store = keyStore();
            PrivateKey privateKey = (PrivateKey) store.getKey(ALIAS, null);
            Cipher wrapping = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            wrapping.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] key = wrapping.doFinal(Base64.decode(prefs.getString("session_key", ""), Base64.DEFAULT));
            byte[] iv = Base64.decode(prefs.getString("session_iv", ""), Base64.DEFAULT);
            byte[] encrypted = Base64.decode(prefs.getString("session_body", ""), Base64.DEFAULT);
            byte[] expected = authenticate(key, iv, encrypted);
            byte[] actual = Base64.decode(prefs.getString("session_mac", ""), Base64.DEFAULT);
            if (!java.security.MessageDigest.isEqual(expected, actual)) throw new SecurityException("Invalid session data");
            Cipher body = Cipher.getInstance("AES/CBC/PKCS5Padding");
            body.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));
            return new String(body.doFinal(encrypted), "UTF-8");
        } catch (Exception ignored) {
            clear();
            return null;
        }
    }

    void clear() {
        prefs.edit().remove("session_id").remove("session_key")
                .remove("session_iv").remove("session_mac").remove("session_body").apply();
    }

    private byte[] authenticate(byte[] key, byte[] iv, byte[] encrypted) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        digest.update(key);
        byte[] macKey = digest.digest("Indie House Leap MAC".getBytes("UTF-8"));
        hmac.init(new SecretKeySpec(macKey, "HmacSHA256"));
        hmac.update(iv);
        return hmac.doFinal(encrypted);
    }
}
