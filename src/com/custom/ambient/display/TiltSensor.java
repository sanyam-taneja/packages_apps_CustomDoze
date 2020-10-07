/*
 * Copyright (c) 2015 The CyanogenMod Project
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

package com.custom.ambient.display;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TiltSensor implements SensorEventListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "TiltSensor";

    private static final int BATCH_LATENCY_IN_MS = 100;
    private static final int MIN_PULSE_INTERVAL_MS = 2500;
    private static final int MIN_WAKEUP_INTERVAL_MS = 1000;
    private static final int WAKELOCK_TIMEOUT_MS = 300;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Context mContext;

    private boolean mTiltGestureEnabled;
    private boolean mIsGlanceGesture;
    private PowerManager mPowerManager;
    private WakeLock mWakeLock;

    private long mEntryTimestamp;
    private boolean mEnabled;

    private final ExecutorService mExecutorService;

    public TiltSensor(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mIsGlanceGesture = false;
            String customTilt = mContext.getResources().getString(R.string.config_custom_tilt);
            if (!customTilt.isEmpty()) {
                mSensorManager = mContext.getSystemService(SensorManager.class);
                mSensor = Utils.getSensor(mSensorManager, customTilt);
            } else {
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_TILT_DETECTOR);
            }

            if (mSensor == null) {
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GLANCE_GESTURE, true);
                mIsGlanceGesture = true;
            }
        }
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean isRaiseToWake = Utils.isRaiseToWakeEnabled(mContext);

        if (DEBUG) Log.d(TAG, "Got sensor event: " + event.values[0]);

        if (mSensor == Utils.getSensor(mSensorManager, "qti.sensor.amd")) {
            if (event.values[0] == 2) {
                Utils.launchDozePulse(mContext);
            }
        } else {
            if (event.values[0] == 1) {
                Utils.launchDozePulse(mContext);
            }
        }
    }

    private Future<?> submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Empty */
    }

    // Switching screen OFF - we enable the sensor
    protected void enable() {
        if (DEBUG) Log.d(TAG, "Enabling");
        submit(() -> {
            // We save user settings so at next screen ON call (enable())
            // we don't need to read them again from the Settings provider
            mTiltGestureEnabled = Utils.tiltGestureEnabled(mContext);
            if (mTiltGestureEnabled) {
                if (!mIsGlanceGesture) {
                    mSensorManager.registerListener(this, mSensor,
                            SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_IN_MS * 1000);
                } else {
                    if (!mEnabled) {
                        if (!mSensorManager.requestTriggerSensor(mGlanceListener, mSensor)) {
                            throw new RuntimeException("Failed to requestTriggerSensor for sensor " + mSensor);
                        }
                        mEnabled = true;
                    }
                }
                mEntryTimestamp = SystemClock.elapsedRealtime();
            }
        });
    }

    // Switching screen ON - we disable the sensor
    protected void disable() {
        if (DEBUG) Log.d(TAG, "Disabling");
        submit(() -> {
            if (mTiltGestureEnabled) {
                if (!mIsGlanceGesture) {
                    mSensorManager.unregisterListener(this, mSensor);
                } else {
                    if (mEnabled) {
                        mSensorManager.cancelTriggerSensor(mGlanceListener, mSensor);
                        mEnabled = false;
                    }
                }
            }
        });
    }

    private TriggerEventListener mGlanceListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            if (DEBUG) Log.d(TAG, "triggered");

            Utils.launchDozePulse(mContext);
            if (!mSensorManager.requestTriggerSensor(mGlanceListener, mSensor)) {
                throw new RuntimeException("Failed to requestTriggerSensor for sensor " + mSensor);
            }
        }
    };
}
