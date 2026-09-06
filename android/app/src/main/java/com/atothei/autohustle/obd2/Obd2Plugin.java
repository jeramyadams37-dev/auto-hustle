package com.atothei.autohustle.obd2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

@CapacitorPlugin(name = "Obd2")
public class Obd2Plugin extends Plugin {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket socket;
    private OutputStream outStream;
    private InputStream inStream;

    @PluginMethod
    public void connect(PluginCall call) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                call.reject("Bluetooth not available or disabled");
                return;
            }

            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            BluetoothDevice target = null;
            for (BluetoothDevice d : paired) {
                String name = d.getName() != null ? d.getName().toUpperCase() : "";
                if (name.contains("OBD") || name.contains("ELM") || name.contains("OBDLINK") || name.contains("BAFX")) {
                    target = d;
                    break;
                }
            }

            if (target == null) {
                call.reject("No paired OBD2 adapter found. Pair it in Bluetooth settings first.");
                return;
            }

            socket = target.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            outStream = socket.getOutputStream();
            inStream = socket.getInputStream();

            initAdapter();

            JSObject ret = new JSObject();
            ret.put("status", "connected");
            ret.put("device", target.getName());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Connection failed: " + e.getMessage());
        }
    }

    private void initAdapter() throws Exception {
        sendCommand("ATZ");
        Thread.sleep(1000);
        sendCommand("ATE0");
        sendCommand("ATL0");
        sendCommand("ATS0");
        sendCommand("ATH0");
        sendCommand("ATAT1");
        sendCommand("ATSP0");
    }

    private String sendCommand(String command) throws Exception {
        outStream.write((command + "\r").getBytes());
        outStream.flush();
        Thread.sleep(300);

        StringBuilder response = new StringBuilder();
        byte[] buffer = new byte[1024];
        while (inStream.available() > 0) {
            int bytes = inStream.read(buffer);
            response.append(new String(buffer, 0, bytes));
        }
        return response.toString().replace(">", "").trim();
    }

    @PluginMethod
    public void readCodes(PluginCall call) {
        try {
            if (outStream == null || inStream == null) {
                call.reject("Not connected. Call connect() first.");
                return;
            }

            String response = sendCommand("03");
            JSArray codes = parseDtcResponse(response);

            JSObject ret = new JSObject();
            ret.put("codes", codes);
            ret.put("raw", response);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Read failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void readLiveData(PluginCall call) {
        try {
            if (outStream == null || inStream == null) {
                call.reject("Not connected. Call connect() first.");
                return;
            }

            String rpmRaw = sendCommand("010C");
            String tempRaw = sendCommand("0105");

            JSObject ret = new JSObject();
            ret.put("rpm_raw", rpmRaw);
            ret.put("coolant_temp_raw", tempRaw);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Live data read failed: " + e.getMessage());
        }
    }

    private JSArray parseDtcResponse(String raw) {
        JSArray codes = new JSArray();
        String cleaned = raw.replaceAll("\\s", "");
        if (cleaned.startsWith("43")) {
            cleaned = cleaned.substring(2);
        }
        for (int i = 0; i + 4 <= cleaned.length(); i += 4) {
            String chunk = cleaned.substring(i, i + 4);
            if (chunk.equals("0000")) continue;
            String code = decodeDtc(chunk);
            if (code != null) codes.put(code);
        }
        return codes;
    }

    private String decodeDtc(String chunk) {
        try {
            int firstByte = Integer.parseInt(chunk.substring(0, 2), 16);
            char prefix;
            switch ((firstByte & 0xC0) >> 6) {
                case 0: prefix = 'P'; break;
                case 1: prefix = 'C'; break;
                case 2: prefix = 'B'; break;
                default: prefix = 'U';
            }
            return prefix + String.format("%04d", Integer.parseInt(chunk.substring(0), 16) & 0x3FFF).substring(0, 4);
        } catch (Exception e) {
            return null;
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        try {
            if (socket != null) socket.close();
            JSObject ret = new JSObject();
            ret.put("status", "disconnected");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Disconnect failed: " + e.getMessage());
        }
    }
}
