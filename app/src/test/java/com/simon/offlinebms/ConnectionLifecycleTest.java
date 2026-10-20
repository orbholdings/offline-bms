package com.simon.offlinebms;

import android.Manifest;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Actual service/client startup, with a simulated Bluetooth transport and full reading. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,35})
@LooperMode(LooperMode.Mode.PAUSED)
public class ConnectionLifecycleTest {
    private static final String ADDRESS="AA:BB:CC:DD:EE:01";
    private static void enableBluetooth() {
        Application app=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS);
        Shadows.shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true);
    }
    @Test public void publishesActiveAttemptBeforeConnectingCallback() throws Exception {
        enableBluetooth();final BleClient[] client=new BleClient[1];final boolean[] observed=new boolean[3];
        client[0]=new BleClient(RuntimeEnvironment.getApplication(),new BleClient.Listener(){
            public void onState(String status,boolean ready){if(status.startsWith("Connecting to")){
                observed[0]=true;observed[1]=client[0].isConnectingOrConnected();
                try{observed[2]=get(client[0],"gatt")==null;}catch(Exception e){throw new AssertionError(e);}
            }}
            public void onDevice(BluetoothDevice d,String n,int r,boolean likely){}
            public void onReading(Reading r){}
            public void onLog(String value){}
            public void onModel(String value){}
        });
        try {
            client[0].connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(ADDRESS),"Test battery","");
            assertTrue("Connecting callback must be published",observed[0]);
            assertTrue("Test must cover the interval before connectGatt returns its handle",observed[2]);
            assertTrue("A connection attempt must already be active during the connecting callback",observed[1]);
        } finally {client[0].disconnect();}
    }
    @Test public void startupKeepsMonitoringAndAcceptsFullReading() throws Exception {
        try(Session session=new Session()) {
            session.start();
            assertTrue("Connecting must not stop the monitoring service",session.service.monitoring());
            Reading sample=sample();session.service.onReading(sample);
            assertSame("A full reading after startup must reach the dashboard/recorder",sample,session.service.latest);
            assertEquals(1,session.service.sessionStats.samples);
        }
    }
    @Test public void realConnectionFailureStillStopsMonitoring() throws Exception {
        try(Session session=new Session()) {
            session.start();
            BluetoothGatt g=(BluetoothGatt)get(session.service.ble,"gatt");assertNotNull(g);
            BluetoothGattCallback callback=(BluetoothGattCallback)get(session.service.ble,"gattCallback");
            callback.onConnectionStateChange(g,133,BluetoothProfile.STATE_DISCONNECTED);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(session.service.monitoring());
            assertFalse(session.service.ble.isConnectingOrConnected());
            assertTrue(session.service.status.contains("133"));
        }
    }
    @Test public void explicitStopClearsAnInFlightConnection() throws Exception {
        try(Session session=new Session()) {
            session.start();session.service.stopMonitoring();
            assertFalse(session.service.monitoring());
            assertFalse("Stopping must clear the early connecting state as well as GATT",session.service.ble.isConnectingOrConnected());
            session.service.onReading(sample());assertNull(session.service.latest);
        }
    }
    @Test public void rejectedJkDiagnosticsSurviveServiceRecreationAndExcludeCredentials() throws Exception {
        String expectedFirst="",expectedLast="";
        try(Session session=new Session()){
            session.start();BleClient ble=session.service.ble;
            set(ble,"jk",true);set(ble,"ready",true);set(ble,"transportReady",true);
            BluetoothGattCharacteristic rx=new BluetoothGattCharacteristic(BleClient.JK_IO,BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,BluetoothGattCharacteristic.PERMISSION_WRITE);
            set(ble,"tx",rx);@SuppressWarnings("unchecked") Set<UUID> notifications=(Set<UUID>)get(ble,"notifyUuids");notifications.add(BleClient.JK_IO);
            BluetoothGatt g=(BluetoothGatt)get(ble,"gatt");Method received=BleClient.class.getDeclaredMethod("received",BluetoothGatt.class,UUID.class,byte[].class);received.setAccessible(true);
            String secret="PRIVATE123";byte[] identity=jkFrame(3);System.arraycopy("JK-TEST".getBytes(StandardCharsets.US_ASCII),0,identity,6,7);System.arraycopy("11.24".getBytes(StandardCharsets.US_ASCII),0,identity,30,5);System.arraycopy(secret.getBytes(StandardCharsets.US_ASCII),0,identity,62,secret.length());checksum(identity);received.invoke(ble,g,BleClient.JK_IO,identity);
            byte[] settings=jkFrame(1);System.arraycopy(secret.getBytes(StandardCharsets.US_ASCII),0,settings,100,secret.length());checksum(settings);received.invoke(ble,g,BleClient.JK_IO,settings);
            for(int i=0;i<40;i++){byte[] invalid=jkFrame(2);invalid[5]=(byte)i;invalid[6]=(byte)0xff;invalid[7]=(byte)0xff;checksum(invalid);received.invoke(ble,g,BleClient.JK_IO,invalid);String hex=JbdProtocol.hex(invalid).replace(" ","");if(i==0)expectedFirst=hex;if(i==39)expectedLast=hex;}
            set(ble,"retries",2);Method timeout=BleClient.class.getDeclaredMethod("timedOut");timeout.setAccessible(true);timeout.invoke(ble);
            assertFalse(session.service.monitoring());String report=session.service.diagnostics();
            assertTrue(report.contains("JK-TEST"));assertTrue(report.contains("11.24"));assertTrue(report.contains("JK cell frames: 40"));assertTrue(report.contains("JK accepted frames: 0"));
            assertTrue(report.contains("JK02_24S:"));assertTrue(report.contains("JK02_32S:"));assertTrue(report.contains(expectedFirst));assertTrue(report.contains(expectedLast));
            assertEquals(1,occurrences(report,"First rejected telemetry hex:"));assertEquals(1,occurrences(report,"Last rejected telemetry hex:"));
            assertFalse(report.contains(secret));assertFalse(report.contains(JbdProtocol.hex(secret.getBytes(StandardCharsets.US_ASCII)).replace(" ","")));
        }
        ServiceController<MonitorService> reopened=Robolectric.buildService(MonitorService.class).create();
        try{
            String saved=reopened.get().diagnostics();assertTrue(saved.contains("SAVED CONNECTION ATTEMPT"));assertTrue(saved.contains("No valid JK readings"));assertTrue(saved.contains(expectedFirst));assertTrue(saved.contains(expectedLast));assertFalse(saved.contains("Choose a battery to connect"));
        }finally{reopened.destroy();((ExecutorService)get(reopened.get(),"io")).awaitTermination(10,TimeUnit.SECONDS);}
    }
    @Test public void diagnosticStoreIsBoundedAndIdleServicesKeepPreviousAttempt() throws Exception {
        enableBluetooth();Context app=RuntimeEnvironment.getApplication();ConnectionDiagnostics.save(app,"Last failure\n"+"x".repeat(100000));
        assertTrue(ConnectionDiagnostics.last(app).length()<66000);assertTrue(ConnectionDiagnostics.last(app).contains("Diagnostic log truncated"));
        ServiceController<MonitorService> idle=Robolectric.buildService(MonitorService.class).create();try{assertTrue(idle.get().diagnostics().contains("Last failure"));}finally{idle.destroy();((ExecutorService)get(idle.get(),"io")).awaitTermination(10,TimeUnit.SECONDS);}
        assertTrue(ConnectionDiagnostics.last(app).contains("Last failure"));
    }
    private static byte[] jkFrame(int type){byte[] f=new byte[300];f[0]=0x55;f[1]=(byte)0xaa;f[2]=(byte)0xeb;f[3]=(byte)0x90;f[4]=(byte)type;return f;}
    private static void checksum(byte[] frame){int sum=0;for(int i=0;i<299;i++)sum+=frame[i]&255;frame[299]=(byte)sum;}
    private static int occurrences(String text,String part){return (text.length()-text.replace(part,"").length())/part.length();}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Reading sample(){Reading demo=Reading.demo();return new Reading(System.currentTimeMillis(),demo.basic,demo.cells,"Test battery ["+ADDRESS+"]");}
    private static Object get(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static final class Session implements AutoCloseable {
        final ServiceController<MonitorService> controller;
        final MonitorService service;
        Session(){enableBluetooth();RuntimeEnvironment.getApplication().getSharedPreferences("offline_bms",Context.MODE_PRIVATE).edit().clear().putBoolean("record",false).commit();controller=Robolectric.buildService(MonitorService.class).create();service=controller.get();}
        void start(){service.onStartCommand(new Intent(service,MonitorService.class).setAction(MonitorService.CONNECT).putExtra("address",ADDRESS).putExtra("name","Test battery").putExtra("password",""),0,1);}
        public void close()throws Exception{controller.destroy();((ExecutorService)get(service,"io")).awaitTermination(10,TimeUnit.SECONDS);}
    }
}
