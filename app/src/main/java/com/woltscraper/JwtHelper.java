package com.woltscraper;

import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;

public class JwtHelper {
    private static final String TAG = "JwtHelper";

    public static String buildServiceAccountJwt(String serviceAccountEmail, String privateKeyPem, String scope, long iat, long exp) {
        try {
            JSONObject header = new JSONObject();
            header.put("alg", "RS256"); header.put("typ", "JWT");
            JSONObject claims = new JSONObject();
            claims.put("iss", serviceAccountEmail); claims.put("scope", scope);
            claims.put("aud", "https://oauth2.googleapis.com/token");
            claims.put("iat", iat); claims.put("exp", exp);
            String encodedHeader = base64UrlEncode(header.toString().getBytes("UTF-8"));
            String encodedClaims = base64UrlEncode(claims.toString().getBytes("UTF-8"));
            String signingInput = encodedHeader + "." + encodedClaims;
            PrivateKey privateKey = loadPrivateKey(privateKeyPem);
            if (privateKey == null) return null;
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey); sig.update(signingInput.getBytes("UTF-8"));
            return signingInput + "." + base64UrlEncode(sig.sign());
        } catch (Exception e) { Log.e(TAG, "JWT build failed", e); return null; }
    }

    private static PrivateKey loadPrivateKey(String pem) {
        try {
            String cleaned = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("-----BEGIN RSA PRIVATE KEY-----", "").replace("-----END RSA PRIVATE KEY-----", "").replaceAll("\\s+", "");
            byte[] keyBytes = Base64.decode(cleaned, Base64.DEFAULT);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) { Log.e(TAG, "Failed to load private key", e); return null; }
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
