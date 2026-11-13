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

package rkr.simplekeyboard.inputmethod.latin.settings;


package rkr.simplekeyboard.inputmethod.latin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.clipboard.ClipboardHistory;
import rkr.simplekeyboard.inputmethod.latin.clipboard.ClipboardPanelView;
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils;

public final class InputView extends FrameLayout
        implements ClipboardHistory.Listener, ClipboardPanelView.Callback {
    private View mToolbar;
    private ImageView mClipboardButton;
    private ClipboardPanelView mPanel;
    private View mKeyboardView;
    private LatinIME mLatinIME;
    private ClipboardHistory mHistory;

    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
    }

    /** Connects the clipboard toolbar and panel. Call once after inflating. */
    public void setupClipboard(final LatinIME latinIME, final ClipboardHistory history) {
        mLatinIME = latinIME;
        mHistory = history;
        mToolbar = findViewById(R.id.clipboard_toolbar);
        mClipboardButton = findViewById(R.id.clipboard_button);
        mPanel = findViewById(R.id.clipboard_panel);
        mKeyboardView = findViewById(R.id.keyboard_view);
        if (mToolbar == null || mPanel == null) {
            return;
        }
        mPanel.setup(history, this);
        mClipboardButton.setOnClickListener(v -> showPanel());
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
        final int foreground = ResourceUtils.isBrightColor(background) ? 0xFF202124 : 0xFFFFFFFF;
        mToolbar.setBackgroundColor(background);
        mClipboardButton.setColorFilter(foreground, PorterDuff.Mode.SRC_IN);
        mPanel.setColors(background, foreground);
    }

    private void showPanel() {
        if (mPanel == null) {
            return;
        }
        syncLook();
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
