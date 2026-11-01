package com.orbholdings.offlinehub;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;
import java.io.ByteArrayOutputStream;

/** Read-only JBD/Xiaoxiang BLE monitor. No BMS configuration commands. */
public class JbdMonitor {
    public interface Listener {
        void status(String message);
        void reading(int percent, float volts, float amps);
    }
    private final Activity activity;
    private final Listener listener;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothDevice lastDevice;
    private int retryCount=0;
    private boolean closed=false;
    private BluetoothGattCharacteristic write,notify;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ByteArrayOutputStream rx=new ByteArrayOutputStream();
    private boolean gotReading=false;
    private int notifications=0;
    private static final UUID SERVICE=UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb");
    private static final UUID TX=UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    private static final UUID RX=UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    private static final UUID CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public JbdMonitor(Activity activity,Listener listener){this.activity=activity;this.listener=listener;}
    public boolean hasPermission(){
        return Build.VERSION.SDK_INT<31 || (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
                &&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);
    }
    public void requestPermission(){
        if(Build.VERSION.SDK_INT>=31) activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},410);
        else if(Build.VERSION.SDK_INT>=23 && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},410);
    }
    private boolean ready(){
        if(!hasPermission()){requestPermission();listener.status("Allow Nearby devices, then tap Scan again");return false;}
        BluetoothManager manager=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
        if(manager==null||manager.getAdapter()==null||!manager.getAdapter().isEnabled()){listener.status("Enable Bluetooth first");return false;}
        return true;
    }
    private final LinkedHashMap<String,BluetoothDevice> devices=new LinkedHashMap<>();
    public void scan(java.util.function.Consumer<List<String>> callback){
        if(!ready())return;
        disconnect();devices.clear();
        BluetoothManager manager=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
        scanner=manager.getAdapter().getBluetoothLeScanner();
        if(scanner==null){listener.status("BLE scanning unavailable");return;}
        listener.status("Scanning for nearby batteries…");
        try{scanner.startScan(scanCallback);}catch(SecurityException e){listener.status("Bluetooth permission denied");return;}
        handler.postDelayed(()->{
            stopScan();
            List<String> names=new ArrayList<>();
            for(Map.Entry<String,BluetoothDevice> entry:devices.entrySet()){
                String name;try{name=entry.getValue().getName();}catch(SecurityException e){name=null;}
                names.add((name==null||name.isEmpty()?"BLE device":name)+"  ·  "+entry.getKey());
            }
            callback.accept(names);
            listener.status(names.isEmpty()?"No BLE devices found":"Choose your JBD battery");
        },9000);
    }
    private final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult result){
            BluetoothDevice device=result.getDevice();
            if(device!=null)devices.put(device.getAddress(),device);
        }
        @Override public void onScanFailed(int error){listener.status("Scan failed: "+error);}
    };
    private void stopScan(){
        if(scanner!=null){try{scanner.stopScan(scanCallback);}catch(Exception ignored){}scanner=null;}
    }
    public void connect(String selected){
        if(!ready())return;
        int pos=selected.lastIndexOf("  ·  ");if(pos<0)return;
        BluetoothDevice device=devices.get(selected.substring(pos+5));if(device==null)return;
        stopScan();disconnect();closed=false;retryCount=0;lastDevice=device;gotReading=false;notifications=0;
        listener.status("Connecting to "+selected+"…");
        try{gatt=device.connectGatt(activity,false,callback,BluetoothDevice.TRANSPORT_LE);}
        catch(SecurityException e){listener.status("Bluetooth permission denied");}
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){
            if(g!=gatt||closed){try{g.close();}catch(Exception ignored){}return;}
            if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){
                listener.status("Connected, discovering services…");
                try{g.discoverServices();}catch(SecurityException e){listener.status("Permission denied");}
            }else{
                handler.removeCallbacks(poller);write=null;notify=null;
                try{g.close();}catch(Exception ignored){}gatt=null;
                if(!closed&&status==133&&retryCount<2&&lastDevice!=null){
                    int attempt=++retryCount;listener.status("Bluetooth error 133 · retrying "+attempt+"/2");
                    handler.postDelayed(()->{if(!closed&&gatt==null&&lastDevice!=null){
                        try{gatt=lastDevice.connectGatt(activity,false,callback,BluetoothDevice.TRANSPORT_LE);}
                        catch(Exception e){listener.status("Bluetooth reconnect failed");}
                    }},2000L*attempt);
                }else listener.status("Disconnected ("+status+")");
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){
            BluetoothGattService service=g.getService(SERVICE);
            if(status!=BluetoothGatt.GATT_SUCCESS||service==null){listener.status("Not a compatible classic JBD device");return;}
            write=service.getCharacteristic(TX);notify=service.getCharacteristic(RX);
            if(write==null||notify==null){listener.status("JBD characteristics not found");return;}
            try{
                g.setCharacteristicNotification(notify,true);
                BluetoothGattDescriptor descriptor=notify.getDescriptor(CCC);
                if(descriptor==null){listener.status("Notification descriptor unavailable");return;}
                if(Build.VERSION.SDK_INT>=33)g.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                else{descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);g.writeDescriptor(descriptor);}
            }catch(SecurityException e){listener.status("Permission denied");}
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){
            if(status==BluetoothGatt.GATT_SUCCESS){listener.status("JBD connected · waiting for battery data");poll();handler.postDelayed(()->{if(gatt!=null&&!gotReading)listener.status("Connected · no valid JBD readings ("+notifications+" notifications)");},11000);}
            else listener.status("Could not enable JBD notifications");
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){
            parse(c.getValue());
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){
            parse(value);
        }
    };
    private final Runnable poller=new Runnable(){public void run(){poll();}};
    private void poll(){
        if(gatt==null||write==null)return;
        byte[] cmd={(byte)0xDD,(byte)0xA5,0x03,0x00,(byte)0xFF,(byte)0xFD,0x77};
        try{
            if(Build.VERSION.SDK_INT>=33)gatt.writeCharacteristic(write,cmd,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            else{write.setValue(cmd);gatt.writeCharacteristic(write);}
            handler.removeCallbacks(poller);handler.postDelayed(poller,3000);
        }catch(SecurityException e){listener.status("Bluetooth permission denied");}
    }
    private synchronized void parse(byte[] bytes){
        if(bytes==null)return;
        notifications++;
        for(byte b:bytes)rx.write(b);
        byte[] data=rx.toByteArray();int offset=0;
        while(offset+7<=data.length){
            if((data[offset]&255)!=0xDD){offset++;continue;}
            int length=data[offset+3]&255;
            if(length>100){offset++;continue;}
            int total=length+7;if(offset+total>data.length)break;
            if((data[offset+total-1]&255)!=0x77){offset++;continue;}
            int sum=0;for(int i=offset+2;i<offset+4+length;i++)sum=(sum+(data[i]&255))&0xffff;
            int check=(-sum)&0xffff;
            int actual=((data[offset+4+length]&255)<<8)|(data[offset+5+length]&255);
            if(check==actual && (data[offset+1]&255)==3 && data[offset+2]==0 && length>=19){
                int base=offset+4;
                float voltage=(((data[base]&255)<<8)|(data[base+1]&255))/100f;
                int raw=((data[base+2]&255)<<8)|(data[base+3]&255);
                float current=(short)raw/100f;
                int percent=data[base+19]&255;
                if(length>19&&percent<=100&&voltage>0&&voltage<1000){gotReading=true;handler.post(()->listener.reading(percent,voltage,current));}
            }
            offset+=total;
        }
        rx.reset();if(offset<data.length)rx.write(data,offset,Math.min(data.length-offset,128));
    }
    public void disconnect(){
        closed=true;handler.removeCallbacks(poller);stopScan();write=null;notify=null;rx.reset();
        if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(Exception ignored){}gatt=null;}
    }
}
