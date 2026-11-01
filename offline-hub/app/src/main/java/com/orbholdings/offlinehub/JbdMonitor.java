package com.orbholdings.offlinehub;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.*;

/** UI adapter around the original, tested Offline BMS Bluetooth engine. Read-only. */
public final class JbdMonitor {
    public interface Listener {
        void status(String message);
        void reading(int percent,float volts,float amps);
    }
    private final Activity activity;
    private final Listener listener;
    private final BleClient client;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String,BluetoothDevice> devices=new LinkedHashMap<>();
    private final Map<String,String> names=new LinkedHashMap<>();
    private boolean scanning;
    public JbdMonitor(Activity activity,Listener listener){
        this.activity=activity;this.listener=listener;
        client=new BleClient(activity,new BleClient.Listener(){
            @Override public void onState(String message,boolean ready){activity.runOnUiThread(()->listener.status(message));}
            @Override public void onDevice(BluetoothDevice device,String name,int rssi,boolean likelyJbd){
                String address=device.getAddress();
                devices.put(address,device);
                names.put(address,name);
            }
            @Override public void onReading(Reading reading){
                activity.runOnUiThread(()->listener.reading(reading.basic.soc,(float)reading.basic.voltage,(float)reading.basic.current));
            }
            @Override public void onLog(String message){}
            @Override public void onModel(String model){}
        });
    }
    public void scan(java.util.function.Consumer<List<String>> callback){
        if(Build.VERSION.SDK_INT>=31&& !client.hasPermission()){
            activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},410);
            listener.status("Allow Nearby devices, then tap Scan again");return;
        }
        if(Build.VERSION.SDK_INT<31&&Build.VERSION.SDK_INT>=23&&activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},410);
            listener.status("Allow location for Bluetooth scanning, then retry");return;
        }
        devices.clear();names.clear();scanning=true;
        client.startScan();
        handler.postDelayed(()->{
            if(!scanning)return;scanning=false;client.stopScan();
            List<String> choices=new ArrayList<>();
            for(Map.Entry<String,BluetoothDevice> entry:devices.entrySet()){
                String addr=entry.getKey();
                choices.add(names.getOrDefault(addr,"BLE device")+"  ·  "+addr);
            }
            callback.accept(choices);
        },9000);
    }
    public void connect(String selection){
        int split=selection.lastIndexOf("  ·  ");if(split<0)return;
        String address=selection.substring(split+5);
        BluetoothDevice device=devices.get(address);
        if(device==null){listener.status("Battery not found · scan again");return;}
        scanning=false;
        client.connect(device,names.getOrDefault(address,"JBD battery"),"","Auto");
    }
    public void disconnect(){scanning=false;client.disconnect();}
}
