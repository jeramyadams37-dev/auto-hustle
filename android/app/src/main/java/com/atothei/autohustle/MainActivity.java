package com.atothei.autohustle;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.atothei.autohustle.obd2.Obd2Plugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(Obd2Plugin.class);
        super.onCreate(savedInstanceState);
    }
}
