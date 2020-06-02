package com.terabee.sdkdemo;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.terabee.sdk.TerabeeSdk;
import com.terabee.sdkdemo.databinding.ActivityMainBinding;
import com.terabee.sdkdemo.logic.DataCollector;
import com.terabee.sdkdemo.logic.SensorCallback;
import com.terabee.sdkdemo.logic.SensorState;

import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Toast toast = null;
    private int entriesCounter = 0;
    private static final String[] SENSOR_TYPES = {
            TerabeeSdk.DeviceType.AUTO_DETECT.name(),
            TerabeeSdk.DeviceType.EVO_3M.name(),
            TerabeeSdk.DeviceType.EVO_60M.name(),
            TerabeeSdk.DeviceType.EVO_64PX.name(),
            TerabeeSdk.DeviceType.MULTI_FLEX.name(),
            TerabeeSdk.DeviceType.EVO_MINI.name(),
    };

    private static final String[] MULTIFLEX_SENSORS_LIST = {
            "Enable Sensor 1",
            "Enable Sensor 2",
            "Enable Sensor 3",
            "Enable Sensor 4",
            "Enable Sensor 5",
            "Enable Sensor 6",
            "Enable Sensor 7",
            "Enable Sensor 8"
    };

    public ActivityMainBinding mBinding;
    private SensorCallback sensorCallback = new SensorCallback() {

        @Override
        public void onEntryListReceived(@NotNull List<Long> entryTimestamps) {
            updateEntries(entryTimestamps);

        }

        @Override
        public void onReceivedData(byte[] bytes, int i, int i1) {
            // show something if needed
        }

        @Override
        public void onMatrixReceived(List<List<Integer>> list, int dataBandwidth, int dataSpeed) {
            updateMatrix(list, dataBandwidth, dataSpeed);
        }

        @Override
        public void onDistancesReceived(List<Integer> list, int dataBandwidth, int dataSpeed) {
            updateDistances(list, dataBandwidth, dataSpeed);
        }

        @Override
        public void onDistanceReceived(int distance, int dataBandwidth, int dataSpeed) {
            updateDistance(distance, dataBandwidth, dataSpeed);
        }

        @Override
        public void onSensorStateChanged(@NotNull SensorState sensorState) {
            updateUiState(sensorState, true);
        }
    };

    @SuppressLint("ShowToast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mBinding.configuration.setEnabled(true);
        toast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);
        // initialize UI
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SENSOR_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBinding.sensorType.setAdapter(adapter);
        mBinding.sensorType.setSelection(0);
        mBinding.sensorType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setCurrentType(((TextView) view).getText().toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no any action
            }
        });

        mBinding.connect.setOnClickListener(v -> DataCollector.INSTANCE.connectToDevice());
        mBinding.disconnect.setOnClickListener(v -> DataCollector.INSTANCE.disconnectDevice());
        mBinding.configuration.setOnClickListener(v -> showMultiflexConfigurationDialog());

        updateUiState(DataCollector.INSTANCE.getSensorState(), false);
        DataCollector.INSTANCE.addListener(sensorCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        entriesCounter = 0;
        mBinding.entriesCount.setText("" + entriesCounter);
        mBinding.lastChunkText.setText("");
    }

    @Override
    protected void onDestroy() {
        DataCollector.INSTANCE.removeListener(sensorCallback);
        toast = null;
        super.onDestroy();
    }


    private void updateUiState(SensorState sensorState, Boolean showToast) {
        runOnUiThread(() -> {
            TerabeeSdk.DeviceType deviceType = DataCollector.INSTANCE.getSensorType();
            switch (sensorState) {
                case Disconnected: {
                    mBinding.sensorType.setEnabled(true);
                    mBinding.connect.setEnabled(true);
                    mBinding.disconnect.setEnabled(false);
                    mBinding.data.setText("");
                    mBinding.dataBandwidth.setText("0");
                    mBinding.configuration.setEnabled(deviceType == TerabeeSdk.DeviceType.MULTI_FLEX || deviceType == TerabeeSdk.DeviceType.AUTO_DETECT);
                    if (showToast)
                        showShortToast("Unable connect to sensor: " + deviceType.name());
                    break;
                }
                case Connecting: {
                    mBinding.sensorType.setEnabled(false);
                    mBinding.connect.setEnabled(false);
                    mBinding.disconnect.setEnabled(false);
                    mBinding.data.setText("");
                    mBinding.dataBandwidth.setText("0");
                    mBinding.configuration.setEnabled(false);
                    break;
                }
                case Connected: {
                    mBinding.sensorType.setEnabled(false);
                    mBinding.connect.setEnabled(false);
                    mBinding.disconnect.setEnabled(true);
                    mBinding.data.setText("");
                    mBinding.dataBandwidth.setText("0");
                    mBinding.configuration.setEnabled(false);
                    if (showToast)
                        showShortToast("Connected sensor: " + deviceType.name());
                    break;
                }
            }
        });
    }

    private void updateEntries(List<Long> entryTimestamps) {
        runOnUiThread(() -> {
                    entriesCounter = entriesCounter + entryTimestamps.size();
                    mBinding.entriesCount.setText("" + entriesCounter);
                    String dateStr = new Date(entryTimestamps.get(entryTimestamps.size() - 1)).toString();
                    String lastChunkText = dateStr + ": " + entryTimestamps.size() + " items";
                    mBinding.lastChunkText.setText(lastChunkText);
                }
        );
    }

    private void setCurrentType(String value) {
        TerabeeSdk.DeviceType type = TerabeeSdk.DeviceType.valueOf(value);
        DataCollector.INSTANCE.setSensorType(type);
        mBinding.configuration.setEnabled(type == TerabeeSdk.DeviceType.MULTI_FLEX ||
                type == TerabeeSdk.DeviceType.AUTO_DETECT);
    }

    private boolean[] mSelectedMultiflexSensors = null;

    private void showMultiflexConfigurationDialog() {
        TerabeeSdk.MultiflexConfiguration multiflexConfiguration = DataCollector.INSTANCE.getMultiflexConfiguration();
        mSelectedMultiflexSensors = new boolean[]{
                multiflexConfiguration.isSensor1Enable(),
                multiflexConfiguration.isSensor2Enable(),
                multiflexConfiguration.isSensor3Enable(),
                multiflexConfiguration.isSensor4Enable(),
                multiflexConfiguration.isSensor5Enable(),
                multiflexConfiguration.isSensor6Enable(),
                multiflexConfiguration.isSensor7Enable(),
                multiflexConfiguration.isSensor8Enable()
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Multiflex configuration");
        builder.setMultiChoiceItems(MULTIFLEX_SENSORS_LIST, mSelectedMultiflexSensors,
                (dialog, selectedItemId, isSelected) -> mSelectedMultiflexSensors[selectedItemId] = isSelected)
                .setPositiveButton("Done", (dialog, id) -> {
                    // apply changes
                    TerabeeSdk.MultiflexConfiguration newMultiflexConfiguration = TerabeeSdk.MultiflexConfiguration.custom(
                            mSelectedMultiflexSensors[0],
                            mSelectedMultiflexSensors[1],
                            mSelectedMultiflexSensors[2],
                            mSelectedMultiflexSensors[3],
                            mSelectedMultiflexSensors[4],
                            mSelectedMultiflexSensors[5],
                            mSelectedMultiflexSensors[6],
                            mSelectedMultiflexSensors[7]
                    );
                    DataCollector.INSTANCE.setMultiflexConfiguration(newMultiflexConfiguration);
                })
                .setNegativeButton("Cancel", (dialog, id) -> {
                    // not apply changes
                });
        Dialog dialog = builder.create();
        dialog.show();
    }

    private void updateMatrix(List<List<Integer>> list, int dataBandwidth, int dataSpeed) {
        runOnUiThread(() -> {
            if (DataCollector.INSTANCE.getSensorState() == SensorState.Connected) {
                mBinding.dataBandwidth.setText("Bandwidth: " + dataBandwidth);

                if (list != null) {
                    String matrix = "Matrix: \n";

                    for (int i = 0; i < list.size(); i++) {
                        for (int j = 0; j < list.get(i).size(); j++) {
                            matrix += String.valueOf(list.get(i).get(j)) + ", ";
                        }

                        matrix += "\n";
                    }

                    mBinding.data.setText(matrix);
                }
            }
        });
    }

    private void updateDistance(int distance, int dataBandwidth, int dataSpeed) {
        runOnUiThread(() -> {
            if (DataCollector.INSTANCE.getSensorState() == SensorState.Connected) {
                mBinding.dataBandwidth.setText("Bandwidth: " + dataBandwidth);
                mBinding.data.setText("Distance: " + distance);
            }
        });
    }

    private void updateDistances(List<Integer> list, int dataBandwidth, int dataSpeed) {
        runOnUiThread(() -> {
            if (DataCollector.INSTANCE.getSensorState() == SensorState.Connected) {
                mBinding.dataBandwidth.setText("Bandwidth: " + dataBandwidth);

                if (list != null) {
                    Log.d(TAG, "onDistancesReceived, size: " + list.size());
                    Log.d(TAG, "onDistancesReceived, list: " + list.toString());

                    String distances = "Distances: \n";

                    for (int i = 0; i < list.size(); i++) {
                        distances += "Sensor " + String.valueOf(i + 1);
                        distances += ": " + String.valueOf(list.get(i));
                        distances += "\n";
                    }

                    mBinding.data.setText(distances);
                }
            }
        });
    }

    private void showShortToast(String message) {
        runOnUiThread(() -> {
            toast.setText(message);
            toast.show();
        });
    }

}
