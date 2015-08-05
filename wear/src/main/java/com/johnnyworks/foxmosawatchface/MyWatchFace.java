
package com.johnnyworks.foxmosawatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
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

	private Engine watchEngine;

	private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context arg0, Intent intent) {
			if (watchEngine != null) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				watchEngine.updateBatteryInfo(level);
			}
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();

		registerReceiver(mBatInfoReceiver, new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED));
		if (watchEngine != null) {
			watchEngine.updateBatteryInfo((int) getBatteryLevel());
		}
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mBatInfoReceiver);
		super.onDestroy();
	}

	public float getBatteryLevel() {
		Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

		// Error checking that probably isn't needed but I added just in case.
		if (level == -1 || scale == -1) {
			return -1;
		}

		return ((float) level / (float) scale) * 100.0f;
	}

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

		boolean mRegisteredTimeZoneReceiver = false;

		Paint mBackgroundPaint;
		Paint mTextClockPaint;
		Paint mTextBatteryPaint;

		boolean mAmbient;

		Time mTime;

		float mXOffset;
		float mYOffset;

		/**
		 * Whether the display supports fewer bits for each color in ambient mode. When true, we
		 * disable anti-aliasing in ambient mode.
		 */
		boolean mLowBitAmbient;

		Bitmap watchFaceBitmap;
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
			mYOffset = resources.getDimension(R.dimen.digital_y_offset);

			mBackgroundPaint = new Paint();
			mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

			mTextClockPaint = new Paint();
			mTextClockPaint = createTextPaint(resources.getColor(R.color.digital_text));

			mTextBatteryPaint = new Paint();
			mTextBatteryPaint = createTextPaint(resources.getColor(R.color.digital_text));

			mTime = new Time();

			BitmapDrawable watchFaceDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.firefox, null);
			watchFaceBitmap = watchFaceDrawable.getBitmap();

		}

		@Override
		public void onDestroy() {
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
			mXOffset = resources.getDimension(isRound
					? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
			float textClockSize = resources.getDimension(isRound
					? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
			float textBatterySize = resources.getDimension(
					R.dimen.digital_text_size_battey);

			mTextClockPaint.setTextSize(textClockSize);
			mTextBatteryPaint.setTextSize(textBatterySize);
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
				}
				invalidate();
			}

			// Whether the timer should be running depends on whether we're visible (as well as
			// whether we're in ambient mode), so we may need to start or stop the timer.
			updateTimer();
		}

		@Override
		public void onDraw(Canvas canvas, Rect bounds) {
			// Draw the background.
			canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
			float bitmapWidth = canvas.getHeight();
			float bitmapHeight = watchFaceBitmap.getHeight() * canvas.getWidth() / watchFaceBitmap.getWidth();
			canvas.drawBitmap(watchFaceBitmap, null, new RectF(0, 0, bitmapWidth, bitmapHeight), null);
			// Draw H:MM in ambient mode or H:MM:SS in interactive mode.
			mTime.setToNow();
			String text = String.format("%02d:%02d", mTime.hour, mTime.minute);
			String batteryText = "---";
			if (batterylevel >= 0) {
				batteryText = String.format("%d%%", batterylevel);
			}

			RectF areaRectClock = new RectF(0, 0, canvas.getWidth(), canvas.getHeight());
			RectF areaRectBattery = new RectF(canvas.getWidth() * 3 / 4, canvas.getHeight() * 2 / 3
					, canvas.getWidth() / 4, canvas.getHeight() / 3);
			RectF textClockBounds = getMeasuredTextRect(areaRectClock, text, mTextClockPaint);
			RectF textBatteryBounds = getMeasuredTextRect(areaRectBattery, text, mTextBatteryPaint);

			canvas.drawText(batteryText,
					areaRectBattery.left, areaRectBattery.top - mTextBatteryPaint.ascent(), mTextBatteryPaint);
			canvas.drawText(text, textClockBounds.left, textClockBounds.top - mTextClockPaint.ascent(), mTextClockPaint);
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

		public void updateBatteryInfo(int level) {
			batterylevel = level;
			mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
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
