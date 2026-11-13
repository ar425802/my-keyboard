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



package rkr.simplekeyboard.inputmethod.latin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.clipboard.ClipboardHistory;
import rkr.simplekeyboard.inputmethod.latin.clipboard.ClipboardPanelView;
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils;

public final class InputView extends FrameLayout
        implements ClipboardHistory.Listener, ClipboardPanelView.Callback {
    private static final int MAX_CHIPS = 12;
    private static final int MAX_CHIP_CHARS = 40;
    /** The clipboard list only covers this part of the keyboard, so the top rows stay usable. */
    private static final float PANEL_HEIGHT_FRACTION = 0.62f;

    private View mToolbar;
    private ImageView mClipboardButton;
    private LinearLayout mChips;
    private ClipboardPanelView mPanel;
    private View mKeyboardView;
    private LatinIME mLatinIME;
    private ClipboardHistory mHistory;
    private int mForeground = 0xFF202124;

    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Connects the clipboard toolbar and panel. Call once after inflating. */
    public void setupClipboard(final LatinIME latinIME, final ClipboardHistory history) {
        mLatinIME = latinIME;
        mHistory = history;
        mToolbar = findViewById(R.id.clipboard_toolbar);
        mClipboardButton = findViewById(R.id.clipboard_button);
        mChips = findViewById(R.id.clipboard_chips);
        mPanel = findViewById(R.id.clipboard_panel);
        mKeyboardView = findViewById(R.id.keyboard_view);
        if (mToolbar == null || mPanel == null || mChips == null) {
            return;
        }
        mPanel.setup(history, this);
        mClipboardButton.setOnClickListener(v -> {
            if (isClipboardPanelShowing()) {
                hidePanel();
            } else {
                showPanel();
            }
        });
        history.setListener(this);
    }

    /** Shows or hides the clipboard button above the keyboard according to the user's setting. */
    public void applyClipboardSetting(final boolean enabled) {
        if (mToolbar == null) {
            return;
        }
        mToolbar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            hidePanel();
        } else {
            syncLook();
            refreshChips();
        }
    }

    public int getToolbarHeight() {
        return (mToolbar != null && mToolbar.getVisibility() == View.VISIBLE)
                ? mToolbar.getHeight() : 0;
    }

    public boolean isClipboardPanelShowing() {
        return mPanel != null && mPanel.getVisibility() == View.VISIBLE;
    }

    /** Copies the keyboard's colors so the toolbar and panel look like part of the keyboard. */
    private void syncLook() {
        int background = Color.TRANSPARENT;
        final Drawable keyboardBackground = mKeyboardView == null ? null
                : mKeyboardView.getBackground();
        if (keyboardBackground instanceof ColorDrawable) {
            background = ((ColorDrawable) keyboardBackground).getColor();
        }
        if (Color.alpha(background) < 255) {
            final boolean night = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            background = night ? 0xFF263238 : 0xFFECEFF1;
        }
        mForeground = ResourceUtils.isBrightColor(background) ? 0xFF202124 : 0xFFFFFFFF;
        mToolbar.setBackgroundColor(background);
        mClipboardButton.setColorFilter(mForeground, PorterDuff.Mode.SRC_IN);
        mPanel.setColors(background, mForeground);
    }

    /** Quick-paste strip: the newest copied texts as chips next to the clipboard button. */
    private void refreshChips() {
        if (mChips == null || mHistory == null) {
            return;
        }
        mChips.removeAllViews();
        final List<ClipboardHistory.Entry> entries = mHistory.getEntries();
        int shown = 0;
        for (final ClipboardHistory.Entry entry : entries) {
            if (shown++ >= MAX_CHIPS) {
                break;
            }
            mChips.addView(makeChip(entry));
        }
    }

    private View makeChip(final ClipboardHistory.Entry entry) {
        final String text = entry.text;
        String shown = text.replace('\n', ' ').trim();
        if (shown.length() > MAX_CHIP_CHARS) {
            shown = shown.substring(0, MAX_CHIP_CHARS);
        }
        final TextView chip = new TextView(getContext());
        chip.setText(shown);
        chip.setTextColor(mForeground);
        chip.setTextSize(13);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(dp(170));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(12), 0, dp(12), 0);
        final GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(16));
        shape.setColor(Color.argb(entry.pinned ? 64 : 34, Color.red(mForeground),
                Color.green(mForeground), Color.blue(mForeground)));
        chip.setBackground(shape);
        chip.setOnClickListener(v -> onClipboardTextSelected(text));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
        lp.setMargins(dp(3), 0, dp(3), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void showPanel() {
        if (mPanel == null) {
            return;
        }
        syncLook();
        // Cover only the lower part of the keyboard (like a half sheet), not the whole thing.
        int height = mKeyboardView == null ? 0 : mKeyboardView.getHeight();
        height = height > 0 ? Math.round(height * PANEL_HEIGHT_FRACTION) : dp(170);
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
        lp.height = height;
        lp.gravity = Gravity.BOTTOM;
        mPanel.setLayoutParams(lp);
        mPanel.refresh();
        mPanel.setVisibility(View.VISIBLE);
    }

    public void hidePanel() {
        if (mPanel != null) {
            mPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClipboardHistoryChanged() {
        post(() -> {
            refreshChips();
            if (isClipboardPanelShowing()) {
                mPanel.refresh();
            }
        });
    }

    @Override
    public void onClipboardTextSelected(final String text) {
        hidePanel();
        if (mLatinIME != null) {
            mLatinIME.onTextInput(text);
        }
    }

    @Override
    public void onClipboardPanelClosed() {
        hidePanel();
    }
}
