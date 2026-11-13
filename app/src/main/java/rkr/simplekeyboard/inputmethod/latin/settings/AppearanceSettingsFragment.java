/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2024 wittmane
 *
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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.widget.Toast;
import android.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme;

/**
 * "Appearance" settings sub screen.
 */
public final class AppearanceSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_appearance);

        setupKeyboardHeightSettings();
        setupBottomOffsetPortraitSettings();
        setupKeyboardColorSettings();
        setupLetterColorSettings();
        setupBackgroundImageSettings();
    }

    private static final int REQUEST_PICK_BACKGROUND = 4711;
    private static final int MAX_BACKGROUND_SIDE = 1600;

    private void setupLetterColorSettings() {
        final LetterColorDialogPreference pref = (LetterColorDialogPreference)findPreference(
                Settings.PREF_LETTER_COLOR);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        pref.setInterface(new ColorDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.hasLetterColor(prefs) ? Settings.readLetterColor(prefs)
                        : Color.WHITE;
            }

            @Override
            public void writeDefaultValue(final String key) {
                Settings.removeLetterColor(prefs);
            }
        });
    }

    private void setupBackgroundImageSettings() {
        final Preference pref = findPreference(Settings.SCREEN_BACKGROUND_IMAGE);
        if (pref == null) {
            return;
        }
        pref.setOnPreferenceClickListener(preference -> {
            final boolean hasImage = Settings.hasBackgroundImage(getSharedPreferences());
            final CharSequence[] items = hasImage
                    ? new CharSequence[] {getString(R.string.background_image_choose),
                            getString(R.string.background_image_remove)}
                    : new CharSequence[] {getString(R.string.background_image_choose)};
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.background_image)
                    .setItems(items, (dialog, which) -> {
                        if (which == 0) {
                            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                            intent.setType("image/*");
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            startActivityForResult(intent, REQUEST_PICK_BACKGROUND);
                        } else {
                            removeBackgroundImage();
                        }
                    })
                    .show();
            return true;
        });
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_BACKGROUND || resultCode != android.app.Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        if (!saveBackgroundImage(data.getData())) {
            Toast.makeText(getActivity(), R.string.background_image_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Decodes the picked picture at a sensible size and stores it in the app's private files. */
    private boolean saveBackgroundImage(final Uri uri) {
        final Context context = getActivity().getApplicationContext();
        try {
            final BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return false;
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_BACKGROUND_SIDE) {
                sample *= 2;
            }
            final BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bitmap == null) {
                return false;
            }
            // Respect the photo's rotation.
            int degrees = 0;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                final int orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            } catch (final Exception ignored) {
                // No EXIF data: keep the picture as it is.
            }
            if (degrees != 0) {
                final Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                        matrix, true);
            }
            final File file = new File(context.getFilesDir(), Settings.BACKGROUND_IMAGE_FILE);
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            bitmap.recycle();
            final int version = Math.max(1, (int) (System.currentTimeMillis() / 1000L));
            getSharedPreferences().edit()
                    .putInt(Settings.PREF_BACKGROUND_IMAGE_VERSION, version).apply();
            return true;
        } catch (final Exception | OutOfMemoryError e) {
            return false;
        }
    }

    private void removeBackgroundImage() {
        final Context context = getActivity().getApplicationContext();
        new File(context.getFilesDir(), Settings.BACKGROUND_IMAGE_FILE).delete();
        getSharedPreferences().edit().remove(Settings.PREF_BACKGROUND_IMAGE_VERSION).apply();
    }

    @Override
    public void onResume() {
        super.onResume();

        ThemeSettingsFragment.updateKeyboardThemeSummary(findPreference(Settings.SCREEN_THEME));

        final Preference colorPreference = findPreference(Settings.PREF_KEYBOARD_COLOR);
        if (colorPreference.isEnabled()) {
            final SharedPreferences prefs = getSharedPreferences();
            final KeyboardTheme theme = KeyboardTheme.getKeyboardTheme(prefs);
            colorPreference.setEnabled(theme.mCustomColorSupport);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (KeyboardTheme.KEYBOARD_THEME_KEY.equals(key)) {
            ThemeSettingsFragment.updateKeyboardThemeSummary(findPreference(Settings.SCREEN_THEME));

            final KeyboardTheme theme = KeyboardTheme.getKeyboardTheme(prefs);
            setPreferenceEnabled(Settings.PREF_KEYBOARD_COLOR, theme.mCustomColorSupport);
        }
    }

    private void setupKeyboardHeightSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEYBOARD_HEIGHT);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            private static final float PERCENTAGE_FLOAT = 100.0f;

            private float getValueFromPercentage(final int percentage) {
                return percentage / PERCENTAGE_FLOAT;
            }

            private int getPercentageFromValue(final float floatValue) {
                return Math.round(floatValue * PERCENTAGE_FLOAT);
            }

            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(Settings.readKeyboardHeight(prefs, 1));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(1);
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return res.getString(R.string.abbreviation_unit_percent, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    private void setupBottomOffsetPortraitSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_BOTTOM_OFFSET_PORTRAIT);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readBottomOffsetPortrait(prefs);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.DEFAULT_BOTTOM_OFFSET;
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return res.getString(R.string.abbreviation_unit_dp, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    private void setupKeyboardColorSettings() {
        final ColorDialogPreference pref = (ColorDialogPreference)findPreference(
                Settings.PREF_KEYBOARD_COLOR);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Context context = this.getActivity().getApplicationContext();
        pref.setInterface(new ColorDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeyboardColor(prefs, context);
            }

            @Override
            public void writeDefaultValue(final String key) {
                Settings.removeKeyboardColor(prefs);
            }
        });
    }
}
