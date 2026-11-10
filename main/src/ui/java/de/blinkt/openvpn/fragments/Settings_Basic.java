/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.R.id;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.Totp;
import de.blinkt.openvpn.views.FileSelectLayout;

public class Settings_Basic extends KeyChainSettingsFragment implements OnItemSelectedListener, FileSelectLayout.FileSelectCallback, CompoundButton.OnCheckedChangeListener {
    private static final int CHOOSE_FILE_OFFSET = 1000;

    private FileSelectLayout mClientCert;
    private FileSelectLayout mCaCert;
    private FileSelectLayout mClientKey;
    private CheckBox mUseLzo;
    private CheckBox mUseLegacyProvider;
    private Spinner mType;
    private Spinner mCompatMode;
    private FileSelectLayout mpkcs12;
    private FileSelectLayout mCrlFile;
    private TextView mPKCS12Password;
    private EditText mUserName;
    private EditText mPassword;
    private View mView;
    private EditText mProfileName;
    private EditText mKeyPassword;
    private CheckBox mEnablePeerFingerprint;
    private EditText mPeerFingerprints;

    private SparseArray<FileSelectLayout> fileselects = new SparseArray<>();
    private Spinner mAuthRetry;

    private CheckBox mUseTotp;
    private EditText mTotpSecret;
    private EditText mTotpDigits;
    private EditText mTotpPeriod;
    private Spinner mTotpAlgorithm;
    private Spinner mTotpMode;
    private TextView mTotpPreview;
    private final Handler mTotpHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTotpTicker = new Runnable() {
        @Override
        public void run() {
            updateTotpPreview();
            mTotpHandler.postDelayed(this, 1000);
        }
    };


    private void addFileSelectLayout(FileSelectLayout fsl, Utils.FileType type) {
        int i = fileselects.size() + CHOOSE_FILE_OFFSET;
        fileselects.put(i, fsl);
        fsl.setCaller(this, i, type);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        mView = inflater.inflate(R.layout.basic_settings, container, false);

        mProfileName = mView.findViewById(id.profilename);
        mClientCert = mView.findViewById(id.certselect);
        mClientKey = mView.findViewById(id.keyselect);
        mCaCert = mView.findViewById(id.caselect);
        mpkcs12 = mView.findViewById(id.pkcs12select);
        mCrlFile = mView.findViewById(id.crlfile);
        mUseLzo = mView.findViewById(id.lzo);
        mUseLegacyProvider = mView.findViewById(R.id.legacyprovider);
        mType = mView.findViewById(id.type);
        mCompatMode = mView.findViewById(id.compatmode);
        mPKCS12Password = mView.findViewById(id.pkcs12password);
        mEnablePeerFingerprint = mView.findViewById(id.enable_peer_fingerprint);
        mPeerFingerprints = mView.findViewById(id.peer_fingerprint);

        mUserName = mView.findViewById(id.auth_username);
        mPassword = mView.findViewById(id.auth_password);
        mKeyPassword = mView.findViewById(id.key_password);
        mAuthRetry = mView.findViewById(id.auth_retry);

        mUseTotp = mView.findViewById(id.use_totp);
        mTotpSecret = mView.findViewById(id.totp_secret);
        mTotpDigits = mView.findViewById(id.totp_digits);
        mTotpPeriod = mView.findViewById(id.totp_period);
        mTotpAlgorithm = mView.findViewById(id.totp_algorithm);
        mTotpMode = mView.findViewById(id.totp_mode);
        mTotpPreview = mView.findViewById(id.totp_preview);
        mUseTotp.setOnCheckedChangeListener(this);
        mTotpAlgorithm.setOnItemSelectedListener(this);
        TextWatcher totpWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateTotpPreview();
            }
        };
        mTotpSecret.addTextChangedListener(totpWatcher);
        mTotpDigits.addTextChangedListener(totpWatcher);
        mTotpPeriod.addTextChangedListener(totpWatcher);

        addFileSelectLayout(mCaCert, Utils.FileType.CA_CERTIFICATE);
        addFileSelectLayout(mClientCert, Utils.FileType.CLIENT_CERTIFICATE);
        addFileSelectLayout(mClientKey, Utils.FileType.KEYFILE);
        addFileSelectLayout(mpkcs12, Utils.FileType.PKCS12);
        addFileSelectLayout(mCrlFile, Utils.FileType.CRL_FILE);
        mCaCert.setShowClear();
        mCrlFile.setShowClear();

        mType.setOnItemSelectedListener(this);
        mAuthRetry.setOnItemSelectedListener(this);
        mEnablePeerFingerprint.setOnCheckedChangeListener(this);

        initKeychainViews(mView);

        return mView;
    }


    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result == Activity.RESULT_OK && request >= CHOOSE_FILE_OFFSET) {
            FileSelectLayout fsl = fileselects.get(request);
            fsl.parseResponse(data, getActivity());

            savePreferences();

            // Private key files may result in showing/hiding the private key password dialog
            if (fsl == mClientKey) {
                changeType(mType.getSelectedItemPosition());
            }
        }

    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mType) {
            changeType(position);
        } else if (parent == mTotpAlgorithm) {
            updateTotpPreview();
        }
    }


    private void changeType(int type) {
        // hide everything
        mView.findViewById(R.id.pkcs12).setVisibility(View.GONE);
        mView.findViewById(R.id.certs).setVisibility(View.GONE);
        mView.findViewById(R.id.statickeys).setVisibility(View.GONE);
        mView.findViewById(R.id.keystore).setVisibility(View.GONE);
        mView.findViewById(R.id.cacert).setVisibility(View.GONE);
        ((FileSelectLayout) mView.findViewById(R.id.caselect)).setClearable(false);
        mView.findViewById(R.id.userpassword).setVisibility(View.GONE);
        mView.findViewById(R.id.key_password_layout).setVisibility(View.GONE);
        mView.findViewById(R.id.external_auth).setVisibility(View.GONE);
        mView.findViewById(R.id.crlfile).setVisibility(View.VISIBLE);



        // Fall through are by design
        switch (type) {
            case VpnProfile.TYPE_USERPASS_CERTIFICATES:
                mView.findViewById(R.id.userpassword).setVisibility(View.VISIBLE);
            case VpnProfile.TYPE_CERTIFICATES:
                mView.findViewById(R.id.certs).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.cacert).setVisibility(View.VISIBLE);
                if (mProfile.requireTLSKeyPassword())
                    mView.findViewById(R.id.key_password_layout).setVisibility(View.VISIBLE);
                break;

            case VpnProfile.TYPE_USERPASS_PKCS12:
                mView.findViewById(R.id.userpassword).setVisibility(View.VISIBLE);
            case VpnProfile.TYPE_PKCS12:
                mView.findViewById(R.id.pkcs12).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.cacert).setVisibility(View.VISIBLE);
                ((FileSelectLayout) mView.findViewById(R.id.caselect)).setClearable(true);
                break;

            case VpnProfile.TYPE_STATICKEYS:
                mView.findViewById(R.id.statickeys).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.crlfile).setVisibility(View.GONE);
                break;

            case VpnProfile.TYPE_USERPASS_KEYSTORE:
                mView.findViewById(R.id.userpassword).setVisibility(View.VISIBLE);
            case VpnProfile.TYPE_KEYSTORE:
                mView.findViewById(R.id.keystore).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.cacert).setVisibility(View.VISIBLE);
                ((FileSelectLayout) mView.findViewById(R.id.caselect)).setClearable(true);
                break;

            case VpnProfile.TYPE_USERPASS:
                mView.findViewById(R.id.userpassword).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.cacert).setVisibility(View.VISIBLE);
                break;
            case VpnProfile.TYPE_EXTERNAL_APP:
                mView.findViewById(R.id.external_auth).setVisibility(View.VISIBLE);
                break;
        }


    }

    protected void loadPreferences() {
        super.loadPreferences();
        mProfileName.setText(mProfile.mName);
        mClientCert.setData(mProfile.mClientCertFilename, getActivity());
        mClientKey.setData(mProfile.mClientKeyFilename, getActivity());
        mCaCert.setData(mProfile.mCaFilename, getActivity());
        mCrlFile.setData(mProfile.mCrlFilename, getActivity());

        mUseLzo.setChecked(mProfile.mUseLzo);
        mUseLegacyProvider.setChecked(mProfile.mUseLegacyProvider);
        mType.setSelection(mProfile.mAuthenticationType);
        mCompatMode.setSelection(Utils.mapCompatVer(mProfile.mCompatMode));
        mpkcs12.setData(mProfile.mPKCS12Filename, getActivity());
        mPKCS12Password.setText(mProfile.mPKCS12Password);
        mUserName.setText(mProfile.mUsername);
        mPassword.setText(mProfile.mPassword);
        mKeyPassword.setText(mProfile.mKeyPassword);
        mAuthRetry.setSelection(mProfile.mAuthRetry);
        mEnablePeerFingerprint.setChecked(mProfile.mCheckPeerFingerprint);
        mPeerFingerprints.setText(mProfile.mPeerFingerPrints);

        mUseTotp.setChecked(mProfile.mUseTotp);
        mTotpSecret.setText(mProfile.mTotpSecret);
        mTotpDigits.setText(mProfile.mTotpDigits > 0 ? Integer.toString(mProfile.mTotpDigits) : "");
        mTotpPeriod.setText(mProfile.mTotpPeriod > 0 ? Integer.toString(mProfile.mTotpPeriod) : "");
        int algoIdx = 0;
        String[] algos = getResources().getStringArray(R.array.totp_algorithms);
        for (int i = 0; i < algos.length; i++)
            if (algos[i].equalsIgnoreCase(mProfile.mTotpAlgorithm))
                algoIdx = i;
        mTotpAlgorithm.setSelection(algoIdx);
        mTotpMode.setSelection(mProfile.mTotpMode);
        mView.findViewById(id.totp_settings).setVisibility(mProfile.mUseTotp ? View.VISIBLE : View.GONE);
    }

    private static int parseIntOr(EditText et, int def) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void updateTotpPreview() {
        if (mTotpPreview == null || !isAdded())
            return;
        String secret = mTotpSecret.getText().toString();
        if (!mUseTotp.isChecked() || TextUtils.isEmpty(secret.trim())) {
            mTotpPreview.setText("");
            return;
        }
        try {
            Totp totp = Totp.fromSettings(secret,
                    parseIntOr(mTotpDigits, Totp.DEFAULT_DIGITS),
                    parseIntOr(mTotpPeriod, Totp.DEFAULT_PERIOD),
                    (String) mTotpAlgorithm.getSelectedItem());
            long now = System.currentTimeMillis();
            mTotpPreview.setText(getString(R.string.totp_preview, totp.generate(now), totp.secondsRemaining(now)));
        } catch (RuntimeException e) {
            mTotpPreview.setText(getString(R.string.totp_invalid, e.getMessage()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mTotpHandler.post(mTotpTicker);
    }

    @Override
    public void onPause() {
        mTotpHandler.removeCallbacks(mTotpTicker);
        super.onPause();
    }

    protected void savePreferences() {
        super.savePreferences();
        mProfile.mName = mProfileName.getText().toString();
        mProfile.mCaFilename = mCaCert.getData();
        mProfile.mClientCertFilename = mClientCert.getData();
        mProfile.mClientKeyFilename = mClientKey.getData();
        mProfile.mCrlFilename = mCrlFile.getData();

        mProfile.mUseLzo = mUseLzo.isChecked();
        mProfile.mUseLegacyProvider = mUseLegacyProvider.isChecked();
        mProfile.mAuthenticationType = mType.getSelectedItemPosition();
        mProfile.mPKCS12Filename = mpkcs12.getData();
        mProfile.mPKCS12Password = mPKCS12Password.getText().toString();

        mProfile.mPassword = mPassword.getText().toString();
        mProfile.mUsername = mUserName.getText().toString();
        mProfile.mKeyPassword = mKeyPassword.getText().toString();
        mProfile.mAuthRetry = mAuthRetry.getSelectedItemPosition();
        mProfile.mCheckPeerFingerprint = mEnablePeerFingerprint.isChecked();
        mProfile.mPeerFingerPrints = mPeerFingerprints.getText().toString();
        mProfile.mCompatMode = Utils.mapCompatMode(mCompatMode.getSelectedItemPosition());

        mProfile.mUseTotp = mUseTotp.isChecked();
        mProfile.mTotpSecret = mTotpSecret.getText().toString().trim();
        mProfile.mTotpDigits = parseIntOr(mTotpDigits, Totp.DEFAULT_DIGITS);
        mProfile.mTotpPeriod = parseIntOr(mTotpPeriod, Totp.DEFAULT_PERIOD);
        mProfile.mTotpAlgorithm = (String) mTotpAlgorithm.getSelectedItem();
        mProfile.mTotpMode = mTotpMode.getSelectedItemPosition();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        savePreferences();
        if (mProfile != null) {
            outState.putString(getActivity().getPackageName() + "profileUUID", mProfile.getUUID().toString());
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }


    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == mEnablePeerFingerprint)
        {
            mPeerFingerprints.setVisibility(isChecked ? View.VISIBLE : View.GONE);

        }
        else if (buttonView == mUseTotp)
        {
            mView.findViewById(id.totp_settings).setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateTotpPreview();
        }
    }
}
