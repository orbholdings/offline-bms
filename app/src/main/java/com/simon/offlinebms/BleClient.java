package com.simon.offlinebms;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import java.util.*;

/** All mutable connection state is owned by the main looper. */
public final class BleClient {
    public interface Listener {
        void onState(String status,boolean ready);
        void onDevice(BluetoothDevice device,String name,int rssi,boolean likelyJbd);
        void onReading(Reading reading);
        void onLog(String text);
        void onModel(String model);
        default void onAdvertisement(BluetoothDevice device,String name,int rssi,byte[] manufacturer){}
        default void onParameters(JbdSettings.Snapshot snapshot,String message){}
        default void onParameterEvent(String message){}
        default void onBalancing(String message){}
    }
    public static final UUID SERVICE=uuid("ff00"),RX=uuid("ff01"),TX=uuid("ff02"),CCCD=uuid("2902");
    public static final UUID JK_SERVICE=uuid("ffe0"),JK_IO=uuid("ffe1"),JK_TX=uuid("ffe2");
    private final List<BluetoothGattCharacteristic> jkWriteChannels=new ArrayList<>();
    private int jkWriteChannel;private boolean jkWriteWithResponse;private String jkLastError="",jkDetectedVariant="";
    private int jkCellFrames,jkAcceptedFrames;
    private byte[] jkFirstRejected,jkLastRejected;
    private final JkProtocol.Assembler jkAssembler=new JkProtocol.Assembler();
    private boolean jk;
    private String protocol="Auto";
    private static UUID uuid(String shortId) { return UUID.fromString("0000"+shortId+"-0000-1000-8000-00805f9b34fb"); }
    private final Context context;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final JbdProtocol.Assembler assembler=new JbdProtocol.Assembler();
    private final JbdAuthentication.Assembler authAssembler=new JbdAuthentication.Assembler();
    private final List<BluetoothGattCharacteristic> subscriptions=new ArrayList<>();
    private final Set<UUID> notifyUuids=new HashSet<>();
    private JbdAuthentication authentication;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothLeScanner scanner;
    private boolean scanning,ready,transportReady,modelAttempted,connecting;
    private int pending=-1,retries,subscriptionIndex,rxCount;
    private String connectionPassword="",deviceAddress="";
    private JbdProtocol.Basic basic;
    private String deviceName="Battery";
    private long pollDelay=5000;
    private final JbdSettings.Assembler parameterAssembler=new JbdSettings.Assembler();
    private JbdSettingsSession parameterSession;
    private boolean parameterStarted,parameterAwaiting;
    private long parameterDeadline;
    private String deviceModel="";
    public JbdSettings.Snapshot parameters;
    public String parameterMessage="Read parameters from the connected battery";
    public JkProtocol.BalanceSettings jkBalanceSettings;
    public String balanceMessage="Read the balancing setting from the battery";
    private long jkBalanceAt,lastReadingAt;
    private boolean balanceBusy,balanceWriteSent,balanceAwaiting,balanceDesired;
    public boolean isJbdReady(){return ready&&!jk&&authentication==null&&basic!=null;}
    public boolean parametersBusy(){return parameterSession!=null;}
    public boolean operationsBusy(){return parametersBusy()||balanceBusy;}
    public boolean balancingBusy(){return balanceBusy;}
    public boolean isJkBalanceReady(){return jk&&ready&&transportReady&&gatt!=null&&basic!=null&&!jkDetectedVariant.isEmpty()&&lastReadingAt>0&&System.currentTimeMillis()-lastReadingAt<=Math.max(20000,pollDelay*3);}
    public boolean hasFreshJkBalanceSetting(){return isJkBalanceReady()&&jkBalanceSettings!=null&&System.currentTimeMillis()-jkBalanceAt<=60000;}
    public void readBalancingSettings(){
        if(!isJkBalanceReady())throw new IllegalArgumentException("Connect to a JK02 battery with recent readings first");
        if(operationsBusy())throw new IllegalArgumentException("Wait for the current battery operation to finish");
        pauseForBalancing();balanceBusy=true;balanceWriteSent=false;balanceAwaiting=true;jkBalanceSettings=null;balanceMessage="Reading balancing setting…";listener.onBalancing(balanceMessage);
        if(writePacket(JkProtocol.readCommand(0x96)))handler.postDelayed(balanceTimeout,6000);
    }
    public void setBalancing(JkProtocol.BalanceSettings expected,boolean enabled){
        if(!hasFreshJkBalanceSetting()||expected!=jkBalanceSettings)throw new IllegalArgumentException("Read this battery's balancing setting again");
        if(operationsBusy())throw new IllegalArgumentException("Wait for the current battery operation to finish");
        if(expected.enabled==enabled)throw new IllegalArgumentException("The balancing setting is already set");
        pauseForBalancing();balanceBusy=true;balanceWriteSent=true;balanceAwaiting=false;balanceDesired=enabled;balanceMessage="Changing balancing setting…";listener.onBalancing(balanceMessage);
        if(writePacket(JkProtocol.balancingCommand(enabled))){
            BluetoothGatt connection=gatt;
            handler.postDelayed(()->{if(gatt!=connection||!balanceBusy)return;jkAssembler.reset();balanceAwaiting=true;balanceMessage="Checking balancing setting…";listener.onBalancing(balanceMessage);if(writePacket(JkProtocol.readCommand(0x96)))handler.postDelayed(balanceTimeout,6000);},400);
        }
    }
    private void pauseForBalancing(){handler.removeCallbacks(poll);handler.removeCallbacks(requestTimeout);pending=-1;jkAssembler.reset();}
    private final Runnable balanceTimeout=()->finishBalancing(false,balanceWriteSent?"Balancing change could not be confirmed. Read the setting again; the battery may have changed.":"No supported balancing setting was returned. Live readings continue.");
    private void finishBalancing(boolean success,String message){
        handler.removeCallbacks(balanceTimeout);boolean wrote=balanceWriteSent;balanceBusy=false;balanceWriteSent=false;balanceAwaiting=false;if(!success){jkBalanceSettings=null;jkBalanceAt=0;}balanceMessage=message;listener.onLog(message);if(wrote)listener.onParameterEvent(message);listener.onBalancing(message);if(ready)handler.postDelayed(poll,200);
    }
    private void receivedBalanceSettings(byte[] frame){
        if(basic==null||jkDetectedVariant.isEmpty())return;
        try{
            JkProtocol.BalanceSettings value=JkProtocol.balanceSettings(frame,jkDetectedVariant,basic);
            if(balanceBusy&&!balanceAwaiting)return;
            if(jkBalanceSettings!=null&&value.enabled==jkBalanceSettings.enabled&&value.startVoltage==jkBalanceSettings.startVoltage&&value.differenceMv==jkBalanceSettings.differenceMv)value=jkBalanceSettings;
            jkBalanceSettings=value;jkBalanceAt=System.currentTimeMillis();
            if(balanceBusy){if(balanceWriteSent&&value.enabled!=balanceDesired)finishBalancing(false,"The battery did not confirm the requested balancing setting. Read it again.");else finishBalancing(true,(value.enabled?"Balancing enabled":"Balancing disabled")+" · confirmed by battery");}
            else{balanceMessage=(value.enabled?"Balancing enabled":"Balancing disabled")+" · read from battery";listener.onBalancing(balanceMessage);}
        }catch(IllegalArgumentException e){listener.onLog("Balancing setting unavailable: "+e.getMessage());}
    }
    public String connectionDiagnostics(){
        StringBuilder out=new StringBuilder("Protocol: "+protocol+"\nNotification packets: "+rxCount+"\n");
        if(jk){
            out.append("JK checksum rejects: ").append(jkAssembler.rejected).append("\nJK cell frames: ").append(jkCellFrames).append("\nJK accepted frames: ").append(jkAcceptedFrames).append("\nDetected JK format: ").append(jkDetectedVariant.isEmpty()?"Unknown":jkDetectedVariant).append("\nLast JK decode: ").append(jkLastError.isEmpty()?"None":jkLastError).append('\n');
            out.append("Rejected JK telemetry only (type 0x02); identity/settings packets are excluded.\n");
            if(jkFirstRejected!=null)out.append("First rejected telemetry hex: ").append(JbdProtocol.hex(jkFirstRejected).replace(" ","")).append('\n');
            if(jkLastRejected!=null&&!Arrays.equals(jkFirstRejected,jkLastRejected))out.append("Last rejected telemetry hex: ").append(JbdProtocol.hex(jkLastRejected).replace(" ","")).append('\n');
        }
        return out.toString();
    }
    public void readParameters(String password){beginParameters(password,null,null,null);}
    public void saveParameter(JbdSettings.Snapshot snapshot,JbdSettings.Field field,String value,String password){
        if(snapshot==null||snapshot!=parameters)throw new IllegalArgumentException("Read parameters from this connection before editing");
        beginParameters(password,snapshot,field,value);
    }
    private void beginParameters(String password,JbdSettings.Snapshot expected,JbdSettings.Field field,String value){
        if(!isJbdReady())throw new IllegalArgumentException("Connect to a JBD battery with valid readings first");
        if(operationsBusy())throw new IllegalArgumentException("A battery operation is already running");
        JbdSettings.Snapshot basis=new JbdSettings.Snapshot(deviceAddress,deviceModel,basic.versionText(),basic.cellCount,basic.capacityAh,basic.extendedScale,System.currentTimeMillis(),Collections.emptyMap());
        parameterSession=field==null?new JbdSettingsSession(basis,password):new JbdSettingsSession(basis,password,expected,field,value);
        parameterStarted=false;parameterDeadline=System.currentTimeMillis()+10000;handler.removeCallbacks(poll);
        parameterMessage="Waiting for the current battery read…";listener.onParameters(null,parameterMessage);handler.post(startParameters);
    }
    private final Runnable startParameters=new Runnable(){@Override public void run(){
        if(parameterSession==null)return;
        if(pending>=0){if(System.currentTimeMillis()>parameterDeadline)applyParameterAction(parameterSession.interrupted());else handler.postDelayed(this,100);return;}
        parameterStarted=true;handler.removeCallbacks(requestTimeout);parameterAssembler.reset();applyParameterAction(parameterSession.start());
    }};
    private final Runnable parameterTimeout=()->{if(parameterSession!=null)applyParameterAction(parameterSession.timeout());};
    private void applyParameterAction(JbdSettingsSession.Action action){
        if(action==null||parameterSession==null)return;
        handler.removeCallbacks(parameterTimeout);parameterAwaiting=false;JbdSettingsSession current=parameterSession;parameterMessage=action.message;
        if(action.complete){
            boolean wrote=current.writeSent();parameterSession=null;parameterStarted=false;parameterAssembler.reset();parameters=action.success?action.snapshot:null;
            listener.onLog("Parameters: "+action.message);listener.onParameters(parameters,parameterMessage);
            if(wrote)listener.onParameterEvent(action.message);
            if(action.reconnect){fail(action.message);return;}
            if(ready&&pending<0)handler.postDelayed(poll,200);return;
        }
        listener.onParameters(null,parameterMessage);
        if(action.backup){boolean saved=new ParameterStore(context).before(current.beforeWrite());applyParameterAction(current.backupReady(saved));return;}
        if(action.packet!=null)handler.postDelayed(()->{
            if(parameterSession!=current||!parameterStarted)return;
            parameterAssembler.reset();parameterAwaiting=true;
            // Settings passwords and their wire packets are never logged.
            if(writePacket(action.packet))handler.postDelayed(parameterTimeout,4000);
        },120);
    }
    public void setPollSeconds(int seconds){pollDelay=Math.max(5,Math.min(30,seconds))*1000L;}
    public void refresh(){if(ready&&pending<0&&authentication==null&&!operationsBusy()){handler.removeCallbacks(poll);request(3);}}
    private final Runnable requestTimeout=this::timedOut;
    private final Runnable connectionTimeout=()->fail("Connection timed out. Close other BMS apps and check the battery is awake.");
    private final Runnable poll=()->request(3);
    private final Runnable stopScanTask=this::stopScan;
    public BleClient(Context context,Listener listener) { this.context=context.getApplicationContext();this.listener=listener; }
    public BluetoothAdapter adapter() { BluetoothManager m=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);return m==null?null:m.getAdapter(); }
    public boolean hasPermission() { return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; }
    public boolean isReady() { return ready; }
    public boolean isScanning() { return scanning; }
    public boolean isConnectingOrConnected() { return connecting||gatt!=null; }
    public void startScan() {
        stopScan();
        if(!hasPermission()) { listener.onState("Allow Nearby devices to scan.",false);return; }
        try {
            BluetoothAdapter a=adapter();
            if(a==null || !a.isEnabled()) { listener.onState("Turn on Bluetooth to find your BMS.",false);return; }
            scanner=a.getBluetoothLeScanner();
            if(scanner==null) { listener.onState("Bluetooth scanning is unavailable.",false);return; }
            scanning=true;
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            listener.onState("Scanning for nearby Bluetooth batteries…",false);
            handler.postDelayed(stopScanTask,12000);
        }catch(SecurityException|IllegalStateException e){ scanning=false;listener.onState("Cannot scan: check Nearby devices permission and Bluetooth.",false); }
    }
    public void stopScan() {
        handler.removeCallbacks(stopScanTask);
        if(scanning && scanner!=null)try{scanner.stopScan(scanCallback);}catch(SecurityException|IllegalStateException ignored){}
        boolean was=scanning;scanning=false;
        if(was)listener.onState("Scan complete. Tap your BMS to connect.",ready);
    }
    private final ScanCallback scanCallback=new ScanCallback() {
        @Override public void onScanResult(int type,ScanResult result) { handler.post(()->acceptScan(result)); }
        @Override public void onBatchScanResults(List<ScanResult> results) { for(ScanResult r:results)handler.post(()->acceptScan(r)); }
        @Override public void onScanFailed(int error) { handler.post(()->{stopScan();listener.onState("Bluetooth scan failed ("+error+"). Wait a moment and scan again.",false);}); }
    };
    private void acceptScan(ScanResult r) {
        if(!scanning)return;
        try {
            ScanRecord record=r.getScanRecord();
            String name=record==null?null:record.getDeviceName();
            if(name==null)name=r.getDevice().getName();
            if(name==null || name.trim().isEmpty())name="Unnamed Bluetooth device";
            String lower=name.toLowerCase(Locale.ROOT);
            boolean likely=lower.contains("jbd") || lower.contains("xiaoxiang") || lower.contains("sp24") || lower.contains("bms") || lower.contains("liontron") || lower.contains("overkill") || lower.startsWith("jk");
            if(record!=null && record.getServiceUuids()!=null)likely |= record.getServiceUuids().contains(new ParcelUuid(SERVICE));
            byte[] victron=record==null?null:record.getManufacturerSpecificData(0x02e1);if(victron!=null)listener.onAdvertisement(r.getDevice(),name,r.getRssi(),victron.clone());
            listener.onDevice(r.getDevice(),name,r.getRssi(),likely);
        }catch(SecurityException ignored){}
    }
    public void connect(BluetoothDevice device,String name,String password) {
        connect(device,name,password,"Auto");
    }
    public void connect(BluetoothDevice device,String name,String password,String protocol) {
        stopScan();disconnect();
        this.protocol=protocol;jk=false;jkAssembler.reset();jkWriteChannels.clear();jkWriteChannel=0;jkWriteWithResponse=false;jkLastError="";jkDetectedVariant="";jkCellFrames=0;jkAcceptedFrames=0;jkFirstRejected=null;jkLastRejected=null;
        if(!hasPermission()){listener.onState("Allow Nearby devices to connect.",false);return;}
        try{JbdAuthentication.validatePassword(password);deviceAddress=device.getAddress();deviceName=name+" ["+deviceAddress+"]";}
        catch(IllegalArgumentException|SecurityException e){listener.onState("Check the battery password and Nearby devices permission.",false);return;}
        connectionPassword=password==null?"":password;
        basic=null;deviceModel="";parameters=null;assembler.reset();authAssembler.reset();modelAttempted=false;rxCount=0;
        // Publish the attempt before the callback: connectGatt has not returned a handle yet.
        // The monitoring service uses this state to distinguish progress from disconnection.
        connecting=true;
        listener.onModel("");listener.onState("Connecting to "+name+"…",false);
        listener.onLog("Connecting to "+name);
        try { gatt=device.connectGatt(context,false,gattCallback,BluetoothDevice.TRANSPORT_LE);if(gatt==null){fail("Android could not open the Bluetooth connection. Try Connect again.");return;}handler.postDelayed(connectionTimeout,18000); }
        catch(SecurityException|IllegalArgumentException e){fail("Could not start Bluetooth connection.");}
    }
    public void disconnect() {
        boolean interruptedBalance=balanceBusy&&balanceWriteSent;balanceBusy=false;balanceWriteSent=false;balanceAwaiting=false;jkBalanceSettings=null;jkBalanceAt=0;lastReadingAt=0;balanceMessage=interruptedBalance?"Connection closed before the balancing change was confirmed. Read it again.":"Read the balancing setting from the battery";
        if(interruptedBalance){listener.onParameterEvent(balanceMessage);listener.onBalancing(balanceMessage);}
        if(parameterSession!=null){JbdSettingsSession oldSession=parameterSession;boolean started=parameterStarted;parameterSession=null;parameterStarted=false;parameters=null;parameterMessage=oldSession.interrupted().message;listener.onParameters(null,parameterMessage);if(oldSession.writeSent())listener.onParameterEvent(parameterMessage);
            // Best effort only if a link still exists; never commit or retry an interrupted write.
            if(started&&gatt!=null&&tx!=null&&transportReady)try{byte[] exit=JbdSettings.exit(false);int type=(tx.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;if(Build.VERSION.SDK_INT>=33)gatt.writeCharacteristic(tx,exit,type);else{tx.setWriteType(type);tx.setValue(exit);gatt.writeCharacteristic(tx);}}catch(RuntimeException ignored){}
        }
        parameters=null;parameterAssembler.reset();
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(connectionTimeout);handler.removeCallbacks(requestTimeout);handler.removeCallbacks(poll);
        connecting=false;ready=false;transportReady=false;pending=-1;tx=null;basic=null;assembler.reset();authAssembler.reset();authentication=null;jkWriteChannels.clear();
        connectionPassword="";subscriptions.clear();notifyUuids.clear();subscriptionIndex=0;
        BluetoothGatt old=gatt;gatt=null;
        if(old!=null){try{old.disconnect();old.close();}catch(SecurityException ignored){}listener.onState("Disconnected · readings are no longer live",false);}
    }
    private void fail(String status) { listener.onLog(status);disconnect();listener.onState(status,false); }
    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state) { handler.post(()->{
            if(g!=gatt)return;
            if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth connection failed ("+status+"). Another BMS app or Home Assistant may be connected.");return;}
            if(state==BluetoothProfile.STATE_CONNECTED){connecting=false;listener.onState("Connected · checking JBD services…",false);try{if(!g.discoverServices())fail("Could not discover Bluetooth services.");}catch(SecurityException e){fail("Bluetooth permission was removed.");}}
            else if(state==BluetoothProfile.STATE_DISCONNECTED)fail("Battery disconnected. Tap Connect to try again.");
        }); }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status) { handler.post(()->{
            if(g!=gatt)return;
            if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth service discovery failed.");return;}
            BluetoothGattService s=g.getService(SERVICE);BluetoothGattService jkService=g.getService(JK_SERVICE);
            if(protocol.startsWith("JK")||(s==null&&protocol.equals("Auto")&&jkService!=null)){s=jkService;jk=true;}
            if(s==null){StringBuilder services=new StringBuilder();for(BluetoothGattService sv:g.getServices())services.append(sv.getUuid()).append(' ');listener.onLog("Services: "+services);fail("This device does not expose the supported JBD Bluetooth service. Open Details for diagnostics.");return;}
            BluetoothGattCharacteristic rx=s.getCharacteristic(RX);tx=s.getCharacteristic(TX);
            if(jk){
                rx=null;tx=null;jkWriteChannels.clear();
                for(UUID channel:new UUID[]{JK_IO,JK_TX})for(BluetoothGattCharacteristic c:s.getCharacteristics())if(c.getUuid().equals(channel)){
                    if(rx==null&&(c.getProperties()&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))!=0)rx=c;
                    if((c.getProperties()&(BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE))!=0)jkWriteChannels.add(c);
                }
                if(!jkWriteChannels.isEmpty())tx=jkWriteChannels.get(0);
                jkWriteChannel=0;jkWriteWithResponse=false;
            }
            if(tx==null){fail("JBD transmit Bluetooth characteristic is missing.");return;}
            subscriptions.clear();notifyUuids.clear();subscriptionIndex=0;
            for(BluetoothGattCharacteristic c:s.getCharacteristics())listener.onLog("Characteristic "+c.getUuid()+" properties 0x"+Integer.toHexString(c.getProperties()));
            for(BluetoothGattCharacteristic c:new BluetoothGattCharacteristic[]{rx,tx}){
                if(c!=null && (c.getProperties()&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))!=0 && c.getDescriptor(CCCD)!=null&&!subscriptions.contains(c))subscriptions.add(c);
            }
            if(subscriptions.isEmpty()){fail("The BMS has no supported Bluetooth notification channel.");return;}
            subscribeNext();
        }); }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){handler.post(()->{
            if(g!=gatt || !d.getUuid().equals(CCCD) || subscriptionIndex>=subscriptions.size())return;
            BluetoothGattCharacteristic c=subscriptions.get(subscriptionIndex);
            if(!d.getCharacteristic().getUuid().equals(c.getUuid()))return;
            if(status==BluetoothGatt.GATT_SUCCESS){notifyUuids.add(c.getUuid());listener.onLog("Notifications enabled on "+c.getUuid());}
            else{listener.onLog("Notification subscription failed on "+c.getUuid()+" ("+status+")");if(notifyUuids.isEmpty() && subscriptionIndex==subscriptions.size()-1){fail("BMS notification subscription failed.");return;}}
            subscriptionIndex++;handler.postDelayed(()->subscribeNext(),100);
        });}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handler.post(()->{
            if(g==gatt && status!=BluetoothGatt.GATT_SUCCESS)fail("Bluetooth request failed ("+status+").");
        });}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){byte[] copy=value.clone();handler.post(()->received(g,c.getUuid(),copy));}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] value=c.getValue();if(value!=null){byte[] copy=value.clone();handler.post(()->received(g,c.getUuid(),copy));}}
    };
    private void subscribeNext(){
        if(gatt==null)return;
        if(subscriptionIndex>=subscriptions.size()){
            if(notifyUuids.isEmpty()){fail("Could not enable BMS notifications.");return;}
            handler.removeCallbacks(connectionTimeout);transportReady=true;
            if(jk){connectionPassword="";ready=true;listener.onState("JK connected · requesting readings",false);writePacket(JkProtocol.readCommand(0x97));handler.postDelayed(()->request(3),600);return;}
            authentication=new JbdAuthentication(deviceAddress,connectionPassword);connectionPassword="";
            applyAuthentication(authentication.start());return;
        }
        BluetoothGattCharacteristic c=subscriptions.get(subscriptionIndex);BluetoothGattDescriptor d=c.getDescriptor(CCCD);
        boolean indications=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0;
        try{
            boolean ok=gatt.setCharacteristicNotification(c,true);
            byte[] value=indications?BluetoothGattDescriptor.ENABLE_INDICATION_VALUE:BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            if(ok){if(Build.VERSION.SDK_INT>=33)ok=gatt.writeDescriptor(d,value)==BluetoothStatusCodes.SUCCESS;else{d.setValue(value);ok=gatt.writeDescriptor(d);}}
            if(!ok){listener.onLog("Could not subscribe on "+c.getUuid());subscriptionIndex++;handler.postDelayed(this::subscribeNext,100);}
        }catch(SecurityException e){fail("Bluetooth permission was removed.");}
    }
    private void applyAuthentication(JbdAuthentication.Action action){
        if(action==null)return;
        handler.removeCallbacks(requestTimeout);listener.onLog(action.message);listener.onState(action.message,false);
        if(action.failed){fail(action.message);return;}
        if(action.complete){authentication=null;authAssembler.reset();assembler.reset();ready=true;handler.postDelayed(poll,100);return;}
        String stage=authentication.stage().name();
        // Credentials and their encoded wire packets are never written to diagnostic logs.
        listener.onLog("Login TX stage "+stage+" (payload hidden)");
        if(writePacket(action.packet))handler.postDelayed(requestTimeout,4000);
    }
    private boolean writePacket(byte[] command){
        if(!transportReady || gatt==null || tx==null)return false;
        int properties=tx.getProperties();
        int type=jk&&jkWriteWithResponse?BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:(properties&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        if((properties&(BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE))==0){fail("BMS Bluetooth transmit characteristic is not writable.");return false;}
        try{
            boolean ok;
            if(Build.VERSION.SDK_INT>=33)ok=gatt.writeCharacteristic(tx,command,type)==BluetoothStatusCodes.SUCCESS;
            else{tx.setWriteType(type);tx.setValue(command);ok=gatt.writeCharacteristic(tx);}
            if(!ok)fail("Bluetooth request could not be sent. Reconnect the battery.");
            return ok;
        }catch(SecurityException e){fail("Bluetooth permission was removed.");return false;}
    }
    private void request(int register){if(operationsBusy())return;retries=0;pending=register;if(jk){if(writePacket(JkProtocol.readCommand(0x96))){handler.removeCallbacks(requestTimeout);handler.postDelayed(requestTimeout,8000);}return;}send();}
    private void send() {
        if(!ready || gatt==null || tx==null || pending<0)return;
        assembler.reset();
        byte[] command=JbdProtocol.readCommand(pending);
        listener.onLog("Telemetry read 0x"+Integer.toHexString(pending));
        if(writePacket(command)){handler.removeCallbacks(requestTimeout);handler.postDelayed(requestTimeout,pending==5?1800:3500);}
    }
    private void timedOut(){
        if(jk){
            if(rxCount==0&&jkWriteChannel+1<jkWriteChannels.size()){
                tx=jkWriteChannels.get(++jkWriteChannel);jkWriteWithResponse=false;retries=0;
                listener.onLog("JK read-only fallback: write channel "+tx.getUuid());
            }else if(rxCount==0&&!jkWriteWithResponse&&(tx.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0&&(tx.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0){
                jkWriteWithResponse=true;retries=0;listener.onLog("JK read-only fallback: acknowledged writes");
            }else if(++retries>2){
                if(!jkDetectedVariant.isEmpty()&&!jkLastError.isEmpty()&&!protocol.equals("Auto"))fail("JK format mismatch: profile uses "+protocol+", but these readings use "+jkDetectedVariant+". Reconnect and choose "+jkDetectedVariant+" or Auto. These names describe the data format, not the cell count.");
                else fail("No valid JK readings ("+rxCount+" packets, "+jkAssembler.rejected+" invalid frames). "+(jkLastError.isEmpty()?"Move close and close the other BMS app. ":"JK data arrived but its layout could not be decoded. ")+"Try Auto, then export connection diagnostics from Overview or Tools.");return;
            }
            if(writePacket(JkProtocol.readCommand(0x96)))handler.postDelayed(requestTimeout,8000);return;
        }
        if(authentication!=null){authAssembler.reset();assembler.reset();applyAuthentication(authentication.timeout());return;}
        if(pending==5){pending=-1;listener.onLog("Optional model read timed out; continuing measurements");handler.postDelayed(poll,pollDelay);return;}
        if(++retries<=2){listener.onLog("Read timed out; retry "+retries);send();return;}
        fail("Bluetooth connected, but no valid battery readings arrived. Check the password on Connect. Open App details and export connection diagnostics if it still fails. Received "+rxCount+" notification packet(s).");
    }
    private void received(BluetoothGatt g,UUID characteristic,byte[] value){
        if(g!=gatt || !transportReady || !notifyUuids.contains(characteristic))return;
        rxCount++;listener.onLog("RX packet "+rxCount+" · "+value.length+" bytes · "+characteristic);
        if(parameterSession!=null&&parameterStarted){if(!parameterAwaiting)return;for(JbdProtocol.Frame f:parameterAssembler.feed(value)){if(parameterSession==null||!parameterAwaiting)break;applyParameterAction(parameterSession.receive(f));}return;}
        if(jk){for(byte[] frame:jkAssembler.feed(value)){
            listener.onLog("JK frame type "+(frame[4]&255)+" · "+frame.length+" bytes");
            if((frame[4]&255)==3){listener.onModel(JkProtocol.model(frame));continue;}
            if((frame[4]&255)==1){receivedBalanceSettings(frame);continue;}
            if((frame[4]&255)!=2)continue;
            jkCellFrames++;
            try{JkProtocol.Sample sample=JkProtocol.parse(frame,protocol);handler.removeCallbacks(requestTimeout);handler.removeCallbacks(poll);pending=-1;jkLastError="";jkDetectedVariant=sample.basic.firmware.endsWith("32S")?"JK02_32S":"JK02_24S";jkAcceptedFrames++;basic=sample.basic;lastReadingAt=System.currentTimeMillis();listener.onReading(new Reading(lastReadingAt,sample.basic,sample.cells,deviceName));listener.onState("Live · JK Bluetooth",true);if(!balanceBusy)handler.postDelayed(poll,pollDelay);}
            catch(IllegalArgumentException e){jkLastError=e.getMessage();jkDetectedVariant=e instanceof JkProtocol.VariantMismatchException?((JkProtocol.VariantMismatchException)e).detectedVariant:"";if(jkFirstRejected==null)jkFirstRejected=frame.clone();jkLastRejected=frame.clone();listener.onLog("JK layout rejected: "+e.getMessage());}
        }return;}
        if(authentication!=null){
            if(authentication.isLegacy()){
                for(JbdProtocol.Frame f:assembler.feed(value))if(f.register==6){listener.onLog("Legacy login reply status 0x"+Integer.toHexString(f.status));JbdAuthentication.Action a=authentication.receiveLegacy(f.status);if(a!=null){handler.removeCallbacks(requestTimeout);handler.postDelayed(()->{if(g==gatt)applyAuthentication(a);},100);}}
            }else{
                for(JbdAuthentication.Frame f:authAssembler.feed(value)){
                    listener.onLog("Login reply command 0x"+Integer.toHexString(f.command)+" · "+f.data.length+" bytes");
                    JbdAuthentication.Action a=authentication.receive(f);
                    if(a!=null){handler.removeCallbacks(requestTimeout);handler.postDelayed(()->{if(g==gatt)applyAuthentication(a);},100);}
                }
            }
            return;
        }
        if(!ready)return;
        for(JbdProtocol.Frame f:assembler.feed(value)){
            if(f.register!=pending)continue;
            handler.removeCallbacks(requestTimeout);
            int register=pending;pending=-1;
            if(f.status!=0){listener.onLog("BMS status 0x"+Integer.toHexString(f.status)+" for read "+register);if(register==5){handler.postDelayed(poll,100);continue;}fail("The BMS refused a telemetry read (0x"+Integer.toHexString(f.status)+"). A password or a different protocol may be required.");return;}
            try {
                if(register==5){String model=JbdProtocol.parseModel(f.payload);deviceModel=model;listener.onModel(model);listener.onLog("BMS model: "+model);handler.postDelayed(poll,pollDelay);}
                else if(register==3){basic=JbdProtocol.parseBasic(f.payload);listener.onReading(new Reading(System.currentTimeMillis(),basic,new double[0],deviceName));handler.postDelayed(()->request(4),100);}
                else if(register==4 && basic!=null){double[] cells=JbdProtocol.parseCells(f.payload,basic.cellCount);listener.onReading(new Reading(System.currentTimeMillis(),basic,cells,deviceName));listener.onState("Live · Bluetooth only",true);if(!modelAttempted){modelAttempted=true;handler.postDelayed(()->request(5),100);}else handler.postDelayed(poll,pollDelay);}
            }catch(IllegalArgumentException e){fail("Invalid BMS data: "+e.getMessage()+". Open Details for diagnostics.");return;}
        }
        if(assembler.rejected>0)listener.onLog("Rejected corrupted Bluetooth frame(s): "+assembler.rejected);
    }
}
