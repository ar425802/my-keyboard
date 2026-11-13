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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import rkr.simplekeyboard.inputmethod.R;

/**
 * Panel that covers the keyboard and lists the copied text. Tap an entry to type it.
 */
public final class ClipboardPanelView extends LinearLayout {
    public interface Callback {
        void onClipboardTextSelected(String text);
        void onClipboardPanelClosed();
    }

    private final TextView mTitle;
    private final TextView mClearAll;
    private final TextView mClose;
    private final LinearLayout mList;
    private final ScrollView mScroll;
    private Callback mCallback;
    private ClipboardHistory mHistory;
    private int mBackgroundColor = 0xFFECEFF1;
    private int mTextColor = 0xFF202124;

    public ClipboardPanelView(final Context context,
            final android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setClickable(true); // swallow touches so they don't reach the keys underneath

        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(2), dp(6), dp(2));

        mTitle = new TextView(context);
        mTitle.setText(R.string.clipboard_title);
        mTitle.setTextSize(16);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(mTitle, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        mClearAll = makeHeaderButton(context, R.string.clipboard_clear_all);
        mClearAll.setOnClickListener(v -> {
            if (mHistory != null) mHistory.clear();
        });
        header.addView(mClearAll);

        mClose = makeHeaderButton(context, R.string.clipboard_close);
        mClose.setOnClickListener(v -> {
            if (mCallback != null) mCallback.onClipboardPanelClosed();
        });
        header.addView(mClose);

        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(36)));

        mScroll = new ScrollView(context);
        mList = new LinearLayout(context);
        mList.setOrientation(VERTICAL);
        mList.setPadding(dp(10), dp(4), dp(10), dp(8));
        mScroll.addView(mList);
        addView(mScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private TextView makeHeaderButton(final Context context, final int textRes) {
        final TextView button = new TextView(context);
        button.setText(textRes);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public void setup(final ClipboardHistory history, final Callback callback) {
        mHistory = history;
        mCallback = callback;
    }

    /** Matches the panel to the keyboard so it looks like part of it. */
    public void setColors(final int backgroundColor, final int textColor) {
        mBackgroundColor = backgroundColor;
        mTextColor = textColor;
        setBackgroundColor(backgroundColor);
        mTitle.setTextColor(textColor);
        mClearAll.setTextColor(textColor);
        mClose.setTextColor(textColor);
    }

    public void refresh() {
        mList.removeAllViews();
        final List<ClipboardHistory.Entry> entries =
                mHistory == null ? null : mHistory.getEntries();
        if (entries == null || entries.isEmpty()) {
            final TextView empty = new TextView(getContext());
            empty.setText(R.string.clipboard_empty);
            empty.setTextColor(mTextColor);
            empty.setAlpha(0.7f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(20), dp(16), dp(12));
            mList.addView(empty);
            mClearAll.setVisibility(View.INVISIBLE);
            return;
        }
        mClearAll.setVisibility(View.VISIBLE);
        for (final ClipboardHistory.Entry entry : entries) {
            mList.addView(makeRow(entry));
        }
    }

    private View makeRow(final ClipboardHistory.Entry entry) {
        final Context context = getContext();
        final String text = entry.text;
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(10));
        shape.setColor(Color.argb(entry.pinned ? 56 : 30, Color.red(mTextColor),
                Color.green(mTextColor), Color.blue(mTextColor)));
        row.setBackground(shape);
        row.setPadding(dp(12), dp(2), dp(2), dp(2));
        final LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(5);
        row.setLayoutParams(rowParams);

        final TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(mTextColor);
        label.setTextSize(15);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, dp(7), dp(6), dp(7));
        row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        // Pin button: a filled pin means the entry stays until you unpin it.
        final ImageView pin = new ImageView(context);
        pin.setImageResource(R.drawable.ic_pin);
        pin.setColorFilter(mTextColor, android.graphics.PorterDuff.Mode.SRC_IN);
        pin.setAlpha(entry.pinned ? 1.0f : 0.35f);
        pin.setPadding(dp(9), dp(9), dp(9), dp(9));
        pin.setContentDescription(context.getString(R.string.clipboard_pin));
        pin.setOnClickListener(v -> {
            if (mHistory != null) mHistory.togglePin(text);
        });
        row.addView(pin, new LayoutParams(dp(40), dp(40)));

        final TextView delete = new TextView(context);
        delete.setText("\u2715");
        delete.setTextColor(mTextColor);
        delete.setAlpha(0.7f);
        delete.setTextSize(15);
        delete.setGravity(Gravity.CENTER);
        delete.setOnClickListener(v -> {
            if (mHistory != null) mHistory.remove(text);
        });
        row.addView(delete, new LayoutParams(dp(40), dp(40)));

        row.setOnClickListener(v -> {
            if (mCallback != null) mCallback.onClipboardTextSelected(text);
        });
        return row;
    }
}
