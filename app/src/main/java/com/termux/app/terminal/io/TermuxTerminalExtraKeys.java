package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import org.json.JSONException;

import static com.termux.shared.termux.extrakeys.ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /** Timestamp when command input lost focus, used to detect extra key button stealing focus. */
    private long mCommandInputFocusLostTime = 0;
    private static final long FOCUS_LOST_THRESHOLD_MS = 300;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
        setupCommandInputFocusTracking();
    }

    private void setupCommandInputFocusTracking() {
        EditText commandInput = mActivity.getCommandInput();
        if (commandInput != null) {
            commandInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    mCommandInputFocusLostTime = SystemClock.elapsedRealtime();
                }
            });
        }
    }

    /**
     * Set the terminal extra keys and style.
     */
    private void setExtraKeys() {
        mExtraKeysInfo = null;

        try {
            String extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Can't create default extra keys",true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    /**
     * Check if the command input EditText had focus recently
     * (extra key button click steals focus, so we use a time threshold).
     */
    private boolean shouldTargetCommandInput() {
        EditText commandInput = mActivity.getCommandInput();
        if (commandInput == null) return false;
        if (commandInput.hasFocus()) return true;
        // If focus was lost very recently, it was likely stolen by the extra key button
        long elapsed = SystemClock.elapsedRealtime() - mCommandInputFocusLostTime;
        return elapsed < FOCUS_LOST_THRESHOLD_MS;
    }

    /**
     * Dispatch a key event to the command input EditText.
     */
    private void dispatchKeyToCommandInput(String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        EditText commandInput = mActivity.getCommandInput();
        if (commandInput == null) return;

        // Re-focus the command input so cursor stays visible
        commandInput.requestFocus();

        if ("ENTER".equals(key)) {
            // ENTER in command input sends the text to the terminal
            mActivity.sendCommandInput();
            return;
        }

        if (PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(key)) {
            Integer keyCode = PRIMARY_KEY_CODES_FOR_STRINGS.get(key);
            if (keyCode == null) return;
            int metaState = 0;
            if (ctrlDown) metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            if (altDown) metaState |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
            if (shiftDown) metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
            if (fnDown) metaState |= KeyEvent.META_FUNCTION_ON;

            KeyEvent downEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState);
            KeyEvent upEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState);
            commandInput.dispatchKeyEvent(downEvent);
            commandInput.dispatchKeyEvent(upEvent);
        } else {
            // Regular text character - insert at cursor
            int start = Math.max(commandInput.getSelectionStart(), 0);
            int end = Math.max(commandInput.getSelectionEnd(), 0);
            commandInput.getText().replace(Math.min(start, end), Math.max(start, end), key);
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("TAB".equals(key)) {
            mTermuxTerminalSessionActivityClient.switchToNextSessionSorted();
        } else if ("KEYBOARD_TAB".equals(key)) {
            // Send real Tab keypress to terminal
            super.onTerminalExtraKeyButtonClick(view, "TAB", ctrlDown, altDown, shiftDown, fnDown);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        } else if ("MENU".equals(key)) {
            mActivity.getTerminalView().showContextMenu();
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else if ("IME".equals(key)) {
            // Toggle soft keyboard without changing terminal focus
            KeyboardUtils.toggleSoftKeyboard(mActivity);
        } else if (shouldTargetCommandInput()) {
            dispatchKeyToCommandInput(key, ctrlDown, altDown, shiftDown, fnDown);
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
