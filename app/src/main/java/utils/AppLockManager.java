package utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.biometric.BiometricManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Stores the PIN (as a salted hash) and the lock state. */
public class AppLockManager {

    private static final String PREFS = "splitlog_lock_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final String KEY_BIOMETRIC = "biometric";
    private static final String KEY_FAILS = "fails";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";

    public static final int PIN_LENGTH = 4;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 30_000L;

    private static boolean locked = false;            // is the app currently locked?
    private static boolean lockScreenShowing = false; // is the unlock screen already open?

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- State ----------------
    public static boolean isEnabled(Context c) { return prefs(c).getBoolean(KEY_ENABLED, false); }
    public static boolean isLocked() { return locked; }
    public static void setLocked(boolean value) { locked = value; }
    public static boolean isLockScreenShowing() { return lockScreenShowing; }
    public static void setLockScreenShowing(boolean value) { lockScreenShowing = value; }

    // ---------------- PIN ----------------
    public static void setPin(Context c, String pin) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP);
        prefs(c).edit()
                .putString(KEY_SALT, salt)
                .putString(KEY_HASH, hash(salt, pin))
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_FAILS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0)
                .apply();
    }

    public static boolean checkPin(Context c, String pin) {
        SharedPreferences p = prefs(c);
        String salt = p.getString(KEY_SALT, null);
        String saved = p.getString(KEY_HASH, null);
        if (salt == null || saved == null) return false;
        return MessageDigest.isEqual(
                hash(salt, pin).getBytes(StandardCharsets.UTF_8),
                saved.getBytes(StandardCharsets.UTF_8));
    }

    /** Turns the lock off and erases the PIN. */
    public static void disable(Context c) {
        prefs(c).edit().clear().apply();
        locked = false;
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((salt + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------- Fingerprint ----------------
    public static boolean isBiometricEnabled(Context c) { return prefs(c).getBoolean(KEY_BIOMETRIC, false); }

    public static void setBiometricEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_BIOMETRIC, enabled).apply();
    }

    public static boolean isBiometricAvailable(Context c) {
        return BiometricManager.from(c).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    // ---------------- Wrong-PIN protection ----------------
    /** Milliseconds left in the lockout, or 0 if none. */
    public static long lockoutRemaining(Context c) {
        return Math.max(0, prefs(c).getLong(KEY_LOCKOUT_UNTIL, 0) - System.currentTimeMillis());
    }

    /** Call after a wrong PIN. Starts a lockout after MAX_ATTEMPTS wrong tries in a row. */
    public static void recordFailure(Context c) {
        SharedPreferences p = prefs(c);
        int fails = p.getInt(KEY_FAILS, 0) + 1;
        SharedPreferences.Editor e = p.edit();
        if (fails >= MAX_ATTEMPTS) {
            e.putInt(KEY_FAILS, 0);
            e.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_MS);
        } else {
            e.putInt(KEY_FAILS, fails);
        }
        e.apply();
    }

    public static void resetFailures(Context c) {
        prefs(c).edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKOUT_UNTIL, 0).apply();
    }
}