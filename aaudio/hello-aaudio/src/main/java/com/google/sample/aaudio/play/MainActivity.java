/*
 * Copyright 2017 The Android Open Source Project
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

package com.google.sample.aaudio.play;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.sample.aaudio.common.AudioDeviceAdapter;
import com.google.sample.aaudio.common.AudioDeviceListEntry;
import com.google.sample.aaudio.common.AudioDeviceListener;
import com.google.sample.aaudio.common.AudioDeviceNotifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private static final long UPDATE_LATENCY_EVERY_MILLIS = 1000;
    private static final int[] BUFFER_SIZE_OPTIONS = {0, 1, 2, 4, 8};

    private boolean mEngineCreated = false;
    private Spinner mPlaybackDeviceSpinner;
    private Spinner mBufferSizeSpinner;
    private TextView mLatencyText;
    private Timer mLatencyUpdater;

    /*
     * Hook to user control to start / stop audio playback:
     *    touch-down: start, and keeps on playing
     *    touch-up: stop.
     * simply pass the events to native side.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        switch(action) {
            case (MotionEvent.ACTION_DOWN) :
                if (mEngineCreated)
                    PlaybackEngine.setToneOn(true);
                break;
            case (MotionEvent.ACTION_UP) :
                if (mEngineCreated)
                    PlaybackEngine.setToneOn(false);
                break;
        }
        return super.onTouchEvent(event);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPlaybackDeviceSpinner = findViewById(R.id.playbackDevicesSpinner);
        mPlaybackDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                PlaybackEngine.setAudioDeviceId(getPlaybackDeviceId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        setupPlaybackDeviceNotifier();

        mBufferSizeSpinner = findViewById(R.id.bufferSizeSpinner);
        mBufferSizeSpinner.setAdapter(new SimpleAdapter(
                this,
                createBufferSizeOptionsList(), // list of buffer size options
                R.layout.buffer_sizes_spinner, // the xml layout
                new String[]{getString(R.string.buffer_size_description_key)}, // field to display
                new int[]{R.id.bufferSizeOption})); // View to show field in

        mBufferSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                PlaybackEngine.setBufferSizeInBursts(getBufferSizeInBursts());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        // initialize native audio system
        mEngineCreated = PlaybackEngine.create();

        // Periodically update the UI with the output stream latency
        mLatencyText = findViewById(R.id.latencyText);
        setupLatencyUpdater();
    }

    private void setupPlaybackDeviceNotifier() {

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceNotifier playbackDeviceNotifier = new AudioDeviceNotifier(
                getResources(),
                audioManager,
                AudioManager.GET_DEVICES_OUTPUTS);

        playbackDeviceNotifier.registerListener(new AudioDeviceListener() {
            @Override
            public void onDevicesUpdated(List<AudioDeviceListEntry> deviceEntries) {
                AudioDeviceAdapter deviceAdapter =
                        new AudioDeviceAdapter(MainActivity.super.getBaseContext(),
                                deviceEntries);
                mPlaybackDeviceSpinner.setSelection(0); // Select first item in list
                mPlaybackDeviceSpinner.setAdapter(deviceAdapter);
            }
        });
    }

    private int getPlaybackDeviceId(){
        return ((AudioDeviceListEntry) mPlaybackDeviceSpinner.getSelectedItem()).getId();
    }

    private int getBufferSizeInBursts(){
        @SuppressWarnings("unchecked")
        HashMap<String,String> selectedOption = (HashMap<String,String>)
                mBufferSizeSpinner.getSelectedItem();

        String valueKey = getString(R.string.buffer_size_value_key);

        // parseInt will throw a NumberFormatException if the string doesn't contain a valid integer
        // representation. We don't need to worry about this because the values are derived from
        // the BUFFER_SIZE_OPTIONS int array.
        return Integer.parseInt(selectedOption.get(valueKey));
    }

    private void setupLatencyUpdater() {

        //Update the latency every 1s
        TimerTask latencyUpdateTask = new TimerTask() {
            @Override
            public void run() {

                double latency = PlaybackEngine.getCurrentOutputLatencyMillis();
                final String latencyStr;
                if (latency >= 0){
                    latencyStr = String.format(Locale.getDefault(), "%.2fms", latency);
                } else {
                    latencyStr = "Unknown";
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLatencyText.setText(getString(R.string.latency, latencyStr));
                    }
                });
            }
        };

        mLatencyUpdater = new Timer();
        mLatencyUpdater.schedule(latencyUpdateTask, 0, UPDATE_LATENCY_EVERY_MILLIS);
    }

    @Override
    protected void onDestroy() {
        if (mLatencyUpdater != null) mLatencyUpdater.cancel();
        PlaybackEngine.delete();
        super.onDestroy();
    }

    /**
     * Creates a list of buffer size options which can be used to populate a SimpleAdapter.
     * Each option has a description and a value. The description is always equal to the value,
     * except when the value is zero as this indicates that the buffer size should be set
     * automatically by the audio engine
     *
     * @return list of buffer size options
     */
    private List<HashMap<String,String>> createBufferSizeOptionsList(){

        ArrayList<HashMap<String,String>> bufferSizeOptions = new ArrayList<>();

        for (int i : BUFFER_SIZE_OPTIONS){
            HashMap<String,String> option = new HashMap<>();
            String strValue = String.valueOf(i);
            String description = (i == 0) ? getString(R.string.automatic) : strValue;
            option.put(getString(R.string.buffer_size_description_key), description);
            option.put(getString(R.string.buffer_size_value_key), strValue);

            bufferSizeOptions.add(option);
        }

        return bufferSizeOptions;
    }
}
