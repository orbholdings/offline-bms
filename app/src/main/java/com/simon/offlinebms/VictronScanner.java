package com.simon.offlinebms;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import java.util.*;
public final class VictronScanner {
    public interface Listener{void received(String address,String name,VictronProtocol.Data data);void error(String address,String message);}
    private final Handler handler=new Handler(Looper.getMainLooper());private final Context context;private final Listener listener;private BluetoothLeScanner scanner;private boolean running;
    private final Map<String,byte[]> keys=new HashMap<>();private final Map<String,Integer> counters=new HashMap<>();private final Set<String> failed=new HashSet<>();
    public VictronScanner(Context c,Listener l){context=c.getApplicationContext();listener=l;}
    public boolean running(){return running;}
    public void start(ProfileStore profiles,PasswordStore secrets)throws Exception{stop();for(ProfileStore.Profile p:profiles.all())if(p.protocol.equals("Victron")){String k=secrets.get("victron:"+p.address);if(!k.isEmpty())keys.put(p.address,VictronProtocol.key(k));}if(keys.isEmpty())throw new IllegalArgumentException("Add a Victron device and its Instant Readout key first");BluetoothManager m=context.getSystemService(BluetoothManager.class);BluetoothAdapter a=m==null?null:m.getAdapter();if(a==null||!a.isEnabled())throw new IllegalArgumentException("Turn on Bluetooth");scanner=a.getBluetoothLeScanner();running=true;scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setManufacturerData(0x02e1,new byte[0]).build()),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);}
    public void stop(){running=false;if(scanner!=null)try{scanner.stopScan(callback);}catch(RuntimeException ignored){}keys.clear();counters.clear();failed.clear();}
    private final ScanCallback callback=new ScanCallback(){@Override public void onScanResult(int type,ScanResult result){handler.post(()->accept(result));}@Override public void onBatchScanResults(List<ScanResult> rows){for(ScanResult r:rows)handler.post(()->accept(r));}@Override public void onScanFailed(int error){handler.post(()->{stop();listener.error("","Victron Bluetooth scan failed ("+error+")");});}};
    private void accept(ScanResult r){if(!running||r.getScanRecord()==null)return;String address;try{address=r.getDevice().getAddress();}catch(SecurityException e){stop();listener.error("","Nearby devices permission was removed");return;}byte[] key=keys.get(address);if(key==null||failed.contains(address))return;byte[] bytes=r.getScanRecord().getManufacturerSpecificData(0x02e1);if(bytes==null||bytes.length<9||(bytes[0]&255)!=0x10)return;if((bytes[4]&255)==0)return;int counter=(bytes[5]&255)|((bytes[6]&255)<<8);if(counters.containsKey(address)&&counters.get(address)==counter)return;
        try{VictronProtocol.Data data=VictronProtocol.decode(bytes,key);counters.put(address,counter);String name=r.getScanRecord().getDeviceName();listener.received(address,name==null?data.typeName():name,data);}catch(Exception e){String message=e.getMessage()==null?"Invalid Victron data":e.getMessage();if(message.contains("key does not match"))failed.add(address);listener.error(address,message);}
    }
}
