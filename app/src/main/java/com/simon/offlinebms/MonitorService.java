package com.simon.offlinebms;

import android.Manifest;
import android.app.*;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Owns the Bluetooth connection independently of Activity and file-picker lifecycles. */
public final class MonitorService extends Service implements BleClient.Listener {
    public static final String CONNECT="com.simon.offlinebms.CONNECT",STOP="com.simon.offlinebms.STOP",VICTRON="com.simon.offlinebms.VICTRON";
    public final Map<String,EquipmentReading> equipment=new LinkedHashMap<>();
    public final Map<String,String> equipmentErrors=new HashMap<>();
    private final Map<String,Long> equipmentSaved=new HashMap<>();
    private VictronScanner victron;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final IBinder binder=new LocalBinder();
    public final class LocalBinder extends Binder { public MonitorService service(){return MonitorService.this;} }
    public BleClient ble;
    public Reading latest;
    public String status="Choose a battery to connect",model="",address="",name="";
    public EnergyStats sessionStats=new EnergyStats();
    public AlertEngine alarms=new AlertEngine();
    private HistoryStore history;
    private ProfileStore profiles;
    private SharedPreferences prefs;
    private BleClient.Listener observer;
    private String sessionId="";
    private boolean monitoring,closing,wasLive,staleAlert;
    private long lastSaved,lastNotification;
    private boolean diagnosticAttempt;
    private long diagnosticStarted;
    private final ArrayDeque<String> logs=new ArrayDeque<>();
    private final Runnable diagnosticCheckpoint=()->saveDiagnostics();
    private final Runnable ticker=new Runnable(){@Override public void run(){if(closing)return;renewWakeLock();if(monitoring&&latest!=null&&!ble.operationsBusy()){long age=System.currentTimeMillis()-latest.timestamp;long limit=Math.max(20000,prefs.getInt("poll_seconds",5)*3000L);if(age>limit&&!staleAlert){staleAlert=true;notifyAlert("Battery readings are stale","No fresh reading for "+age/1000+" seconds");event("connection","Readings became stale");}if(System.currentTimeMillis()-lastNotification>=10000)notification();}handler.postDelayed(this,5000);}};
    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("offline_bms",MODE_PRIVATE);profiles=new ProfileStore(this);history=new HistoryStore(this);ble=new BleClient(this,this);victron=new VictronScanner(this,new VictronScanner.Listener(){public void received(String a,String n,VictronProtocol.Data d){receivedEquipment(a,n,d);}public void error(String a,String message){equipmentErrors.put(a,message);onLog("Victron: "+message);if(!victron.running()&&!monitoring){releaseWakeLock();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}}});channels();handler.post(ticker);}
    @Override public IBinder onBind(Intent i){return binder;}
    public void observe(BleClient.Listener l){observer=l;}
    public void unobserve(BleClient.Listener l){if(observer==l)observer=null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null)return START_NOT_STICKY;
        if(STOP.equals(intent.getAction())){stopMonitoring();return START_NOT_STICKY;}
        if(VICTRON.equals(intent.getAction())){
            if(!ble.hasPermission()){status="Allow Nearby devices to read Victron devices";stopSelf();return START_NOT_STICKY;}
            try{ble.stopScan();equipmentErrors.clear();startForeground(1,buildNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);victron.start(profiles,new PasswordStore(this));renewWakeLock();notification();}
            catch(Exception e){victron.stop();status=e.getMessage()==null?"Victron monitoring could not start":e.getMessage();onLog(status);if(!monitoring){releaseWakeLock();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}if(observer!=null)observer.onState(status,false);}
            return START_NOT_STICKY;
        }
        if(CONNECT.equals(intent.getAction())){
            String target=intent.getStringExtra("address"),label=intent.getStringExtra("name"),password=intent.getStringExtra("password");intent.removeExtra("password");
            if(ble.operationsBusy()){onLog("Wait for the battery operation to finish before switching batteries");return START_NOT_STICKY;}
            if(!ble.hasPermission()){onState("Allow Nearby devices to connect",false);stopSelf();return START_NOT_STICKY;}
            if(monitoring){saveSession("Switched battery");monitoring=false;}ble.stopScan();ble.disconnect();
            address=target==null?"":target;name=label==null?"Saved battery":label;latest=null;model="";sessionId=UUID.randomUUID().toString();sessionStats=new EnergyStats();alarms=new AlertEngine();lastSaved=0;wasLive=false;staleAlert=false;status="Connecting to "+name;monitoring=true;
            diagnosticAttempt=true;diagnosticStarted=System.currentTimeMillis();logs.clear();
            try{startForeground(1,buildNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);renewWakeLock();ble.setPollSeconds(prefs.getInt("poll_seconds",5));ProfileStore.Profile p=profiles.get(address);ble.connect(ble.adapter().getRemoteDevice(address),name,password==null?"":password,p==null?"Auto":p.protocol);}catch(RuntimeException e){monitoring=false;status="Could not start monitoring. Check Bluetooth permission and try again.";onLog(status);saveDiagnostics();if(!victron.running()){releaseWakeLock();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}if(observer!=null)observer.onState(status,false);}
        }return START_NOT_STICKY;
    }
    public void stopMonitoring(){
        saveDiagnostics();
        boolean active=monitoring;monitoring=false;victron.stop();releaseWakeLock();ble.stopScan();ble.disconnect();
        if(active){saveSession("Stopped");event("connection","Monitoring stopped");}
        wasLive=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    public void stopVictronMonitoring(){
        victron.stop();equipmentErrors.clear();
        if(monitoring){notification();return;}
        releaseWakeLock();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    public boolean monitoring(){return monitoring;}
    public boolean victronRunning(){return victron.running();}
    public void settingsChanged(){ble.setPollSeconds(prefs.getInt("poll_seconds",5));alarms=new AlertEngine();notification();}
    @Override public void onDevice(BluetoothDevice d,String n,int r,boolean likely){if(observer!=null)observer.onDevice(d,n,r,likely);}
    @Override public void onAdvertisement(BluetoothDevice d,String n,int r,byte[] bytes){if(observer!=null)observer.onAdvertisement(d,n,r,bytes);}
    @Override public void onLog(String value){if(logs.size()>=200)logs.removeFirst();logs.addLast(new SimpleDateFormat("HH:mm:ss",Locale.UK).format(new Date())+" "+value);queueDiagnostics();if(observer!=null)observer.onLog(value);}
    private void queueDiagnostics(){if(diagnosticAttempt&&!closing&&!handler.hasCallbacks(diagnosticCheckpoint))handler.postDelayed(diagnosticCheckpoint,2000);}
    private String diagnosticSnapshot(){StringBuilder b=new StringBuilder("Offline BMS 1.2.8\nAttempt started: "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.UK).format(new Date(diagnosticStarted))+"\nSnapshot time: "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.UK).format(new Date())+"\nBattery: "+name+"\nAddress: "+address+"\nStatus: "+status+"\nModel: "+model+"\nMonitoring active: "+monitoring+"\nBluetooth attempt active: "+ble.isConnectingOrConnected()+"\n\n"+ble.connectionDiagnostics()+"\n");for(String l:logs)b.append(l).append('\n');return b.toString();}
    private void saveDiagnostics(){handler.removeCallbacks(diagnosticCheckpoint);if(diagnosticAttempt&&!closing)ConnectionDiagnostics.save(this,diagnosticSnapshot());}
    public String diagnostics(){if(!diagnosticAttempt)return ConnectionDiagnostics.last(this);saveDiagnostics();return diagnosticSnapshot();}
    @Override public void onModel(String value){model=value;queueDiagnostics();if(observer!=null)observer.onModel(value);}
    @Override public void onParameters(JbdSettings.Snapshot snapshot,String message){if(snapshot!=null)new ParameterStore(this).latest(snapshot);if(observer!=null)observer.onParameters(snapshot,message);if(!ble.operationsBusy()&&observer==null&&!prefs.getBoolean("background",true)&&monitoring)handler.post(this::stopMonitoring);}
    @Override public void onBalancing(String message){if(observer!=null)observer.onBalancing(message);if(!ble.operationsBusy()&&observer==null&&!prefs.getBoolean("background",true)&&monitoring)handler.post(this::stopMonitoring);}
    @Override public void onParameterEvent(String message){event("parameters",message);}
    @Override public void onState(String value,boolean ready){
        status=value;
        if(monitoring&&ready&&!wasLive){wasLive=true;event("connection","Battery connected");}
        if(monitoring&&!ready&&!ble.isConnectingOrConnected()&&!ble.isScanning()){
            if(wasLive){notifyAlert("Battery disconnected",value);event("connection",value);}
            monitoring=false;saveSession("Disconnected");wasLive=false;if(!victron.running()){releaseWakeLock();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
        }
        if(!value.startsWith("Disconnected ·")){if(!ready&&!ble.isConnectingOrConnected())saveDiagnostics();else queueDiagnostics();}
        if(monitoring)notification();if(observer!=null)observer.onState(value,ready);
    }
    @Override public void onReading(Reading r){
        if(r.isDemo||r.cells.length==0||!monitoring||!address.equalsIgnoreCase(r.address))return;latest=r;staleAlert=false;sessionStats.add(r.timestamp,r.basic.voltage,r.basic.current,sessionId);
        ProfileStore.Profile p=profiles.get(address);if(p!=null){AlertEngine.Sample sample=new AlertEngine.Sample(r.basic.soc,r.basic.protection,r.minCell(),r.maxCell(),r.deltaMv(),r.maxTemp());for(String alert:alarms.update(p.alerts,sample)){event("alert",alert);notifyAlert(alert,name+" • "+r.basic.soc+"% • "+String.format(Locale.UK,"%.2f V",r.basic.voltage));}}
        int interval=Math.max(prefs.getInt("sample_seconds",10),prefs.getInt("poll_seconds",5));
        if(prefs.getBoolean("record",true)&&r.timestamp-lastSaved>=interval*1000L){lastSaved=r.timestamp;String session=sessionId;int days=prefs.getInt("retention_days",30);String target=address,label=name;EnergyStats snapshot=statsCopy();io.execute(()->{try{history.add(r,session,days);history.session(session,target,label,snapshot,"Recorded");}catch(Exception e){handler.post(()->{onLog("Local recording failed; live monitoring continues");if(observer!=null)observer.onState("Live · could not save this reading",true);});}});}
        notification();if(observer!=null)observer.onReading(r);
    }
    private EnergyStats statsCopy(){EnergyStats s=new EnergyStats();s.samples=sessionStats.samples;s.firstTime=sessionStats.firstTime;s.lastTime=sessionStats.lastTime;s.observedMs=sessionStats.observedMs;s.chargeWh=sessionStats.chargeWh;s.dischargeWh=sessionStats.dischargeWh;s.chargeAh=sessionStats.chargeAh;s.dischargeAh=sessionStats.dischargeAh;s.minVoltage=sessionStats.minVoltage;s.maxVoltage=sessionStats.maxVoltage;s.peakChargeW=sessionStats.peakChargeW;s.peakDischargeW=sessionStats.peakDischargeW;return s;}
    private void saveSession(String reason){if(!prefs.getBoolean("record",true)||sessionStats.samples==0)return;String session=sessionId,target=address,label=name;EnergyStats snapshot=statsCopy();io.execute(()->{try{history.session(session,target,label,snapshot,reason);}catch(Exception ignored){}});}
    private void event(String kind,String text){if(address.isEmpty()||!prefs.getBoolean("record",true))return;String target=address;io.execute(()->{try{history.event(target,kind,text);}catch(Exception ignored){}});}
    private void channels(){NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("monitor","Battery monitoring",NotificationManager.IMPORTANCE_LOW));nm.createNotificationChannel(new NotificationChannel("alerts","Battery alerts",NotificationManager.IMPORTANCE_DEFAULT));}
    private PendingIntent openIntent(){return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification buildNotification(){String content=victron!=null&&victron.running()?"Reading Victron equipment • "+equipment.size()+" device(s) received":status;if(monitoring&&latest!=null){long age=Math.max(0,(System.currentTimeMillis()-latest.timestamp)/1000);content=latest.basic.soc+"% • "+String.format(Locale.UK,"%.2f V • %+.0f W",latest.basic.voltage,latest.basic.power())+(age>Math.max(20,prefs.getInt("poll_seconds",5)*3)?" • STALE":"");}
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MonitorService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"monitor").setSmallIcon(R.drawable.ic_battery).setContentTitle(monitoring&&!name.isEmpty()?name:"Offline BMS • Equipment").setContentText(content).setContentIntent(openIntent()).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(null,"Stop monitoring",stop).build()).build();
    }
    private void notification(){if(!monitoring&&!victron.running())return;lastNotification=System.currentTimeMillis();getSystemService(NotificationManager.class).notify(1,buildNotification());}
    private void renewWakeLock(){if(!monitoring&&!victron.running())return;if(wakeLock==null){wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OfflineBMS:monitoring");wakeLock.setReferenceCounted(false);}wakeLock.acquire(120000);}
    private void releaseWakeLock(){if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}
    private void receivedEquipment(String target,String label,VictronProtocol.Data data){ProfileStore.Profile p=profiles.get(target);EquipmentReading r=new EquipmentReading(System.currentTimeMillis(),target,p==null?label:p.name,data);equipment.put(target,r);equipmentErrors.remove(target);if(prefs.getBoolean("record",true)&&r.timestamp-equipmentSaved.getOrDefault(target,0L)>=Math.max(5,prefs.getInt("sample_seconds",10))*1000L){equipmentSaved.put(target,r.timestamp);int days=prefs.getInt("retention_days",30);io.execute(()->{try{history.equipment(r,days);}catch(Exception e){handler.post(()->onLog("Victron reading could not be saved"));}});}notification();}
    private void notifyAlert(String title,String message){ProfileStore.Profile p=profiles.get(address);if(p==null||!p.alerts.enabled)return;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;Notification n=new Notification.Builder(this,"alerts").setSmallIcon(R.drawable.ic_battery).setContentTitle(name+" • "+title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(openIntent()).setAutoCancel(true).build();getSystemService(NotificationManager.class).notify(100+Math.floorMod(title.hashCode(),10000),n);}
    @Override public void onDestroy(){saveDiagnostics();closing=true;handler.removeCallbacksAndMessages(null);observer=null;if(monitoring)saveSession("Service stopped");monitoring=false;victron.stop();releaseWakeLock();ble.stopScan();ble.disconnect();io.execute(history::close);io.shutdown();super.onDestroy();}
}
