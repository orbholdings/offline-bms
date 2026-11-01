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
 private final ArrayDeque<BluetoothGattCharacteristic> subscriptions=new ArrayDeque<>();
 private String reportText="";private int packets=0;private BluetoothGattCharacteristic statusChannel;private boolean statusSent=false;
 private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
 private static String hex(byte[] data){StringBuilder b=new StringBuilder();if(data!=null)for(byte v:data)b.append(String.format(java.util.Locale.ROOT,"%02X",v&255));return b.toString();}
 private void requestStatus(BluetoothGatt g){
  if(g!=gatt||statusSent||statusChannel==null)return;
  statusSent=true;
  // AA55 command 1 is a read-only status query; packet format from diesel-heater-ble.
  // Default passkey 1234 is used only for this query; no power/mode commands are sent.
  byte[] packet=new byte[]{(byte)0xAA,0x55,12,34,1,0,0,47};
  try{
   boolean ok;
   if(Build.VERSION.SDK_INT>=33)ok=g.writeCharacteristic(statusChannel,packet,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
   else{statusChannel.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);statusChannel.setValue(packet);ok=g.writeCharacteristic(statusChannel);}
   reportText+="\nTX STATUS QUERY (command 1, default PIN 1234): "+hex(packet)+" · "+(ok?"queued":"rejected");
   listener.report(reportText);
   listener.status(ok?"Status query sent · awaiting response":"Status query could not be queued");
  }catch(SecurityException e){listener.status("Bluetooth permission denied");}
 }
 private void nextSubscription(BluetoothGatt g){if(g!=gatt)return;if(subscriptions.isEmpty()){requestStatus(g);return;}BluetoothGattCharacteristic ch=subscriptions.poll();try{g.setCharacteristicNotification(ch,true);BluetoothGattDescriptor d=ch.getDescriptor(CCCD);if(d==null){nextSubscription(g);return;}if(Build.VERSION.SDK_INT>=33){if(g.writeDescriptor(d,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)!=BluetoothStatusCodes.SUCCESS)nextSubscription(g);}else{d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);if(!g.writeDescriptor(d))nextSubscription(g);}}catch(SecurityException e){listener.status("Bluetooth permission denied");}}

 public HeaterProbe(Activity activity,Listener listener){this.activity=activity;this.listener=listener;}
 private boolean permission(){if(Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},421);listener.status("Allow Nearby devices, then scan again");return false;}return true;}
 public void scan(){if(!permission())return;stopScan();found.clear();names.clear();BluetoothManager manager=(BluetoothManager)activity.getSystemService(Activity.BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager.getAdapter();if(adapter==null||!adapter.isEnabled()){listener.status("Enable Bluetooth first");return;}scanner=adapter.getBluetoothLeScanner();if(scanner==null){listener.status("Bluetooth scanner unavailable");return;}try{scanner.startScan(scanCallback);listener.status("Scanning for heater…");handler.postDelayed(()->{stopScan();List<String> list=new ArrayList<>();for(String address:found.keySet())list.add(names.get(address)+" · "+address);listener.devices(list);listener.status(list.isEmpty()?"No BLE devices found":"Select your heater");},9000);}catch(SecurityException e){listener.status("Bluetooth permission denied");}}
 private void stopScan(){if(scanner!=null)try{scanner.stopScan(scanCallback);}catch(Exception ignored){}scanner=null;}
 public void connect(String selection){stopScan();int idx=selection.lastIndexOf(" · ");if(idx<0)return;String address=selection.substring(idx+3);BluetoothDevice device=found.get(address);if(device==null){listener.status("Heater not found; scan again");return;}if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(Exception ignored){}gatt=null;}listener.status("Connecting to "+names.get(address)+"…");try{gatt=device.connectGatt(activity,false,new BluetoothGattCallback(){
  @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){handler.post(()->{if(g!=gatt)return;if(status!=BluetoothGatt.GATT_SUCCESS){listener.status("BLE connection failed ("+status+")");close(g);return;}if(state==BluetoothProfile.STATE_CONNECTED){listener.status("Connected · discovering services");try{g.discoverServices();}catch(SecurityException e){listener.status("Bluetooth permission denied");}}else if(state==BluetoothProfile.STATE_DISCONNECTED){listener.status("Heater disconnected");close(g);}});}
  @Override public void onServicesDiscovered(BluetoothGatt g,int status){handler.post(()->{if(g!=gatt)return;if(status!=BluetoothGatt.GATT_SUCCESS){listener.status("Service discovery failed ("+status+")");return;}StringBuilder b=new StringBuilder("Device: "+names.get(address)+"\nAddress: "+address+"\n\n");for(BluetoothGattService service:g.getServices()){b.append("SERVICE ").append(service.getUuid()).append("\n");for(BluetoothGattCharacteristic c:service.getCharacteristics()){int p=c.getProperties();b.append("  ").append(c.getUuid()).append("  ");if((p&BluetoothGattCharacteristic.PROPERTY_READ)!=0)b.append("READ ");if((p&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)b.append("NOTIFY ");if((p&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0)b.append("INDICATE ");if((p&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0)b.append("WRITE ");if((p&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0)b.append("WRITE_NO_RESPONSE ");b.append("\n");}}reportText=b.toString();packets=0;statusSent=false;statusChannel=null;subscriptions.clear();BluetoothGattService heater=g.getService(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"));if(heater!=null){statusChannel=heater.getCharacteristic(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"));for(String uuid:new String[]{"0000ffe1-0000-1000-8000-00805f9b34fb","0000ffe4-0000-1000-8000-00805f9b34fb"}){BluetoothGattCharacteristic ch=heater.getCharacteristic(UUID.fromString(uuid));if(ch!=null&&(ch.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)subscriptions.add(ch);}}listener.status("Heater connected · listening for status packets");listener.report(reportText);nextSubscription(g);});}
  @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic ch,int status){handler.post(()->{if(g!=gatt)return;reportText+="\nWRITE RESULT "+ch.getUuid()+" GATT="+status+(status==BluetoothGatt.GATT_SUCCESS?" (delivered to BLE stack)":" (failed)");listener.report(reportText);if(status!=BluetoothGatt.GATT_SUCCESS)listener.status("Status query write failed · GATT "+status);});}
  @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor descriptor,int status){handler.post(()->{if(g!=gatt)return;if(status!=BluetoothGatt.GATT_SUCCESS)listener.status("Notification setup failed ("+status+")");nextSubscription(g);});}
  @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic ch,byte[] value){capture(g,ch,value);}
  @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic ch){capture(g,ch,ch.getValue());}
  private void capture(BluetoothGatt g,BluetoothGattCharacteristic ch,byte[] value){handler.post(()->{if(g!=gatt||packets>=40)return;packets++;String frame=hex(value);String family=frame.startsWith("AA55")?"AA55":frame.startsWith("AA66")?"AA66":"Unknown / possible encrypted or fragmented";reportText+="\nRX "+packets+" "+ch.getUuid()+" "+family+" "+frame;listener.report(reportText);listener.status("Heater connected · "+packets+" notification(s) captured");});}
 },BluetoothDevice.TRANSPORT_LE);}catch(SecurityException e){listener.status("Bluetooth permission denied");}}
 private void close(BluetoothGatt g){try{g.close();}catch(Exception ignored){}if(g==gatt)gatt=null;}
 public void disconnect(){stopScan();if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(Exception ignored){}gatt=null;}}
}
