package com.simon.offlinebms;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Exercises actual service/GATT lifecycles; the radio and returned telemetry are simulated. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,35},shadows={AutoReconnectTest.GattShadow.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class AutoReconnectTest {
    private static final String ADDRESS="AA:BB:CC:DD:EE:01",OTHER="AA:BB:CC:DD:EE:02";
    /** Robolectric 4.14.1 models only the legacy descriptor-write overload. */
    @Implements(BluetoothGatt.class)
    public static class GattShadow extends org.robolectric.shadows.ShadowBluetoothGatt {
        @Implementation(minSdk=33) protected int writeDescriptor(BluetoothGattDescriptor descriptor,byte[] value){
            descriptor.setValue(value);return super.writeDescriptor(descriptor)?BluetoothStatusCodes.SUCCESS:BluetoothStatusCodes.ERROR_UNKNOWN;
        }
    }

    @Test public void losingRangeWaitsWithoutKeepingTheCpuAwake() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);set(s.service.ble,"ready",true);s.service.onReading(sample());s.service.onState("Live",true);
            String firstSession=(String)get(s.service,"sessionId");BluetoothGatt original=s.gatt();s.disconnect(0);
            assertTrue(s.service.monitoring());assertTrue(s.service.waitingForBattery());assertNull(s.service.latest);
            assertTrue(Shadows.shadowOf(original).isClosed());assertTrue(s.service.status.contains("reconnecting automatically"));
            assertFalse(((PowerManager.WakeLock)get(s.service,"wakeLock")).isHeld());assertNotEquals(firstSession,get(s.service,"sessionId"));assertEquals(0,s.service.sessionStats.samples);
            s.service.onReading(sample());assertNull("A late reading while waiting must not appear live",s.service.latest);
            advance(5);BluetoothGatt waiting=s.gatt();assertNotNull(waiting);assertTrue(s.service.ble.isWaitingForDevice());
            advance(120);assertSame("Waiting uses one availability-based GATT request",waiting,s.gatt());assertTrue(s.service.monitoring());assertTrue(s.service.waitingForBattery());
        }
    }
    @Test public void initialOutOfRangeFailureAlsoWaitsForTheDevice() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);s.disconnect(133);assertTrue(s.service.waitingForBattery());advance(5);assertTrue(s.service.ble.isWaitingForDevice());
            assertTrue(s.prefs().getBoolean("resume_monitoring",false));assertEquals(ADDRESS,s.prefs().getString("resume_address",""));
        }
    }
    @Test public void returningBatteryCompletesHandshakeAndDeliversFreshJkReadings() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);s.disconnect(0);advance(5);BluetoothGatt g=s.gatt();BluetoothGattCallback callback=s.callback();
            BluetoothGattService service=new BluetoothGattService(BleClient.JK_SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);
            BluetoothGattCharacteristic rx=new BluetoothGattCharacteristic(BleClient.JK_IO,BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE);
            BluetoothGattDescriptor descriptor=new BluetoothGattDescriptor(BleClient.CCCD,BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE);rx.addDescriptor(descriptor);service.addCharacteristic(rx);
            Shadows.shadowOf(g).addDiscoverableService(service);Shadows.shadowOf(g).allowCharacteristicNotification(rx);
            callback.onConnectionStateChange(g,0,BluetoothProfile.STATE_CONNECTED);idle();assertFalse(s.service.waitingForBattery());assertNull(s.service.latest);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS);
            assertTrue(s.service.diagnostics(),(Boolean)get(s.service.ble,"transportReady"));assertNull("Transport ready is not a fresh reading",s.service.latest);
            byte[] frame=jkFrame();if(Build.VERSION.SDK_INT>=33)callback.onCharacteristicChanged(g,rx,frame);else{rx.setValue(frame);callback.onCharacteristicChanged(g,rx);}idle();
            assertTrue(s.service.ble.isReady());assertNotNull(s.service.latest);assertEquals(90,s.service.latest.basic.soc);assertEquals(13.243,s.service.latest.basic.voltage,.000001);assertEquals(4,s.service.latest.cells.length);assertEquals(1,s.service.sessionStats.samples);assertEquals(0,s.service.sessionStats.observedMs);
            assertTrue(((PowerManager.WakeLock)get(s.service,"wakeLock")).isHeld());
        }
    }
    @Test public void explicitStopCancelsPendingAndActiveReconnects() throws Exception {
        for(boolean requestAlreadyStarted:new boolean[]{false,true})try(Session s=new Session()){
            s.start(ADDRESS);s.disconnect(0);if(requestAlreadyStarted)advance(5);BluetoothGatt waiting=s.gatt();s.service.stopMonitoring();advance(120);
            assertFalse(s.service.monitoring());assertFalse(s.service.waitingForBattery());assertNull(s.gatt());assertFalse(s.prefs().getBoolean("resume_monitoring",true));assertEquals("",get(s.service,"reconnectPassword"));
            if(waiting!=null)assertTrue(Shadows.shadowOf(waiting).isClosed());
        }
    }
    @Test public void switchingBatteryCancelsThePreviousRetryAndItsCallbacks() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);BluetoothGatt original=s.gatt();s.disconnect(133);s.start(OTHER);BluetoothGatt current=s.gatt();advance(6);
            s.callback().onConnectionStateChange(original,0,BluetoothProfile.STATE_CONNECTED);idle();
            assertEquals(OTHER,s.service.address);assertSame(current,s.gatt());assertFalse(s.service.waitingForBattery());assertFalse(s.service.ble.isWaitingForDevice());
        }
    }
    @Test public void unsupportedServiceDoesNotRetryEvenAfterAnAutomaticReconnect() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);s.disconnect(0);advance(5);BluetoothGatt g=s.gatt();set(s.service.ble,"connecting",false);set(s.service.ble,"waitForDevice",false);s.service.onState("Checking battery services",false);
            s.callback().onServicesDiscovered(g,0);idle();assertFalse(s.service.monitoring());assertTrue(s.service.status.contains("supported JBD"));assertFalse(s.prefs().getBoolean("resume_monitoring",true));advance(120);assertNull(s.gatt());
        }
    }
    @Test public void turningBluetoothOffAndOnWaitsThenReconnectsImmediately() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);Shadows.shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(false);s.broadcastBluetooth();
            assertTrue(s.service.waitingForBattery());assertTrue(s.service.status.contains("Bluetooth is off"));assertNull(s.gatt());advance(65);assertNull(s.gatt());
            Shadows.shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true);s.broadcastBluetooth();assertNotNull(s.gatt());assertTrue(s.service.ble.isWaitingForDevice());
        }
    }
    @Test public void disablingReconnectWhileWaitingClosesTheGattRequest() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);s.disconnect(0);advance(5);BluetoothGatt g=s.gatt();s.prefs().edit().putBoolean("auto_reconnect",false).commit();s.service.settingsChanged();
            assertFalse(s.service.monitoring());assertTrue(Shadows.shadowOf(g).isClosed());advance(120);assertNull(s.gatt());
        }
    }
    @Test public void lostPermissionStopsWaitingWithoutAnotherAttempt() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);s.disconnect(0);Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT);advance(5);
            assertFalse(s.service.monitoring());assertNull(s.gatt());assertTrue(s.service.status.contains("Nearby devices"));
        }
    }
    @Test public void pausingForScreenOffRetainsResumeButAnExplicitStopClearsIt() throws Exception {
        try(Session s=new Session()){
            s.start(ADDRESS);s.service.pauseMonitoring();assertFalse(s.service.monitoring());assertTrue(s.prefs().getBoolean("resume_monitoring",false));s.service.stopMonitoring();assertFalse(s.prefs().getBoolean("resume_monitoring",true));
        }
    }
    private static byte[] jkFrame(){byte[] f=new byte[300];f[0]=0x55;f[1]=(byte)0xaa;f[2]=(byte)0xeb;f[3]=(byte)0x90;f[4]=2;int[] cells={3313,3312,3310,3308};for(int i=0;i<4;i++)le(f,6+i*2,cells[i],2);le(f,118,13243,4);f[141]=90;le(f,142,252500,4);le(f,146,280000,4);f[166]=1;f[167]=1;int sum=0;for(int i=0;i<299;i++)sum+=f[i]&255;f[299]=(byte)sum;return f;}
    private static void le(byte[] f,int offset,long value,int size){for(int i=0;i<size;i++)f[offset+i]=(byte)(value>>(i*8));}
    private static Reading sample(){Reading d=Reading.demo();return new Reading(System.currentTimeMillis(),d.basic,d.cells,"Test battery ["+ADDRESS+"]");}
    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static void advance(int seconds){Shadows.shadowOf(Looper.getMainLooper()).idleFor(seconds,TimeUnit.SECONDS);}
    private static Object get(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static final class Session implements AutoCloseable {
        final ServiceController<MonitorService> controller;final MonitorService service;
        Session(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("offline_bms",Context.MODE_PRIVATE).edit().clear().putBoolean("record",false).commit();Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS);Shadows.shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true);controller=Robolectric.buildService(MonitorService.class).create();service=controller.get();}
        SharedPreferences prefs(){return service.getSharedPreferences("offline_bms",Context.MODE_PRIVATE);}
        void start(String address){service.onStartCommand(new Intent(service,MonitorService.class).setAction(MonitorService.CONNECT).putExtra("address",address).putExtra("name","Test battery"),0,1);}
        BluetoothGatt gatt()throws Exception{return (BluetoothGatt)get(service.ble,"gatt");}
        BluetoothGattCallback callback()throws Exception{return (BluetoothGattCallback)get(service.ble,"gattCallback");}
        void disconnect(int status)throws Exception{callback().onConnectionStateChange(gatt(),status,BluetoothProfile.STATE_DISCONNECTED);idle();}
        void broadcastBluetooth(){service.sendBroadcast(new Intent(BluetoothAdapter.ACTION_STATE_CHANGED));idle();}
        public void close()throws Exception{controller.destroy();((ExecutorService)get(service,"io")).awaitTermination(10,TimeUnit.SECONDS);}
    }
}
