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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * A colour picker made of a saturation/brightness square with a draggable point and a hue bar
 * underneath. Drag the point (or the hue handle) to pick any colour.
 */
public final class ColorPickerView extends View {
    public interface OnColorChangedListener {
        void onColorChanged(int color, boolean fromUser);
    }

    private static final float HUE_BAR_HEIGHT_DP = 28f;
    private static final float GAP_DP = 14f;
    private static final float POINT_RADIUS_DP = 11f;

    private final float mDensity;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mSquare = new RectF();
    private final RectF mHueBar = new RectF();
    private final float[] mHsv = new float[] {0f, 1f, 1f};
    private final int[] mHueColors = new int[7];
    private OnColorChangedListener mListener;
    private boolean mDraggingSquare;
    private boolean mDraggingHue;

    public ColorPickerView(final Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < mHueColors.length; i++) {
            mHueColors[i] = Color.HSVToColor(new float[] {i * 60f, 1f, 1f});
        }
    }

    public void setOnColorChangedListener(final OnColorChangedListener listener) {
        mListener = listener;
    }

    public void setColor(final int color) {
        Color.colorToHSV(color, mHsv);
        invalidate();
    }

    public int getColor() {
        return Color.HSVToColor(mHsv);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = (int) (width * 0.62f + (GAP_DP + HUE_BAR_HEIGHT_DP) * mDensity);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        final float pad = POINT_RADIUS_DP * mDensity;
        final float hueH = HUE_BAR_HEIGHT_DP * mDensity;
        final float gap = GAP_DP * mDensity;
        mSquare.set(pad, pad, w - pad, h - hueH - gap - pad);
        mHueBar.set(pad, h - hueH - pad, w - pad, h - pad);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        final float radius = 8f * mDensity;

        // Saturation (left to right) and brightness (top to bottom) for the current hue.
        final int pureHue = Color.HSVToColor(new float[] {mHsv[0], 1f, 1f});
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(new LinearGradient(mSquare.left, 0, mSquare.right, 0,
                Color.WHITE, pureHue, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mSquare, radius, radius, mPaint);
        mPaint.setShader(new LinearGradient(0, mSquare.top, 0, mSquare.bottom,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mSquare, radius, radius, mPaint);

        // Hue bar.
        mPaint.setShader(new LinearGradient(mHueBar.left, 0, mHueBar.right, 0,
                mHueColors, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mHueBar, radius, radius, mPaint);
        mPaint.setShader(null);

        // Draggable point on the square.
        final float px = mSquare.left + mHsv[1] * mSquare.width();
        final float py = mSquare.top + (1f - mHsv[2]) * mSquare.height();
        drawHandle(canvas, px, py, getColor());

        // Handle on the hue bar.
        final float hx = mHueBar.left + (mHsv[0] / 360f) * mHueBar.width();
        drawHandle(canvas, hx, mHueBar.centerY(),
                Color.HSVToColor(new float[] {mHsv[0], 1f, 1f}));
    }

    private void drawHandle(final Canvas canvas, final float x, final float y, final int fill) {
        final float r = POINT_RADIUS_DP * mDensity;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(fill);
        canvas.drawCircle(x, y, r, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2.5f * mDensity);
        mPaint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, r, mPaint);
        mPaint.setStrokeWidth(1f * mDensity);
        mPaint.setColor(0x66000000);
        canvas.drawCircle(x, y, r + 1.5f * mDensity, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                mDraggingHue = y > mSquare.bottom;
                mDraggingSquare = !mDraggingHue;
                update(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                update(x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDraggingSquare = false;
                mDraggingHue = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void update(final float x, final float y) {
        if (mDraggingSquare) {
            mHsv[1] = clamp((x - mSquare.left) / mSquare.width());
            mHsv[2] = 1f - clamp((y - mSquare.top) / mSquare.height());
        } else if (mDraggingHue) {
            mHsv[0] = clamp((x - mHueBar.left) / mHueBar.width()) * 359.99f;
        } else {
            return;
        }
        invalidate();
        if (mListener != null) {
            mListener.onColorChanged(getColor(), true);
        }
    }

    private static float clamp(final float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
