/*
 * Copyright (c) 2012-2026 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import java.net.URI;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time password generator (RFC 6238 / RFC 4226).
 * <p>
 * Pure Java (no Android dependencies) so it can be unit tested on the JVM.
 * Accepts either a plain Base32 secret (as shown by Google Authenticator,
 * FreeOTP, privacyIDEA, ...) or a full {@code otpauth://totp/...} URI.
 */
public final class Totp {

    public static final int DEFAULT_DIGITS = 6;
    public static final int DEFAULT_PERIOD = 30;
    public static final String DEFAULT_ALGORITHM = "SHA1";

    private final byte[] mKey;
    private final int mDigits;
    private final int mPeriod;
    private final String mAlgorithm;

    public Totp(byte[] key, int digits, int period, String algorithm) {
        if (key == null || key.length == 0)
            throw new IllegalArgumentException("Empty TOTP secret");
        if (digits < 6 || digits > 10)
            throw new IllegalArgumentException("TOTP digits must be between 6 and 10");
        if (period <= 0)
            throw new IllegalArgumentException("TOTP period must be positive");
        mKey = key.clone();
        mDigits = digits;
        mPeriod = period;
        mAlgorithm = normaliseAlgorithm(algorithm);
    }

    /**
     * Builds a generator from profile settings. If {@code secret} is an otpauth:// URI,
     * the parameters contained in it override {@code digits}, {@code period} and {@code algorithm}.
     */
    public static Totp fromSettings(String secret, int digits, int period, String algorithm) {
        if (secret == null)
            throw new IllegalArgumentException("Empty TOTP secret");
        secret = secret.trim();
        if (digits <= 0)
            digits = DEFAULT_DIGITS;
        if (period <= 0)
            period = DEFAULT_PERIOD;

        if (secret.regionMatches(true, 0, "otpauth://", 0, 10)) {
            URI uri = URI.create(secret);
            if (!"totp".equalsIgnoreCase(uri.getHost()))
                throw new IllegalArgumentException("Only otpauth://totp/ URIs are supported");
            String query = uri.getRawQuery();
            String b32 = null;
            if (query != null) {
                for (String kv : query.split("&")) {
                    int eq = kv.indexOf('=');
                    if (eq < 0)
                        continue;
                    String k = kv.substring(0, eq).toLowerCase(Locale.ROOT);
                    String v = urlDecode(kv.substring(eq + 1));
                    switch (k) {
                        case "secret":
                            b32 = v;
                            break;
                        case "digits":
                            digits = Integer.parseInt(v);
                            break;
                        case "period":
                            period = Integer.parseInt(v);
                            break;
                        case "algorithm":
                            algorithm = v;
                            break;
                    }
                }
            }
            if (b32 == null)
                throw new IllegalArgumentException("otpauth URI without secret");
            secret = b32;
        }
        return new Totp(base32Decode(secret), digits, period, algorithm);
    }

    /** Checks the settings without throwing; returns an error message or null if fine. */
    public static String validate(String secret, int digits, int period, String algorithm) {
        try {
            fromSettings(secret, digits, period, algorithm).generate(System.currentTimeMillis());
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    public String generate(long timeMillis) {
        long counter = Math.floorDiv(timeMillis / 1000L, mPeriod);
        return hotp(counter);
    }

    public String now() {
        return generate(System.currentTimeMillis());
    }

    /** Seconds until the current code expires. */
    public int secondsRemaining(long timeMillis) {
        long sec = timeMillis / 1000L;
        return (int) (mPeriod - Math.floorMod(sec, mPeriod));
    }

    String hotp(long counter) {
        byte[] msg = new byte[8];
        for (int i = 7; i >= 0; i--) {
            msg[i] = (byte) (counter & 0xff);
            counter >>>= 8;
        }
        byte[] hash;
        try {
            Mac mac = Mac.getInstance("Hmac" + mAlgorithm);
            mac.init(new SecretKeySpec(mKey, "RAW"));
            hash = mac.doFinal(msg);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-" + mAlgorithm + " not available", e);
        }
        int offset = hash[hash.length - 1] & 0x0f;
        long binary = ((long) (hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        long mod = 1;
        for (int i = 0; i < mDigits; i++)
            mod *= 10;
        StringBuilder code = new StringBuilder(Long.toString(binary % mod));
        while (code.length() < mDigits)
            code.insert(0, '0');
        return code.toString();
    }

    // URLDecoder.decode(String, Charset) needs API 33, use the String variant
    private static String urlDecode(String v) {
        try {
            return URLDecoder.decode(v, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String normaliseAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isEmpty())
            return DEFAULT_ALGORITHM;
        String a = algorithm.toUpperCase(Locale.ROOT).replace("-", "");
        switch (a) {
            case "SHA1":
            case "SHA256":
            case "SHA512":
                return a;
            default:
                throw new IllegalArgumentException("Unsupported TOTP algorithm " + algorithm);
        }
    }

    /** RFC 4648 Base32 decoding, tolerant to spaces, dashes, lower case and missing padding. */
    public static byte[] base32Decode(String input) {
        String s = input.replaceAll("[\\s\\-=]", "").toUpperCase(Locale.ROOT);
        if (s.isEmpty())
            throw new IllegalArgumentException("Empty TOTP secret");
        byte[] out = new byte[s.length() * 5 / 8];
        int buffer = 0, bits = 0, pos = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int val;
            if (c >= 'A' && c <= 'Z')
                val = c - 'A';
            else if (c >= '2' && c <= '7')
                val = c - '2' + 26;
            else
                throw new IllegalArgumentException("Invalid Base32 character '" + c + "' in TOTP secret");
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out[pos++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        if (pos != out.length) {
            byte[] trimmed = new byte[pos];
            System.arraycopy(out, 0, trimmed, 0, pos);
            return trimmed;
        }
        return out;
    }
}
