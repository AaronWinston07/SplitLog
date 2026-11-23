package com.hisab;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import utils.AppLockManager;

/** PIN screen. Modes: unlock the app, create a new PIN, or verify the current PIN. */
public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final int MODE_UNLOCK = 0;
    public static final int MODE_SET = 1;
    public static final int MODE_VERIFY = 2;

    private int mode;
    private final StringBuilder entry = new StringBuilder();
    private String firstPin = null; // used while creating a PIN
    private View[] dots;
    private TextView tvTitle, tvError;
    private CountDownTimer lockoutTimer;
    private boolean biometricShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_UNLOCK);

        tvTitle = findViewById(R.id.tvTitle);
        tvError = findViewById(R.id.tvError);
        dots = new View[]{findViewById(R.id.dot1), findViewById(R.id.dot2),
                findViewById(R.id.dot3), findViewById(R.id.dot4)};

        if (mode == MODE_SET) tvTitle.setText(R.string.lock_create_pin);
        else if (mode == MODE_VERIFY) tvTitle.setText(R.string.lock_enter_current);
        else tvTitle.setText(R.string.lock_enter_pin);

        buildKeypad();

        if (mode == MODE_UNLOCK) {
            TextView tvForgot = findViewById(R.id.tvForgot);
            tvForgot.setVisibility(View.VISIBLE);
            tvForgot.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.lock_forgot_title)
                    .setMessage(R.string.lock_forgot_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        }

        // Back: in unlock mode just leave the app (no way around the lock)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mode == MODE_UNLOCK) {
                    moveTaskToBack(true);
                } else {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        });

        checkLockout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Offer the fingerprint automatically, once
        if (mode == MODE_UNLOCK && !biometricShown && biometricUsable()) {
            biometricShown = true;
            showBiometric();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutTimer != null) lockoutTimer.cancel();
        AppLockManager.setLockScreenShowing(false);
    }

    private boolean biometricUsable() {
        return AppLockManager.isBiometricEnabled(this) && AppLockManager.isBiometricAvailable(this);
    }

    // ---------------- Keypad ----------------
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int ringFor(int column) {
        if (column == 0) return R.drawable.bg_key_pink;
        if (column == 1) return R.drawable.bg_key_purple;
        return R.drawable.bg_key_blue;
    }

    private GridLayout.LayoutParams keyParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(72);
        lp.height = dp(72);
        lp.setMargins(dp(10), dp(8), dp(10), dp(8));
        return lp;
    }

    private void buildKeypad() {
        GridLayout keypad = findViewById(R.id.keypad);
        // "bio" = fingerprint key, "del" = backspace key
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del"};

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            int column = i % 3;

            if (key.equals("bio")) {
                ImageView img = new ImageView(this);
                img.setLayoutParams(keyParams());
                img.setPadding(dp(18), dp(18), dp(18), dp(18));
                img.setImageResource(R.drawable.ic_fingerprint);
                img.setContentDescription(getString(R.string.use_fingerprint));
                if (mode == MODE_UNLOCK && biometricUsable()) {
                    img.setClickable(true);
                    img.setFocusable(true);
                    img.setOnClickListener(v -> showBiometric());
                } else {
                    img.setVisibility(View.INVISIBLE);
                }
                keypad.addView(img);

            } else if (key.equals("del")) {
                ImageView img = new ImageView(this);
                img.setLayoutParams(keyParams());
                img.setPadding(dp(20), dp(20), dp(20), dp(20));
                img.setImageResource(R.drawable.ic_backspace);
                img.setContentDescription("Delete");
                img.setClickable(true);
                img.setFocusable(true);
                img.setOnClickListener(v -> onKey("←"));
                keypad.addView(img);

            } else {
                TextView tv = new TextView(this);
                tv.setLayoutParams(keyParams());
                tv.setText(key);
                tv.setGravity(Gravity.CENTER);
                tv.setTextColor(ContextCompat.getColor(this, R.color.white));
                tv.setTextSize(26);
                tv.setBackgroundResource(ringFor(column));
                tv.setClickable(true);
                tv.setFocusable(true);
                tv.setOnClickListener(v -> onKey(key));
                keypad.addView(tv);
            }
        }
    }

    private void onKey(String key) {
        if (AppLockManager.lockoutRemaining(this) > 0) return;

        if (key.equals("←")) {
            if (entry.length() > 0) entry.deleteCharAt(entry.length() - 1);
            tvError.setText("");
        } else if (entry.length() < AppLockManager.PIN_LENGTH) {
            entry.append(key);
        }
        updateDots();

        if (entry.length() == AppLockManager.PIN_LENGTH) {
            onPinEntered(entry.toString());
        }
    }

    private void updateDots() {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(i < entry.length()
                    ? R.drawable.bg_dot_filled : R.drawable.bg_dot_empty);
        }
    }

    private void clearEntry() {
        entry.setLength(0);
        updateDots();
    }

    // ---------------- PIN logic ----------------
    private void onPinEntered(String pin) {
        if (mode == MODE_SET) {
            if (firstPin == null) {
                firstPin = pin;
                tvTitle.setText(R.string.lock_confirm_pin);
                tvError.setText("");
                clearEntry();
            } else if (firstPin.equals(pin)) {
                AppLockManager.setPin(this, pin);
                setResult(RESULT_OK);
                finish();
            } else {
                firstPin = null;
                tvTitle.setText(R.string.lock_create_pin);
                tvError.setText(R.string.lock_mismatch);
                clearEntry();
            }
            return;
        }

        // UNLOCK or VERIFY
        if (AppLockManager.checkPin(this, pin)) {
            AppLockManager.resetFailures(this);
            if (mode == MODE_UNLOCK) {
                AppLockManager.setLocked(false);
            } else {
                setResult(RESULT_OK);
            }
            finish();
        } else {
            AppLockManager.recordFailure(this);
            tvError.setText(R.string.lock_wrong_pin);
            clearEntry();
            checkLockout();
        }
    }

    /** If too many wrong tries, show a countdown and ignore the keypad. */
    private void checkLockout() {
        long remaining = AppLockManager.lockoutRemaining(this);
        if (remaining <= 0) return;

        if (lockoutTimer != null) lockoutTimer.cancel();
        lockoutTimer = new CountDownTimer(remaining, 1000) {
            @Override public void onTick(long ms) {
                tvError.setText(getString(R.string.lock_too_many, (int) Math.ceil(ms / 1000.0)));
            }
            @Override public void onFinish() {
                tvError.setText("");
            }
        }.start();
    }

    // ---------------- Fingerprint ----------------
    private void showBiometric() {
        BiometricPrompt prompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        AppLockManager.resetFailures(LockActivity.this);
                        AppLockManager.setLocked(false);
                        finish();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setNegativeButtonText(getString(R.string.biometric_use_pin))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
        prompt.authenticate(info);
    }
}