package edu.nyu.classes.syllabusfeed;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class HMACSession {
    private static final Logger LOG = LoggerFactory.getLogger(HMACSession.class);

    private String algorithm;

    public HMACSession(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getToken(String secret, long timestamp)
        throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        Mac mac = getMac(secret);
        mac.update(String.valueOf(timestamp).getBytes("UTF-8"));

        return String.format("%d_%s", timestamp, Base64.getUrlEncoder().encodeToString(mac.doFinal()));
    }

    public boolean tokenOk(String token, String secret, long maxAgeMs) {
        String[] parts = token.split("_", 2);

        if (parts.length != 2) {
            return false;
        }

        String timestamp = parts[0];
        String macToCheck = parts[1];

        try {
            if (Math.abs(System.currentTimeMillis() - Long.valueOf(timestamp)) > maxAgeMs) {
                // Token too old.
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        try {
            Mac mac = getMac(secret);
            mac.update(timestamp.getBytes("UTF-8"));

            return MessageDigest.isEqual(mac.doFinal(), Base64.getUrlDecoder().decode(macToCheck));
        } catch (Exception e) {
            LOG.error("Exception while checking token: " + e);
            e.printStackTrace();
            return false;
        }
    }

    private Mac getMac(String secret)
        throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        Mac mac = Mac.getInstance(this.algorithm);
        Key key = new SecretKeySpec(secret.getBytes("UTF-8"), this.algorithm);
        mac.init(key);

        return mac;
    }

}
