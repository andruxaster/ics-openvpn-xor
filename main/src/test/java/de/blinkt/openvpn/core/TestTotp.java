/*
 * Copyright (c) 2012-2026 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class TestTotp {
    // RFC 6238 Appendix B seeds
    private static final byte[] SEED20 = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEED32 = "12345678901234567890123456789012".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEED64 = "1234567890123456789012345678901234567890123456789012345678901234".getBytes(StandardCharsets.US_ASCII);

    private static final long[] TIMES = {59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L};
    private static final String[] SHA1 = {"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"};
    private static final String[] SHA256 = {"46119246", "68084774", "67062674", "91819424", "90698825", "77737706"};
    private static final String[] SHA512 = {"90693936", "25091201", "99943326", "93441116", "38618901", "47863826"};

    @Test
    public void testRfc6238Vectors() {
        Totp t1 = new Totp(SEED20, 8, 30, "SHA1");
        Totp t256 = new Totp(SEED32, 8, 30, "SHA256");
        Totp t512 = new Totp(SEED64, 8, 30, "SHA512");
        for (int i = 0; i < TIMES.length; i++) {
            Assert.assertEquals(SHA1[i], t1.generate(TIMES[i] * 1000));
            Assert.assertEquals(SHA256[i], t256.generate(TIMES[i] * 1000));
            Assert.assertEquals(SHA512[i], t512.generate(TIMES[i] * 1000));
        }
    }

    @Test
    public void testBase32AndUri() {
        // "12345678901234567890" in Base32
        String b32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        Assert.assertArrayEquals(SEED20, Totp.base32Decode(b32));
        Assert.assertArrayEquals(SEED20, Totp.base32Decode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"));

        Totp plain = Totp.fromSettings(b32, 6, 30, "SHA1");
        Assert.assertEquals("287082", plain.generate(59000));

        Totp uri = Totp.fromSettings("otpauth://totp/VPN:user?secret=" + b32
                + "&issuer=VPN&digits=8&period=30&algorithm=SHA1", 6, 60, "SHA512");
        Assert.assertEquals("94287082", uri.generate(59000));
    }

    @Test
    public void testValidation() {
        Assert.assertNotNull(Totp.validate("", 6, 30, "SHA1"));
        Assert.assertNotNull(Totp.validate("not*base32", 6, 30, "SHA1"));
        Assert.assertNotNull(Totp.validate("GEZDGNBV", 6, 30, "MD5"));
        Assert.assertNull(Totp.validate("GEZDGNBV", 0, 0, null));
    }

    @Test
    public void testSecondsRemaining() {
        Totp t = new Totp(SEED20, 6, 30, "SHA1");
        Assert.assertEquals(30, t.secondsRemaining(60_000));
        Assert.assertEquals(1, t.secondsRemaining(89_000));
    }
}
