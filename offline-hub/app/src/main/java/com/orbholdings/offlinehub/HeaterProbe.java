package com.orbholdings.offlinehub;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;

/** Read-only BLE discovery for unidentified diesel-heater controllers. No command writes. */
public final class HeaterProbe {
 public interface Listener {void status(String s);void devices(List<String> devices);void report(String s);}
 private final Activity activity;private final Listener listener;private final Handler handler=new Handler(Looper.getMainLooper());
 private BluetoothLeScanner scanner;private BluetoothGatt gatt;private final Map<String,BluetoothDevice> found=new LinkedHashMap<>();
 private final ScanCallback scanCallback=new ScanCallback(){
  @Override public void onScanResult(int type,ScanResult result){BluetoothDevice d=result.getDevice();String name=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();if(name==null)try{name=d.getName();}catch(SecurityException ignored){}if(name==null)name="Unknown";found.put(d.getAddress(),d);names.put(d.getAddress(),name);}
 };
 private final Map<String,String> names=new LinkedHashMap<>();
 public HeaterProbe(Activity activity,Listener listener){this.activity=activity;this.listener=listener;}
 private boolean permission(){if(Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},421);listener.status("Allow Nearby devices, then scan again");return false;}return true;}
 public void scan(){if(!permission())return;stopScan();found.clear();names.clear();BluetoothManager manager=(BluetoothManager)activity.getSystemService(Activity.BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager.getAdapter();if(adapter==null||!adapter.isEnabled()){listener.status("Enable Bluetooth first");return;}scanner=adapter.getBluetoothLeScanner();if(scanner==null){listener.status("Bluetooth scanner unavailable");return;}try{scanner.startScan(scanCallback);listener.status("Scanning for heater…");handler.postDelayed(()->{stopScan();List<String> list=new ArrayList<>();for(String address:found.keySet())list.add(names.get(address)+" · "+address);listener.devices(list);listener.status(list.isEmpty()?"No BLE devices found":"Select your heater");},9000);}catch(SecurityException e){listener.status("Bluetooth permission denied");}}
 private void stopScan(){if(scanner!=null)try{scanner.stopScan(scanCallback);}catch(Exception ignored){}scanner=null;}
 public void connect(String selection){stopScan();int idx=selection.lastIndexOf(" · ");if(idx<0)return;String address=selection.substring(idx+3);BluetoothDevice device=found.get(address);if(device==null){listener.status("Heater not found; scan again");return;}if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(Exception ignored){}gatt=null;}listener.status("Connecting to "+names.get(address)+"…");try{gatt=device.connectGatt(activity,false,new BluetoothGattCallback(){
  @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){handler.post(()->{if(g!=gatt)return;if(status!=BluetoothGatt.GATT_SUCCESS){listener.status("BLE connection failed ("+status+")");close(g);return;}if(state==BluetoothProfile.STATE_CONNECTED){listener.status("Connected · discovering services");try{g.discoverServices();}catch(SecurityException e){listener.status("Bluetooth permission denied");}}else if(state==BluetoothProfile.STATE_DISCONNECTED){listener.status("Heater disconnected");close(g);}});}
  @Override public void onServicesDiscovered(BluetoothGatt g,int status){handler.post(()->{if(g!=gatt)return;if(status!=BluetoothGatt.GATT_SUCCESS){listener.status("Service discovery failed ("+status+")");return;}StringBuilder b=new StringBuilder("Device: "+names.get(address)+"\nAddress: "+address+"\n\n");for(BluetoothGattService service:g.getServices()){b.append("SERVICE ").append(service.getUuid()).append("\n");for(BluetoothGattCharacteristic c:service.getCharacteristics()){int p=c.getProperties();b.append("  ").append(c.getUuid()).append("  ");if((p&BluetoothGattCharacteristic.PROPERTY_READ)!=0)b.append("READ ");if((p&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)b.append("NOTIFY ");if((p&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0)b.append("INDICATE ");if((p&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0)b.append("WRITE ");if((p&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0)b.append("WRITE_NO_RESPONSE ");b.append("\n");}}listener.status("Heater discovered · diagnostic only");listener.report(b.toString());});}
 },BluetoothDevice.TRANSPORT_LE);}catch(SecurityException e){listener.status("Bluetooth permission denied");}}
 private void close(BluetoothGatt g){try{g.close();}catch(Exception ignored){}if(g==gatt)gatt=null;}
 public void disconnect(){stopScan();if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(Exception ignored){}gatt=null;}}
}
