/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package rkr.simplekeyboard.inputmethod.latin.clipboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a short history of text the user copied. Only active while the clipboard setting is on.
 * History stays on the device, in the app's private storage, and is never sent anywhere.
 */
public final class ClipboardHistory implements ClipboardManager.OnPrimaryClipChangedListener {
    private static final String TAG = "ClipboardHistory";
    private static final String PREFS_NAME = "clipboard_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 30;       // unpinned items kept
    private static final int MAX_PINNED = 30;
    private static final int MAX_ITEM_LENGTH = 20000;

    /** One copied text, which the user may have pinned so it never gets pushed out. */
    public static final class Entry {
        public final String text;
        public final boolean pinned;

        Entry(final String text, final boolean pinned) {
            this.text = text;
            this.pinned = pinned;
        }
    }

    public interface Listener {
        void onClipboardHistoryChanged();
    }

    private static ClipboardHistory sInstance;

    private final Context mContext;
    private final ClipboardManager mClipboardManager;
    private final ArrayList<String> mItems = new ArrayList<>();   // newest first
    private final ArrayList<String> mPinned = new ArrayList<>();  // pin order
    private Listener mListener;
    private boolean mRegistered;
    private boolean mLoaded;

    public static synchronized ClipboardHistory getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new ClipboardHistory(context.getApplicationContext());
        }
        return sInstance;
    }

    private ClipboardHistory(final Context context) {
        mContext = context;
        mClipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public void setListener(final Listener listener) {
        mListener = listener;
    }

    /** Starts or stops listening to the system clipboard depending on the user's setting. */
    public synchronized void setEnabled(final boolean enabled) {
        if (mClipboardManager == null) {
            return;
        }
        if (enabled && !mRegistered) {
            mClipboardManager.addPrimaryClipChangedListener(this);
            mRegistered = true;
            load();
        } else if (!enabled && mRegistered) {
            mClipboardManager.removePrimaryClipChangedListener(this);
            mRegistered = false;
        }
    }

    /** Pinned entries first, then the rest from newest to oldest. */
    public synchronized List<Entry> getEntries() {
        load();
        final ArrayList<Entry> result = new ArrayList<>();
        for (final String text : mPinned) {
            result.add(new Entry(text, true));
        }
        for (final String text : mItems) {
            if (!mPinned.contains(text)) {
                result.add(new Entry(text, false));
            }
        }
        return result;
    }

    public synchronized void togglePin(final String text) {
        if (mPinned.contains(text)) {
            mPinned.remove(text);
        } else if (mPinned.size() < MAX_PINNED) {
            mPinned.add(0, text);
            if (!mItems.contains(text)) {
                mItems.add(0, text);
            }
        }
        save();
        notifyChanged();
    }

    public synchronized void remove(final String text) {
        mItems.remove(text);
        mPinned.remove(text);
        save();
        notifyChanged();
    }

    /** Removes everything except pinned entries. */
    public synchronized void clear() {
        mItems.retainAll(mPinned);
        save();
        notifyChanged();
    }

    @Override
    public void onPrimaryClipChanged() {
        try {
            if (mClipboardManager == null || !mClipboardManager.hasPrimaryClip()) {
                return;
            }
            final ClipData clip = mClipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return;
            }
            final ClipDescription description = clip.getDescription();
            if (description == null) {
                return;
            }
            // Never keep items that the source app marked as sensitive (for example passwords).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && description.getExtras() != null
                    && description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) {
                return;
            }
            if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                    && !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
                return;
            }
            final CharSequence text = clip.getItemAt(0).getText();
            if (text == null) {
                return;
            }
            final String value = text.toString();
            if (value.trim().isEmpty() || value.length() > MAX_ITEM_LENGTH) {
                return;
            }
            add(value);
        } catch (final Exception e) {
            // Reading the clipboard can be refused by the system; just skip this change.
            Log.w(TAG, "Could not read clipboard", e);
        }
    }

    private synchronized void add(final String value) {
        load();
        mItems.remove(value);          // no duplicates: move to the top instead
        mItems.add(0, value);
        // Trim only unpinned entries.
        int unpinned = 0;
        for (int i = 0; i < mItems.size(); i++) {
            if (!mPinned.contains(mItems.get(i)) && ++unpinned > MAX_ITEMS) {
                mItems.remove(i--);
            }
        }
        save();
        notifyChanged();
    }

    private void notifyChanged() {
        final Listener listener = mListener;
        if (listener != null) {
            listener.onClipboardHistoryChanged();
        }
    }

    private boolean isStorageAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            return um == null || um.isUserUnlocked();
        }
        return true;
    }

    private void load() {
        if (mLoaded || !isStorageAvailable()) {
            return;
        }
        mLoaded = true;
        try {
            final SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME,
                    Context.MODE_PRIVATE);
            final String json = prefs.getString(KEY_ITEMS, null);
            if (json == null) {
                return;
            }
            final JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                final Object raw = array.opt(i);
                String text = null;
                boolean pinned = false;
                if (raw instanceof JSONObject) {
                    text = ((JSONObject) raw).optString("t", null);
                    pinned = ((JSONObject) raw).optBoolean("p", false);
                } else if (raw instanceof String) {
                    text = (String) raw;   // older format
                }
                if (text == null || text.isEmpty()) {
                    continue;
                }
                // Anything copied before the load finished stays on top.
                if (!mItems.contains(text)) {
                    mItems.add(text);
                }
                if (pinned && !mPinned.contains(text)) {
                    mPinned.add(text);
                }
            }
        } catch (final Exception e) {
            Log.w(TAG, "Could not load clipboard history", e);
        }
    }

    private void save() {
        if (!isStorageAvailable()) {
            return;
        }
        try {
            final JSONArray array = new JSONArray();
            for (final String item : mItems) {
                final JSONObject object = new JSONObject();
                object.put("t", item);
                object.put("p", mPinned.contains(item));
                array.put(object);
            }
            mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_ITEMS, array.toString()).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Could not save clipboard history", e);
        }
    }
}
