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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rkr.simplekeyboard.inputmethod.R;

/**
 * Lets the user pick the colour of the key letters: type a hex code, tap a preset colour, or drag
 * the point on the colour picker.
 */
public final class LetterColorDialogPreference extends DialogPreference {
    private static final int[] PRESETS = {
            0xFFFFFFFF, 0xFF000000, 0xFFF44336, 0xFFFF9800, 0xFFFFEB3B, 0xFF8BC34A, 0xFF4CAF50,
            0xFF009688, 0xFF00BCD4, 0xFF2196F3, 0xFF3F51B5, 0xFF9C27B0, 0xFFE91E63, 0xFF9E9E9E,
    };
    private static final int PRESETS_PER_ROW = 7;

    private ColorDialogPreference.ValueProxy mValueProxy;
    private ColorPickerView mPicker;
    private EditText mHexEdit;
    private TextView mPreview;
    private int mColor = Color.WHITE;
    private boolean mUpdating;

    public LetterColorDialogPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public void setInterface(final ColorDialogPreference.ValueProxy proxy) {
        mValueProxy = proxy;
    }

    private int dp(final int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    @Override
    protected View onCreateDialogView() {
        final Context context = getContext();
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(4));

        mPreview = new TextView(context);
        mPreview.setGravity(Gravity.CENTER);
        mPreview.setTextSize(22);
        mPreview.setText(R.string.letter_color_preview_text);
        mPreview.setBackgroundColor(0xFF444444);
        mPreview.setPadding(dp(8), dp(14), dp(8), dp(14));
        root.addView(mPreview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mHexEdit = new EditText(context);
        mHexEdit.setHint(R.string.letter_color_hex_hint);
        mHexEdit.setSingleLine(true);
        mHexEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mHexEdit.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(7),
                (source, start, end, dest, dstart, dend) -> {
                    final StringBuilder sb = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        final char c = source.charAt(i);
                        if (Character.digit(c, 16) >= 0 || c == '#') {
                            sb.append(Character.toUpperCase(c));
                        }
                    }
                    return sb.length() == end - start ? null : sb.toString();
                }});
        mHexEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(final Editable s) {
                if (mUpdating) return;
                final Integer parsed = parseHex(s.toString());
                if (parsed != null) {
                    applyColor(parsed, false, true);
                }
            }
        });
        root.addView(mHexEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Preset list.
        LinearLayout row = null;
        for (int i = 0; i < PRESETS.length; i++) {
            if (i % PRESETS_PER_ROW == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                final LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.topMargin = dp(10);
                root.addView(row, rp);
            }
            final int preset = PRESETS[i];
            final View swatch = new View(context);
            final GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(preset);
            circle.setStroke(dp(1), 0x66888888);
            swatch.setBackground(circle);
            swatch.setOnClickListener(v -> applyColor(preset, true, true));
            final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(34), dp(34));
            sp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(swatch, sp);
        }

        mPicker = new ColorPickerView(context);
        mPicker.setOnColorChangedListener((color, fromUser) -> applyColor(color, true, false));
        final LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.topMargin = dp(14);
        root.addView(mPicker, pp);

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        return scroll;
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);
        applyColor(mValueProxy.readValue(getKey()) | 0xFF000000, true, true);
    }

    @Override
    protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
        builder.setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setNeutralButton(R.string.button_default, this);
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        super.onClick(dialog, which);
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mValueProxy.writeValue(mColor | 0xFF000000, getKey());
        } else if (which == DialogInterface.BUTTON_NEUTRAL) {
            mValueProxy.writeDefaultValue(getKey());
        }
    }

    private void applyColor(final int color, final boolean updateHex, final boolean updatePicker) {
        mColor = color | 0xFF000000;
        mUpdating = true;
        if (updatePicker) {
            mPicker.setColor(mColor);
        }
        if (updateHex) {
            final String hex = String.format("#%06X", mColor & 0xFFFFFF);
            mHexEdit.setText(hex);
            mHexEdit.setSelection(hex.length());
        }
        mUpdating = false;
        mPreview.setTextColor(mColor);
        // Show the sample on a background where the colour stays readable.
        final boolean bright = Color.red(mColor) * 299 + Color.green(mColor) * 587
                + Color.blue(mColor) * 114 > 128 * 1000;
        mPreview.setBackgroundColor(bright ? 0xFF222222 : 0xFFEEEEEE);
    }

    private static Integer parseHex(final String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
