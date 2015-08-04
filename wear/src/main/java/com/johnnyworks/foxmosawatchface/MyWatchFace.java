package com.johnnyworks.foxmosawatchface;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.widget.TextView;

// http://www.binpress.com/tutorial/how-to-create-a-custom-android-wear-watch-face/120

public class MyWatchFace extends Activity {

	private static final String TAG = "WearActivity";

	private TextView mTime;
	private TextView mBattery;

	private final static IntentFilter INTENT_FILTER;
	static {
		INTENT_FILTER = new IntentFilter();
		INTENT_FILTER.addAction(Intent.ACTION_TIME_TICK);
		INTENT_FILTER.addAction(Intent.ACTION_TIMEZONE_CHANGED);
		INTENT_FILTER.addAction(Intent.ACTION_TIME_CHANGED);
	}

	private final String TIME_FORMAT_DISPLAYED = "kk:mm";

	private BroadcastReceiver mTimeInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context arg0, Intent intent) {
			mTime.setText(new SimpleDateFormat(TIME_FORMAT_DISPLAYED)
					.format(Calendar.getInstance().getTime()));
		}
	};

	private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context arg0, Intent intent) {
			mBattery.setText(String.valueOf(intent.getIntExtra(
					BatteryManager.EXTRA_LEVEL, 0) + "%"));
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_my);
		final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
		stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
			@Override
			public void onLayoutInflated(WatchViewStub stub) {
				mTime = (TextView) stub.findViewById(R.id.watch_time);
				mBattery = (TextView) stub.findViewById(R.id.watch_battery);

				mTimeInfoReceiver.onReceive(MyWatchFace.this,
						registerReceiver(null, INTENT_FILTER));
				registerReceiver(mTimeInfoReceiver, INTENT_FILTER);
				registerReceiver(mBatInfoReceiver, new IntentFilter(
						Intent.ACTION_BATTERY_CHANGED));
			}
		});

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mTimeInfoReceiver);
		unregisterReceiver(mBatInfoReceiver);
	}
}
