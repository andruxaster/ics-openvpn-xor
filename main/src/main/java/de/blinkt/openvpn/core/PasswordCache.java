/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import java.util.UUID;

/**
 * Created by arne on 15.12.16.
 */

public class PasswordCache {
    public static final int PKCS12ORCERTPASSWORD = 2;
    public static final int AUTHPASSWORD = 3;
    public static final int CHALLENGERESPONSE = 4;
    private static PasswordCache mInstance;
    final private UUID mUuid;
    private String mKeyOrPkcs12Password;
    private String mAuthPassword;
    private String mChallengeResponse;

    private PasswordCache(UUID uuid) {
        mUuid = uuid;
    }

    public static PasswordCache getInstance(UUID uuid) {
        if (mInstance == null || !mInstance.mUuid.equals(uuid)) {
            mInstance = new PasswordCache(uuid);
        }
        return mInstance;
    }

    public static String getPKCS12orCertificatePassword(UUID uuid, boolean resetPw) {
        String pwcopy = getInstance(uuid).mKeyOrPkcs12Password;
        if (resetPw)
            getInstance(uuid).mKeyOrPkcs12Password = null;
        return pwcopy;
    }


    public static String getAuthPassword(UUID uuid, boolean resetPW) {
        String pwcopy = getInstance(uuid).mAuthPassword;
        if (resetPW)
            getInstance(uuid).mAuthPassword = null;
        return pwcopy;
    }

    /** Response to a static-challenge (e.g. a TOTP code) entered by the user, used only once */
    public static String getChallengeResponse(UUID uuid, boolean reset) {
        String copy = getInstance(uuid).mChallengeResponse;
        if (reset)
            getInstance(uuid).mChallengeResponse = null;
        return copy;
    }

    public static void setCachedPassword(String uuid, int type, String password) {
        PasswordCache instance = getInstance(UUID.fromString(uuid));
        switch (type) {
            case PKCS12ORCERTPASSWORD:
                instance.mKeyOrPkcs12Password = password;
                break;
            case AUTHPASSWORD:
                instance.mAuthPassword = password;
                break;
            case CHALLENGERESPONSE:
                instance.mChallengeResponse = password;
                break;
        }
    }


}
