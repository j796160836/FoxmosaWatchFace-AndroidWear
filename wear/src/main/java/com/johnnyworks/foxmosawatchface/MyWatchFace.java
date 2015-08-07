
package com.johnnyworks.foxmosawatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
	private static final Typeface NORMAL_TYPEFACE =
			Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

	/**
	 * Update rate in milliseconds for interactive mode. We update once a second since seconds are
	 * displayed in interactive mode.
	 */
	private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

	/**
	 * Handler message id for updating the time periodically in interactive mode.
	 */
	private static final int MSG_UPDATE_TIME = 0;
	private static final String TAG = MyWatchFace.class.getSimpleName();

	private Engine watchEngine;

	@Override
	public Engine onCreateEngine() {
		watchEngine = new Engine();
		return watchEngine;
	}

	private class Engine extends CanvasWatchFaceService.Engine {
		final Handler mUpdateTimeHandler = new EngineHandler(this);

		final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mTime.clear(intent.getStringExtra("time-zone"));
				mTime.setToNow();
			}
		};

		final BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context arg0, Intent intent) {
				batterylevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				updateTimer();
			}
		};

		boolean mRegisteredTimeZoneReceiver = false;

		Paint mPaint;
		Paint mBackgroundPaint;
		Paint mTextClockPaint;
		Paint mTextBatteryPaint;

		boolean mAmbient;

		Time mTime;

		float mYOffset;

		float mBatteryXOffset;
		float mBatteryYOffset;

		/**
		 * Whether the display supports fewer bits for each color in ambient mode. When true, we
		 * disable anti-aliasing in ambient mode.
		 */
		boolean mLowBitAmbient;

		Bitmap watchFaceBitmap;
		Bitmap watchFaceBitmapAmbient;

		int batterylevel = -1;

		@Override
		public void onCreate(SurfaceHolder holder) {
			super.onCreate(holder);

			setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
					.setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
					.setViewProtectionMode(WatchFaceStyle.PROTECT_HOTWORD_INDICATOR)
					.setStatusBarGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)
					.setHotwordIndicatorGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
					.setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
					.setShowSystemUiTime(false)
					.build());
			Resources resources = MyWatchFace.this.getResources();
			mYOffset = resources.getDimension(R.dimen.clock_y_offset);

			mPaint = new Paint();
			mPaint.setColor(Color.CYAN);

			mBackgroundPaint = new Paint();
			mBackgroundPaint.setColor(resources.getColor(R.color.clock_background));

			mTextClockPaint = new Paint();
			mTextClockPaint = createTextPaint(resources.getColor(R.color.clock_text));
			mTextClockPaint.setShadowLayer(5.0f, 3.0f, 3.0f, Color.BLACK);

			mTextBatteryPaint = new Paint();
			mTextBatteryPaint = createTextPaint(resources.getColor(R.color.clock_text));

			mTime = new Time();

			watchFaceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg_round);
			watchFaceBitmapAmbient = BitmapFactory.decodeResource(getResources(), R.drawable.bg_round_ambient);

			MyWatchFace.this.registerReceiver(mBatteryInfoReceiver, new IntentFilter(
					Intent.ACTION_BATTERY_CHANGED));
		}

		@Override
		public void onDestroy() {
			MyWatchFace.this.unregisterReceiver(mBatteryInfoReceiver);
			mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
			super.onDestroy();
		}

		private Paint createTextPaint(int textColor) {
			Paint paint = new Paint();
			paint.setColor(textColor);
			paint.setTypeface(NORMAL_TYPEFACE);
			paint.setAntiAlias(true);
			return paint;
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);

			if (visible) {
				registerReceiver();

				// Update time zone in case it changed while we weren't visible.
				mTime.clear(TimeZone.getDefault().getID());
				mTime.setToNow();
			} else {
				unregisterReceiver();
			}

			// Whether the timer should be running depends on whether we're visible (as well as
			// whether we're in ambient mode), so we may need to start or stop the timer.
			updateTimer();
		}

		private void registerReceiver() {
			if (mRegisteredTimeZoneReceiver) {
				return;
			}
			mRegisteredTimeZoneReceiver = true;
			IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
			MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
		}

		private void unregisterReceiver() {
			if (!mRegisteredTimeZoneReceiver) {
				return;
			}
			mRegisteredTimeZoneReceiver = false;
			MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
		}

		@Override
		public void onApplyWindowInsets(WindowInsets insets) {
			super.onApplyWindowInsets(insets);

			// Load resources that have alternate values for round watches.
			Resources resources = MyWatchFace.this.getResources();
			boolean isRound = insets.isRound();
			mYOffset = resources.getDimension(isRound ? R.dimen.clock_y_offset_round : R.dimen.clock_y_offset);

			mBatteryXOffset = resources.getDimension(isRound
					? R.dimen.battery_x_offset_round : R.dimen.battery_x_offset);
			mBatteryYOffset = resources.getDimension(isRound
					? R.dimen.battery_y_offset_round : R.dimen.battery_y_offset);

			float textClockSize = resources.getDimension(isRound
					? R.dimen.clock_text_size_round : R.dimen.clock_text_size);
			float textBatterySize = resources.getDimension(isRound ? R.dimen.clock_text_size_battey_round :
					R.dimen.clock_text_size_battey);

			mTextClockPaint.setTextSize(textClockSize);
			mTextBatteryPaint.setTextSize(textBatterySize);
			Log.v(TAG, "onApplyWindowInsets");

			watchFaceBitmap = BitmapFactory.decodeResource(getResources(), isRound ? R.drawable.bg_round : R.drawable.bg_rect);
			watchFaceBitmapAmbient = BitmapFactory.decodeResource(getResources(), isRound ? R.drawable.bg_round_ambient : R.drawable.bg_rect_ambient);
		}

		@Override
		public void onPropertiesChanged(Bundle properties) {
			super.onPropertiesChanged(properties);
			mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
		}

		@Override
		public void onTimeTick() {
			super.onTimeTick();
			invalidate();
		}

		@Override
		public void onAmbientModeChanged(boolean inAmbientMode) {
			super.onAmbientModeChanged(inAmbientMode);
			if (mAmbient != inAmbientMode) {
				mAmbient = inAmbientMode;
				if (mLowBitAmbient) {
					mTextClockPaint.setAntiAlias(!inAmbientMode);
					mTextBatteryPaint.setAntiAlias(!inAmbientMode);
					if (!inAmbientMode) {
						mTextClockPaint.setShadowLayer(5.0f, 3.0f, 3.0f, Color.parseColor("#cc000000"));
					} else {
						mTextClockPaint.setShadowLayer(0.001f, 3.0f, 3.0f, Color.BLACK);
					}
				}
				invalidate();
			}

			// Whether the timer should be running depends on whether we're visible (as well as
			// whether we're in ambient mode), so we may need to start or stop the timer.
			updateTimer();
		}

		@Override
		public void onDraw(Canvas canvas, Rect bounds) {
			canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
			float bitmapWidth = canvas.getHeight();
			float bitmapHeight = watchFaceBitmap.getHeight() * canvas.getWidth() / watchFaceBitmap.getWidth();
			canvas.drawBitmap(mAmbient ? watchFaceBitmapAmbient : watchFaceBitmap, null, new RectF(0, 0, bitmapWidth, bitmapHeight), null);
			mTime.setToNow();
			String text = String.format("%02d:%02d", mTime.hour, mTime.minute);
			String batteryText = "---";
			if (batterylevel >= 0) {
				batteryText = String.format("%d%%", batterylevel);
			}

			RectF areaRectClock = new RectF(0, mYOffset, canvas.getWidth(), canvas.getHeight());
			RectF areaRectBattery = new RectF(
					canvas.getWidth() * 3 / 4 - mBatteryXOffset
					, canvas.getHeight() * 3 / 4 - mBatteryYOffset
					, canvas.getWidth() - mBatteryXOffset
					, canvas.getHeight() - mBatteryYOffset);

			RectF textClockBounds = getMeasuredTextRect(areaRectClock, text, mTextClockPaint);
			RectF textBatteryBounds = getMeasuredTextRect(areaRectBattery, text, mTextBatteryPaint);

			canvas.drawText(batteryText
					, textBatteryBounds.left
					, textBatteryBounds.top - mTextBatteryPaint.ascent()
					, mTextBatteryPaint);

			canvas.drawText(text
					, textClockBounds.left
					, textClockBounds.top - mTextClockPaint.ascent()
					, mTextClockPaint);
		}

		private RectF getMeasuredTextRect(RectF areaRect, String text, Paint textPaint) {
			RectF bounds = new RectF(areaRect);
			// measure text width
			bounds.right = textPaint.measureText(text, 0, text.length());
			// measure text height
			bounds.bottom = textPaint.descent() - textPaint.ascent();

			bounds.left += (areaRect.width() - bounds.right) / 2.0f;
			bounds.top += (areaRect.height() - bounds.bottom) / 2.0f;
			return bounds;
		}

		/**
		 * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
		 * or stops it if it shouldn't be running but currently is.
		 */
		private void updateTimer() {
			mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
			if (shouldTimerBeRunning()) {
				mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
			}
		}

		/**
		 * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
		 * only run when we're visible and in interactive mode.
		 */
		private boolean shouldTimerBeRunning() {
			return isVisible() && !isInAmbientMode();
		}

		/**
		 * Handle updating the time periodically in interactive mode.
		 */
		private void handleUpdateTimeMessage() {
			invalidate();
			if (shouldTimerBeRunning()) {
				long timeMs = System.currentTimeMillis();
				long delayMs = INTERACTIVE_UPDATE_RATE_MS
						- (timeMs % INTERACTIVE_UPDATE_RATE_MS);
				mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
			}
		}
	}

	private static class EngineHandler extends Handler {
		private final WeakReference<MyWatchFace.Engine> mWeakReference;

		public EngineHandler(MyWatchFace.Engine reference) {
			mWeakReference = new WeakReference<>(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			MyWatchFace.Engine engine = mWeakReference.get();
			if (engine != null) {
				switch (msg.what) {
					case MSG_UPDATE_TIME:
						engine.handleUpdateTimeMessage();
						break;
				}
			}
		}
	}
}
