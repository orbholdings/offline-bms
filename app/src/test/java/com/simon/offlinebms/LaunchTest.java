package com.simon.offlinebms;

import android.app.Application;
import android.Manifest;
import android.bluetooth.*;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Runs the real launch path and Android resource rendering without a radio. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,35},qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LaunchTest {
    @Test public void opensDarkDashboard() throws Exception {open("Dark",false);}
    @Test public void opensLightDashboard() throws Exception {open("Light",false);}
    @Test public void opensSystemThemeDashboard() throws Exception {open("System",false);}
    @Test public void bottomTabsKeepIconsLabelsAndSelectedStatesCentred() throws Exception {
        try{
            for(String theme:new String[]{"Dark","Light"})try(Screen screen=new Screen(theme,false)){
                MainActivity a=screen.activity();Method show=MainActivity.class.getDeclaredMethod("showTab",String.class);show.setAccessible(true);
                for(int width:new int[]{360,393,412}){show.invoke(a,"Batteries");renderPhone(a,width);assertCentredNavigation(a,"Batteries");}
                for(String page:new String[]{"Overview","Cells","History","Batteries","Tools","Equipment","Parameters"}){show.invoke(a,page);renderPhone(a,393);assertCentredNavigation(a,page.equals("Equipment")?"Batteries":page.equals("Parameters")?"Tools":page);}
            }
            RuntimeEnvironment.setFontScale(1.3f);
            try(Screen screen=new Screen("Dark",false)){renderPhone(screen.activity(),360);assertCentredNavigation(screen.activity(),"Overview");}
        }finally{RuntimeEnvironment.setFontScale(1f);}
    }
    @Test public void batteryCardsShowLiveStateAndOpenTheExistingConnection() throws Exception {
        for(String theme:new String[]{"Dark","Light"})try(Screen screen=new Screen(theme,false)){
            MainActivity a=screen.activity();MonitorService service=screen.service.get();
            ProfileStore.Profile p=new ProfileStore.Profile("AA:BB:CC:DD:EE:01","Leisure battery");p.protocol="JK02_32S";p.chemistry="LiFePO₄";new ProfileStore(service).put(p);
            ProfileStore.Profile second=new ProfileStore.Profile("AA:BB:CC:DD:EE:02","Workshop battery");second.protocol="JBD";new ProfileStore(service).put(second);
            ProfileStore.Profile third=new ProfileStore.Profile("AA:BB:CC:DD:EE:03","Spare battery");new ProfileStore(service).put(third);
            startBluetoothSession(service);Method show=MainActivity.class.getDeclaredMethod("showTab",String.class);show.setAccessible(true);show.invoke(a,"Batteries");renderPhone(a,393);
            Button connecting=findButton(a.getWindow().getDecorView(),"Connecting…");assertNotNull(connecting);assertFalse(connecting.isEnabled());
            JkProtocol.Sample sample=JkProtocol.parse(capturedJk("first"),"Auto");setPrivate(service.ble,"ready",true);setPrivate(a,"selected",p.address);service.onReading(new Reading(System.currentTimeMillis(),sample.basic,sample.cells,"Captured JK ["+p.address+"]"));service.onState("Live · JK Bluetooth",true);renderPhone(a,393);
            View view=a.getWindow().getDecorView();assertTrue(containsText(view,"CONNECTED"));Button open=findButton(view,"Open dashboard");assertNotNull(open);assertTrue(open.isEnabled());assertFalse(containsTextPart(view,"Unknown / other"));saveAppearancePreview(a,"batteries-"+theme.toLowerCase(Locale.ROOT));
            @SuppressWarnings("unchecked") Map<String,Button> actions=(Map<String,Button>)getPrivate(a,"batteryActions");assertEquals("Connect",actions.get(second.address).getText().toString());
            BluetoothGatt existing=(BluetoothGatt)getPrivate(service.ble,"gatt");open.performClick();renderPhone(a,393);assertSame(existing,getPrivate(service.ble,"gatt"));assertEquals("Overview",getPrivate(a,"tab"));assertTrue(containsText(a.getWindow().getDecorView(),"13.13 V"));saveAppearancePreview(a,"dashboard-"+theme.toLowerCase(Locale.ROOT));
            show.invoke(a,"Batteries");renderPhone(a,393);service.stopMonitoring();renderPhone(a,393);assertNull(findButton(a.getWindow().getDecorView(),"Open dashboard"));assertNotNull(findButton(a.getWindow().getDecorView(),"Connect"));assertTrue(containsText(a.getWindow().getDecorView(),"Victron equipment"));
        }
    }
    private static void assertCentredNavigation(MainActivity a,String selected)throws Exception{
        ViewGroup nav=(ViewGroup)getPrivate(a,"nav");assertEquals(5,nav.getChildCount());int chosen=0,minWidth=Integer.MAX_VALUE,maxWidth=0;
        for(int i=0;i<nav.getChildCount();i++){
            ViewGroup item=(ViewGroup)nav.getChildAt(i);View icon=item.getChildAt(0);TextView label=(TextView)item.getChildAt(1);minWidth=Math.min(minWidth,item.getWidth());maxWidth=Math.max(maxWidth,item.getWidth());
            assertEquals(item.getWidth()/2f,icon.getLeft()+icon.getWidth()/2f,1.1f);assertNotNull(label.getLayout());
            float textCentre=label.getLeft()+label.getTotalPaddingLeft()+(label.getLayout().getLineLeft(0)+label.getLayout().getLineRight(0))/2f;
            assertEquals("Tab text must share the icon's centre: "+label.getText(),icon.getLeft()+icon.getWidth()/2f,textCentre,1.1f);assertEquals(0,label.getLayout().getEllipsisCount(0));assertTrue(label.getBottom()<=item.getHeight()-item.getPaddingBottom());
            assertTrue(item.getHeight()>=48*a.getResources().getDisplayMetrics().density);if(item.isSelected()){chosen++;assertEquals(selected,item.getContentDescription().toString());}
        }
        assertEquals(1,chosen);assertTrue(maxWidth-minWidth<=1);
    }
    @Test public void batteryArrowsCycleWrapKeepThePageAndClearOtherReadings() throws Exception {
        for(String theme:new String[]{"Dark","Light"})try(Screen screen=new Screen(theme,false)){
            MainActivity a=screen.activity();MonitorService service=screen.service.get();render(a);savedSwitchProfiles(a);startBluetoothSession(service);setPrivate(a,"selected",service.address);setPrivate(service.ble,"ready",true);
            JkProtocol.Sample sample=JkProtocol.parse(capturedJk("first"),"Auto");service.onReading(new Reading(System.currentTimeMillis(),sample.basic,sample.cells,"Alpha ["+service.address+"]"));service.onState("Live · JK Bluetooth",true);showPage(a,"Overview");renderPhone(a,393);
            assertEquals("1 of 3 · Live",((TextView)getPrivate(a,"batterySwitchPosition")).getText().toString());assertTrue(containsText(a.getWindow().getDecorView(),"13.13 V"));
            BluetoothGatt original=(BluetoothGatt)getPrivate(service.ble,"gatt");((View)getPrivate(a,"nextBattery")).performClick();
            assertEquals("AA:BB:CC:DD:EE:02",getPrivate(a,"selected"));assertNull(getPrivate(a,"reading"));assertEquals("Overview",getPrivate(a,"tab"));assertFalse(containsText(a.getWindow().getDecorView(),"13.13 V"));deliverNextConnection(a,service);assertEquals(getPrivate(a,"selected"),service.address);assertNull(service.latest);assertTrue(Shadows.shadowOf(original).isClosed());assertEquals(0,service.sessionStats.samples);
            showPage(a,"Cells");((View)getPrivate(a,"nextBattery")).performClick();deliverNextConnection(a,service);assertEquals("Cells",getPrivate(a,"tab"));assertEquals("AA:BB:CC:DD:EE:03",service.address);
            ((View)getPrivate(a,"nextBattery")).performClick();deliverNextConnection(a,service);assertEquals("AA:BB:CC:DD:EE:01",service.address);
            showPage(a,"History");((View)getPrivate(a,"previousBattery")).performClick();deliverNextConnection(a,service);assertEquals("History",getPrivate(a,"tab"));assertEquals("AA:BB:CC:DD:EE:03",service.address);
            ((View)getPrivate(a,"previousBattery")).performClick();((View)getPrivate(a,"previousBattery")).performClick();((View)getPrivate(a,"previousBattery")).performClick();
            deliverNextConnection(a,service);deliverNextConnection(a,service);deliverNextConnection(a,service);assertEquals("AA:BB:CC:DD:EE:03",service.address);assertEquals(service.address,getPrivate(a,"selected"));assertEquals(service.address,a.getSharedPreferences("offline_bms",Context.MODE_PRIVATE).getString("selected_address",""));
        }
    }
    @Test public void batteryPickerAndArrowsFitPhoneWidthsAndLargeText() throws Exception {
        try{
            RuntimeEnvironment.setFontScale(1.3f);
            for(String theme:new String[]{"Dark","Light"})try(Screen screen=new Screen(theme,false)){
                MainActivity a=screen.activity();render(a);ProfileStore initial=new ProfileStore(a);for(ProfileStore.Profile p:initial.all())initial.remove(p.address);showPage(a,"Overview");assertEquals(View.GONE,((View)getPrivate(a,"batterySwitcher")).getVisibility());
                ProfileStore store=new ProfileStore(a);ProfileStore.Profile one=new ProfileStore.Profile("AA:BB:CC:DD:EE:01","Alpha leisure battery");one.protocol="JK02_32S";store.put(one);showPage(a,"Overview");assertFalse(((View)getPrivate(a,"previousBattery")).isEnabled());assertFalse(((View)getPrivate(a,"nextBattery")).isEnabled());
                savedSwitchProfiles(a);ProfileStore.Profile longName=store.get(one.address);longName.name="Alpha leisure battery with a longer name for the woodland and workshop";store.put(longName);setPrivate(a,"selected",one.address);RuntimeEnvironment.setFontScale(1.3f);
                for(int width:new int[]{360,393,412}){
                    showPage(a,"Overview");renderPhone(a,width);View row=(View)getPrivate(a,"batterySwitcher"),previous=(View)getPrivate(a,"previousBattery"),next=(View)getPrivate(a,"nextBattery"),choice=(View)getPrivate(a,"batterySwitchChoice");TextView name=(TextView)getPrivate(a,"batterySwitchName");float dp=a.getResources().getDisplayMetrics().density;
                    assertTrue(previous.getWidth()>=48*dp);assertTrue(previous.getHeight()>=48*dp);assertEquals(previous.getWidth(),next.getWidth());assertTrue(name.getLineCount()<=2);assertTrue(name.getWidth()>0);assertEquals(row.getWidth()/2f,choice.getLeft()+choice.getWidth()/2f,1f);assertCentredNavigation(a,"Overview");
                }
                for(String page:new String[]{"Overview","Cells","History","Batteries","Tools","Equipment","Parameters"}){showPage(a,page);assertEquals(page.equals("Overview")||page.equals("Cells")||page.equals("History")?View.VISIBLE:View.GONE,((View)getPrivate(a,"batterySwitcher")).getVisibility());}
                startBluetoothSession(screen.service.get());showPage(a,"Overview");((View)getPrivate(a,"batterySwitchChoice")).performClick();AlertDialog chooser=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals(3,chooser.getListView().getAdapter().getCount());chooser.getListView().performItemClick(null,1,1);assertFalse(chooser.isShowing());deliverNextConnection(a,screen.service.get());assertEquals("AA:BB:CC:DD:EE:02",getPrivate(a,"selected"));
            }
        }finally{RuntimeEnvironment.setFontScale(1f);}
    }
    @Test public void batterySwitchingWaitsForWritesAndRejectsOldConnectionCallbacks() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MainActivity a=screen.activity();MonitorService service=screen.service.get();render(a);savedSwitchProfiles(a);startBluetoothSession(service);setPrivate(a,"selected",service.address);showPage(a,"Overview");BluetoothGatt old=(BluetoothGatt)getPrivate(service.ble,"gatt");
            setPrivate(service.ble,"parameterSession",new JbdSettingsSession(parameterFixture(),""));service.onState("Reading parameters…",false);assertFalse(((View)getPrivate(a,"nextBattery")).isEnabled());
            Method cycle=MainActivity.class.getDeclaredMethod("cycleBattery",int.class);cycle.setAccessible(true);cycle.invoke(a,1);assertEquals(service.address,getPrivate(a,"selected"));
            service.onStartCommand(new Intent(service,MonitorService.class).setAction(MonitorService.CONNECT).putExtra("address","AA:BB:CC:DD:EE:02").putExtra("name","Bravo"),0,2);assertSame(old,getPrivate(service.ble,"gatt"));
            setPrivate(service.ble,"parameterSession",null);service.onState("Connecting…",false);((View)getPrivate(a,"nextBattery")).performClick();deliverNextConnection(a,service);
            BluetoothGattCallback callback=(BluetoothGattCallback)getPrivate(service.ble,"gattCallback");callback.onConnectionStateChange(old,133,BluetoothProfile.STATE_DISCONNECTED);Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(service.monitoring());assertFalse(service.status.contains("133"));
            JkProtocol.Sample sample=JkProtocol.parse(capturedJk("first"),"Auto");service.onReading(new Reading(System.currentTimeMillis(),sample.basic,sample.cells,"Old battery [AA:BB:CC:DD:EE:01]"));assertNull(service.latest);assertNull(getPrivate(a,"reading"));assertEquals(0,service.sessionStats.samples);
        }
    }
    private static void savedSwitchProfiles(Context c)throws Exception{
        ProfileStore store=new ProfileStore(c);for(int i=1;i<=3;i++){ProfileStore.Profile p=new ProfileStore.Profile("AA:BB:CC:DD:EE:0"+i,i==1?"Alpha battery":i==2?"Bravo battery":"Charlie battery");p.protocol=i==2?"JBD":"JK02_32S";store.put(p);}ProfileStore.Profile v=new ProfileStore.Profile("AA:BB:CC:DD:EE:09","A Victron charger");v.protocol="Victron";store.put(v);
    }
    private static void showPage(MainActivity a,String page)throws Exception{Method show=MainActivity.class.getDeclaredMethod("showTab",String.class);show.setAccessible(true);show.invoke(a,page);}
    private static void deliverNextConnection(MainActivity a,MonitorService service){Intent intent;do{intent=Shadows.shadowOf(a).getNextStartedService();}while(intent!=null&&!MonitorService.CONNECT.equals(intent.getAction()));assertNotNull("A battery tap must start monitoring",intent);service.onStartCommand(intent,0,1);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    @Test public void jkBalanceButtonReadsThenConfirmsAndVerifiesTheSetting() throws Exception {
        for(String theme:new String[]{"Dark","Light"})try(Screen screen=new Screen(theme,false)){
            MainActivity a=screen.activity();render(a);savedSwitchProfiles(a);BluetoothGattCharacteristic rx=liveControlJk(screen);MonitorService service=screen.service.get();BleClient ble=service.ble;BluetoothGatt g=(BluetoothGatt)getPrivate(ble,"gatt");showPage(a,"Cells");renderPhone(a,393);
            Button balance=(Button)getPrivate(a,"balanceToggle");assertTrue(balance.isEnabled());assertEquals("Balance",balance.getText().toString());balance.performClick();assertTrue("Balance read must stay active: "+ble.balanceMessage,ble.balancingBusy());assertEquals(0x96,Shadows.shadowOf(g).getLatestWrittenBytes()[4]&255);assertNull(ble.jkBalanceSettings);
            feedJk(ble,rx,balanceFrame(false));AlertDialog review=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(review.isShowing());assertTrue(containsText(review.getWindow().getDecorView(),"Enable balancing?"));assertFalse(ble.jkBalanceSettings.enabled);assertFalse(ble.operationsBusy());
            review.getButton(-1).performClick();assertTrue(ble.balancingBusy());assertArrayEquals(JkProtocol.balancingCommand(true),Shadows.shadowOf(g).getLatestWrittenBytes());assertFalse(ble.jkBalanceSettings.enabled);assertFalse(((View)getPrivate(a,"nextBattery")).isEnabled());
            feedJk(ble,rx,balanceFrame(true));assertTrue("An unsolicited frame before verification must not confirm a write",ble.balancingBusy());Shadows.shadowOf(Looper.getMainLooper()).idleFor(400,TimeUnit.MILLISECONDS);assertEquals(0x96,Shadows.shadowOf(g).getLatestWrittenBytes()[4]&255);feedJk(ble,rx,balanceFrame(true));assertFalse(ble.operationsBusy());assertTrue(ble.jkBalanceSettings.enabled);assertTrue(ble.balanceMessage.contains("confirmed by battery"));assertEquals("Disable balancing",((Button)getPrivate(a,"balanceToggle")).getText().toString());assertTrue(containsText(a.getWindow().getDecorView(),"Setting: Enabled · Idle"));assertTrue(((View)getPrivate(a,"nextBattery")).isEnabled());renderPhone(a,393);saveAppearancePreview(a,"balance-cells-"+theme.toLowerCase(Locale.ROOT));
            ((Button)getPrivate(a,"balanceToggle")).performClick();AlertDialog cancel=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();cancel.getButton(-2).performClick();assertFalse(ble.operationsBusy());assertTrue(ble.jkBalanceSettings.enabled);
            showPage(a,"Overview");renderPhone(a,393);saveAppearancePreview(a,"switch-dashboard-"+theme.toLowerCase(Locale.ROOT));
        }
    }
    @Test public void jkBalanceFailuresAndDisconnectKeepTheOutcomeUnconfirmed() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MainActivity a=screen.activity();render(a);savedSwitchProfiles(a);BluetoothGattCharacteristic rx=liveControlJk(screen);BleClient ble=screen.service.get().ble;feedJk(ble,rx,balanceFrame(false));
            ble.setBalancing(ble.jkBalanceSettings,true);Shadows.shadowOf(Looper.getMainLooper()).idleFor(400,TimeUnit.MILLISECONDS);feedJk(ble,rx,balanceFrame(false));assertFalse(ble.operationsBusy());assertNull(ble.jkBalanceSettings);assertTrue(ble.balanceMessage.contains("did not confirm"));assertTrue(screen.service.get().monitoring());
            ble.readBalancingSettings();byte[] corrupt=balanceFrame(true);corrupt[299]++;feedJk(ble,rx,corrupt);assertTrue(ble.balancingBusy());assertNull(ble.jkBalanceSettings);Shadows.shadowOf(Looper.getMainLooper()).idleFor(6000,TimeUnit.MILLISECONDS);assertFalse(ble.operationsBusy());assertNull(ble.jkBalanceSettings);assertTrue(ble.balanceMessage.contains("No supported"));
            ble.readBalancingSettings();feedJk(ble,rx,balanceFrame(false));ble.setBalancing(ble.jkBalanceSettings,true);Shadows.shadowOf(Looper.getMainLooper()).idleFor(400,TimeUnit.MILLISECONDS);Shadows.shadowOf(Looper.getMainLooper()).idleFor(6000,TimeUnit.MILLISECONDS);assertFalse(ble.operationsBusy());assertNull(ble.jkBalanceSettings);assertTrue(ble.balanceMessage.contains("could not be confirmed"));
            ble.readBalancingSettings();feedJk(ble,rx,balanceFrame(false));ble.setBalancing(ble.jkBalanceSettings,true);screen.service.get().stopMonitoring();assertFalse(ble.operationsBusy());assertNull(ble.jkBalanceSettings);assertTrue(ble.balanceMessage.contains("before the balancing change was confirmed"));assertFalse(((Button)getPrivate(a,"balanceToggle")).isEnabled());
        }
    }
    @Test public void jbdBalanceShortcutUsesTheReviewedBitPreservingTransaction() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MainActivity a=screen.activity();MonitorService service=screen.service.get();render(a);savedSwitchProfiles(a);startBluetoothSession(service);setPrivate(a,"selected",service.address);JbdSettings.Snapshot s=parameterFixture();
            JbdProtocol.Basic basic=Reading.demo().basic;basic.cellCount=16;basic.capacityAh=280;basic.vendor="JBD";setPrivate(service.ble,"basic",basic);setPrivate(service.ble,"ready",true);service.ble.parameters=s;service.onReading(new Reading(System.currentTimeMillis(),basic,new double[16],"Alpha ["+s.address+"]"));service.onState("Live · Bluetooth",true);showPage(a,"Cells");
            Button balance=(Button)getPrivate(a,"balanceToggle");assertTrue(balance.isEnabled());assertEquals("Disable balancing",balance.getText().toString());balance.performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();AlertDialog review=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(containsText(review.getWindow().getDecorView(),"Save to battery?"));assertTrue(containsText(review.getWindow().getDecorView(),"On → Off"));assertFalse(service.ble.parametersBusy());assertEquals(0xab0c,JbdSettings.u16(s.get(0x2d)));
            review.getButton(-1).performClick();assertTrue("JBD balance transaction: "+org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),service.ble.parametersBusy());JbdSettingsSession transaction=(JbdSettingsSession)getPrivate(service.ble,"parameterSession");assertEquals("balance_enabled",((JbdSettings.Field)getPrivate(transaction,"field")).id);assertEquals("0",getPrivate(transaction,"requested"));assertEquals(0xab08,JbdSettings.u16(s.change(JbdSettings.field("balance_enabled"),"0")));assertEquals("Parameters",getPrivate(a,"tab"));
        }
        try(Screen screen=new Screen("Light",false)){
            MainActivity a=screen.activity();render(a);Method demo=MainActivity.class.getDeclaredMethod("startDemo");demo.setAccessible(true);demo.invoke(a);showPage(a,"Cells");assertFalse(((Button)getPrivate(a,"balanceToggle")).isEnabled());assertTrue(containsText(a.getWindow().getDecorView(),"Demo · balancing controls unavailable"));
        }
    }
    private static BluetoothGattCharacteristic liveControlJk(Screen screen)throws Exception{
        MonitorService service=screen.service.get();startBluetoothSession(service);setPrivate(screen.activity(),"selected",service.address);BluetoothGattCharacteristic rx=prepareCapturedJk(service.ble);BluetoothGattService characteristicService=new BluetoothGattService(BleClient.JK_SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);characteristicService.addCharacteristic(rx);((android.os.Handler)getPrivate(service.ble,"handler")).removeCallbacks((Runnable)getPrivate(service.ble,"connectionTimeout"));feedJk(service.ble,rx,capturedJk("first"));assertTrue(service.ble.isJkBalanceReady());return rx;
    }
    private static byte[] balanceFrame(boolean enabled){byte[] f=new byte[300];f[0]=0x55;f[1]=(byte)0xaa;f[2]=(byte)0xeb;f[3]=(byte)0x90;f[4]=1;le(f,26,5,4);le(f,114,4,4);le(f,118,1,4);le(f,122,1,4);le(f,126,enabled?1:0,4);le(f,130,280000,4);le(f,138,3300,4);int sum=0;for(int i=0;i<299;i++)sum+=f[i]&255;f[299]=(byte)sum;return f;}
    private static void feedJk(BleClient ble,BluetoothGattCharacteristic rx,byte[] data)throws Exception{BluetoothGattCallback callback=(BluetoothGattCallback)getPrivate(ble,"gattCallback");BluetoothGatt g=(BluetoothGatt)getPrivate(ble,"gatt");notifyJk(callback,g,rx,data);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static void renderPhone(MainActivity activity,int widthDp){
        Shadows.shadowOf(Looper.getMainLooper()).idle();float density=activity.getResources().getDisplayMetrics().density;int width=Math.round(widthDp*density),height=Math.round(850*density);View view=activity.getWindow().getDecorView();view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));bitmap.recycle();assertFalse(activity.isFinishing());
    }
    private static void saveAppearancePreview(MainActivity a,String name)throws Exception{
        String directory=System.getProperty("bms.previewDir");if(directory==null)return;View view=a.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));java.io.File folder=new java.io.File(directory);folder.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    @Test public void opensRetainedBatteryDashboard() throws Exception {open("Dark",true);}

    private void open(String theme,boolean retained) throws Exception {
        try(Screen screen=new Screen(theme,retained)){
            render(screen.activity());
            assertTrue(containsText(screen.activity().getWindow().getDecorView(),"Offline BMS"));
            if(!theme.equals("System"))assertSystemBars(screen.activity(),theme.equals("Light"));
            if(retained)assertNotNull(new ProfileStore(screen.activity()).get("AA:BB:CC:DD:EE:01"));
        }
    }

    @Test public void opensEveryPageAndDemo() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MainActivity activity=screen.activity();
            Method showTab=MainActivity.class.getDeclaredMethod("showTab",String.class);
            showTab.setAccessible(true);
            for(String tab:new String[]{"Cells","History","Batteries","Equipment","Tools","Parameters"}){
                showTab.invoke(activity,tab);
                render(activity);
                if(tab.equals("Tools"))assertTrue(containsText(activity.getWindow().getDecorView(),"Buy me a coffee"));
            }
            Method demo=MainActivity.class.getDeclaredMethod("startDemo");
            demo.setAccessible(true);demo.invoke(activity);render(activity);
            assertTrue(containsText(activity.getWindow().getDecorView(),"DEMO • SAMPLE DATA"));
            showTab.invoke(activity,"Cells");render(activity);
        }
    }

    @Test public void recreatesDashboardInNewTheme() throws Exception {
        try(Screen screen=new Screen("Dark",true)){
            render(screen.activity());
            MainActivity previous=screen.activity();
            previous.getSharedPreferences("offline_bms",Context.MODE_PRIVATE).edit().putString("theme","Light").commit();
            screen.controller.recreate();awaitIo(previous);
            render(screen.activity());assertSystemBars(screen.activity(),true);
            assertNotNull(new ProfileStore(screen.activity()).get("AA:BB:CC:DD:EE:01"));
        }
    }

    @Test public void createsIdleMonitoringService() throws Exception {
        ServiceController<MonitorService> c=Robolectric.buildService(MonitorService.class).create();
        try{assertNotNull(c.get().ble);assertFalse(c.get().monitoring());}
        finally{c.destroy();awaitIo(c.get());}
    }

    @Test public void victronSetupIdentifiesAddressAndKeyErrors() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MainActivity a=screen.activity();render(a);
            Method add=MainActivity.class.getDeclaredMethod("addVictron",String.class,String.class);add.setAccessible(true);add.invoke(a,"","AC charger");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            List<EditText> inputs=new ArrayList<>();collectInputs(dialog.getWindow().getDecorView(),inputs);assertEquals(3,inputs.size());
            EditText mac=inputs.get(1),key=inputs.get(2);mac.setText("invalid");key.setText("1234");dialog.getButton(-1).performClick();
            assertNotNull(mac.getError());assertTrue(dialog.isShowing());
            mac.setText("aabbccddeeff");mac.setError(null);dialog.getButton(-1).performClick();
            assertNull("Compact pasted address must pass address validation",mac.getError());
            assertNotNull(key.getError());assertTrue(key.getError().toString().contains("32-character"));assertTrue(dialog.isShowing());
            dialog.getButton(-2).performClick();
        }
    }

    @Test public void overviewShowsVictronBelowBatteryWithFreshnessAndDetails() throws Exception {
        for(String theme:new String[]{"Dark","Light"})try(Screen screen=new Screen(theme,false)){
            MainActivity a=screen.activity();MonitorService service=screen.service.get();String address="AA:BB:CC:DD:EE:22";
            ProfileStore.Profile p=new ProfileStore.Profile(address,"Main charger");p.protocol="Victron";new ProfileStore(a).put(p);
            VictronProtocol.Data data=new VictronProtocol.Data();data.type=15;data.state=3;data.add("Output voltage",14.4,"V");data.add("Output current",30,"A");data.details.put("Off reason","0x1");
            EquipmentReading r=new EquipmentReading(System.currentTimeMillis(),address,p.name,data);service.equipment.put(address,r);
            Object scanner=getPrivate(service,"victron");setPrivate(scanner,"running",true);
            Method show=MainActivity.class.getDeclaredMethod("showTab",String.class);show.setAccessible(true);show.invoke(a,"Overview");render(a);
            View view=a.getWindow().getDecorView();assertTrue(containsText(view,"Main charger"));assertTrue(containsText(view,"14.40 V"));assertTrue(containsText(view,"Off reason: 0x1"));
            assertTrue(containsTextPart(view,"LIVE • Orion XS"));assertNull(findButton(view,"Edit readout key"));assertNotNull(findButton(view,"Manage Victron devices"));
            LinearLayoutBodyCheck(a);
            String preview=System.getProperty("bms.previewDir");
            if(preview!=null){
                android.widget.ScrollView scroller=(android.widget.ScrollView)getPrivate(a,"scroll");scroller.scrollTo(0,scroller.getChildAt(0).getHeight());Shadows.shadowOf(Looper.getMainLooper()).idle();
                Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
                java.io.File directory=new java.io.File(preview);directory.mkdirs();
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,"overview-"+theme.toLowerCase(java.util.Locale.ROOT)+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
            }
            service.equipment.put(address,new EquipmentReading(System.currentTimeMillis()-60000,address,p.name,data));
            Method refresh=MainActivity.class.getDeclaredMethod("refreshEquipment");refresh.setAccessible(true);refresh.invoke(a);assertTrue(containsTextPart(view,"STALE • Orion XS"));
            service.stopVictronMonitoring();refresh.invoke(a);assertTrue(containsTextPart(view,"SAVED • Orion XS"));
            show.invoke(a,"Equipment");render(a);assertNotNull(findButton(a.getWindow().getDecorView(),"Edit readout key"));
        }
    }
    private static void LinearLayoutBodyCheck(MainActivity a)throws Exception{
        ViewGroup body=(ViewGroup)getPrivate(a,"body");int battery=-1,victron=-1;
        for(int i=0;i<body.getChildCount();i++){if(containsText(body.getChildAt(i),"This connection"))battery=i;if(containsText(body.getChildAt(i),"Victron devices"))victron=i;}
        assertTrue("Victron section must be below battery information",battery>=0&&victron>battery);
    }
    @Test public void stoppingVictronKeepsBatteryConnectionRunning() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            startBluetoothSession(screen.service.get());MonitorService service=screen.service.get();
            setPrivate(getPrivate(service,"victron"),"running",true);service.stopVictronMonitoring();
            assertTrue(service.monitoring());assertTrue(service.ble.isConnectingOrConnected());assertFalse(service.victronRunning());
        }
    }
    @Test public void jkDiscoveryAcceptsDedicatedTransmitAndSubscribesOnce() throws Exception {
        for(boolean separate:new boolean[]{false,true})try(Screen screen=new Screen("Dark",false)){
            MonitorService service=screen.service.get();startBluetoothSession(service);BleClient ble=service.ble;
            BluetoothGatt g=(BluetoothGatt)getPrivate(ble,"gatt");BluetoothGattService s=new BluetoothGattService(BleClient.JK_SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);
            int notify=BluetoothGattCharacteristic.PROPERTY_NOTIFY,write=BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
            BluetoothGattCharacteristic rx=new BluetoothGattCharacteristic(BleClient.JK_IO,notify|(separate?0:write),BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE);
            rx.addDescriptor(new BluetoothGattDescriptor(BleClient.CCCD,BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE));s.addCharacteristic(rx);
            BluetoothGattCharacteristic tx=separate?new BluetoothGattCharacteristic(BleClient.JK_TX,write,BluetoothGattCharacteristic.PERMISSION_WRITE):rx;
            if(separate)s.addCharacteristic(tx);
            Shadows.shadowOf(g).addDiscoverableService(s);Shadows.shadowOf(g).allowCharacteristicNotification(rx);g.discoverServices();
            ((BluetoothGattCallback)getPrivate(ble,"gattCallback")).onServicesDiscovered(g,BluetoothGatt.GATT_SUCCESS);Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertSame(tx,getPrivate(ble,"tx"));assertEquals(1,((List<?>)getPrivate(ble,"subscriptions")).size());
        }
    }
    @Test public void jkFallbackAndFragmentedFourCellReadingReachDashboard() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MainActivity a=screen.activity();MonitorService service=screen.service.get();startBluetoothSession(service);BleClient ble=service.ble;
            BluetoothGatt g=(BluetoothGatt)getPrivate(ble,"gatt");BluetoothGattService s=new BluetoothGattService(BleClient.JK_SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);
            BluetoothGattCharacteristic first=new BluetoothGattCharacteristic(BleClient.JK_IO,BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,BluetoothGattCharacteristic.PERMISSION_WRITE);
            BluetoothGattCharacteristic alternate=new BluetoothGattCharacteristic(BleClient.JK_TX,BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,BluetoothGattCharacteristic.PERMISSION_WRITE);s.addCharacteristic(first);s.addCharacteristic(alternate);
            @SuppressWarnings("unchecked") List<BluetoothGattCharacteristic> writes=(List<BluetoothGattCharacteristic>)getPrivate(ble,"jkWriteChannels");writes.add(first);writes.add(alternate);
            setPrivate(ble,"jk",true);setPrivate(ble,"ready",true);setPrivate(ble,"transportReady",true);setPrivate(ble,"tx",first);
            @SuppressWarnings("unchecked") Set<java.util.UUID> notifications=(Set<java.util.UUID>)getPrivate(ble,"notifyUuids");notifications.add(BleClient.JK_IO);
            Method timeout=BleClient.class.getDeclaredMethod("timedOut");timeout.setAccessible(true);timeout.invoke(ble);
            assertSame("Read-only fallback selects the dedicated channel",alternate,getPrivate(ble,"tx"));assertArrayEquals(JkProtocol.readCommand(0x96),Shadows.shadowOf(g).getLatestWrittenBytes());
            setPrivate(a,"selected",service.address);byte[] frame=jkFourCells();BluetoothGattCallback callback=(BluetoothGattCallback)getPrivate(ble,"gattCallback");
            notifyJk(callback,g,first,Arrays.copyOfRange(frame,0,20));notifyJk(callback,g,first,Arrays.copyOfRange(frame,20,120));notifyJk(callback,g,first,Arrays.copyOfRange(frame,120,300));Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(service.latest);assertEquals(4,service.latest.cells.length);assertEquals(90,service.latest.basic.soc);assertEquals(13.243,service.latest.basic.voltage,.00001);assertEquals(280,service.latest.basic.capacityAh,.00001);
            render(a);assertTrue(containsText(a.getWindow().getDecorView(),"13.24 V"));
        }
    }
    private static void startBluetoothSession(MonitorService service){
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS);
        Shadows.shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true);
        service.onStartCommand(new Intent(service,MonitorService.class).setAction(MonitorService.CONNECT).putExtra("address","AA:BB:CC:DD:EE:01").putExtra("name","Test battery").putExtra("password",""),0,1);
    }
    @Test public void capturedFirmware1155ReadingsReachDashboardWithUnavailableTemperatures() throws Exception {
        for(String protocol:new String[]{"Auto","JK02_32S"})try(Screen screen=new Screen("Dark",false)){
            MainActivity a=screen.activity();MonitorService service=screen.service.get();ProfileStore.Profile p=new ProfileStore.Profile("AA:BB:CC:DD:EE:01","Captured JK test");p.protocol=protocol;new ProfileStore(service).put(p);
            startBluetoothSession(service);BleClient ble=service.ble;BluetoothGatt g=(BluetoothGatt)getPrivate(ble,"gatt");BluetoothGattCharacteristic rx=prepareCapturedJk(ble);setPrivate(a,"selected",service.address);
            BluetoothGattCallback callback=(BluetoothGattCallback)getPrivate(ble,"gattCallback");
            for(String name:new String[]{"first","last"}){
                byte[] frame=capturedJk(name);int start=0;for(int length:new int[]{128,22,128,22}){notifyJk(callback,g,rx,Arrays.copyOfRange(frame,start,start+length));start+=length;}Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertNotNull(service.latest);assertEquals(4,service.latest.cells.length);assertEquals(13.134,service.latest.basic.voltage,.000001);assertEquals(89,service.latest.basic.soc);assertEquals(280,service.latest.basic.capacityAh,.000001);assertEquals(0,service.latest.basic.temperatures.length);assertTrue(Double.isNaN(service.latest.maxTemp()));assertTrue(service.latest.basic.chargeEnabled());assertTrue(service.latest.basic.dischargeEnabled());assertEquals(0,Reading.fromJson(service.latest.json()).basic.temperatures.length);
            }
            render(a);assertTrue(containsText(a.getWindow().getDecorView(),"13.13 V"));assertTrue(containsText(a.getWindow().getDecorView(),"Temperature readings unavailable"));assertTrue(service.diagnostics().contains("Detected JK format: JK02_32S"));assertTrue(service.diagnostics().contains("JK accepted frames: 2"));
            String preview=System.getProperty("bms.previewDir");if(preview!=null){View view=a.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));java.io.File directory=new java.io.File(preview);directory.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,"jk-captured-replay.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
            // A later stream timeout must not falsely report a profile mismatch.
            setPrivate(ble,"retries",2);Method timeout=BleClient.class.getDeclaredMethod("timedOut");timeout.setAccessible(true);timeout.invoke(ble);assertFalse(service.status.contains("format mismatch"));
        }
    }
    @Test public void wrongCapturedJkProfileExplainsWhichFormatToChoose() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MonitorService service=screen.service.get();ProfileStore.Profile p=new ProfileStore.Profile("AA:BB:CC:DD:EE:01","Captured JK test");p.protocol="JK02_24S";new ProfileStore(service).put(p);startBluetoothSession(service);BleClient ble=service.ble;
            BluetoothGatt g=(BluetoothGatt)getPrivate(ble,"gatt");BluetoothGattCharacteristic rx=prepareCapturedJk(ble);BluetoothGattCallback callback=(BluetoothGattCallback)getPrivate(ble,"gattCallback");notifyJk(callback,g,rx,capturedJk("first"));Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(service.latest);
            setPrivate(ble,"retries",2);Method timeout=BleClient.class.getDeclaredMethod("timedOut");timeout.setAccessible(true);timeout.invoke(ble);
            assertTrue(service.status.contains("profile uses JK02_24S"));assertTrue(service.status.contains("readings use JK02_32S"));assertTrue(service.status.contains("or Auto"));assertFalse(service.status.contains("JK04"));assertEquals("JK02_24S",new ProfileStore(service).get(p.address).protocol);assertTrue(service.diagnostics().contains("Detected JK format: JK02_32S"));
        }
    }
    private static BluetoothGattCharacteristic prepareCapturedJk(BleClient ble)throws Exception{
        BluetoothGattCharacteristic rx=new BluetoothGattCharacteristic(BleClient.JK_IO,BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,BluetoothGattCharacteristic.PERMISSION_WRITE);
        setPrivate(ble,"jk",true);setPrivate(ble,"ready",true);setPrivate(ble,"transportReady",true);setPrivate(ble,"tx",rx);@SuppressWarnings("unchecked") Set<java.util.UUID> notifications=(Set<java.util.UUID>)getPrivate(ble,"notifyUuids");notifications.add(BleClient.JK_IO);return rx;
    }
    private static byte[] capturedJk(String name)throws Exception{
        java.nio.file.Path path=java.nio.file.Path.of(System.getProperty("bms.fixtureDir","tests/fixtures"),"jk32-fw11-55-"+name+".hex");String hex=java.nio.file.Files.readString(path).trim();byte[] bytes=new byte[hex.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);return bytes;
    }
    @Test public void exportsSavedDiagnosticsFromOverviewAndToolsAfterRestart() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MainActivity a=screen.activity();ConnectionDiagnostics.save(a,"Offline BMS 1.2.5\nBattery: JK test\nStatus: No valid JK readings\nLast JK decode: T1 temperature outside supported range\n");render(a);
            Button export=findButton(a.getWindow().getDecorView(),"Export connection diagnostics");assertNotNull(export);export.performClick();
            assertTrue(((String)getPrivate(a,"textExport")).contains("No valid JK readings"));assertFalse(((String)getPrivate(a,"textExport")).contains("Choose a battery to connect"));
            assertEquals("text",getPrivate(a,"exportKind"));Intent picker=Shadows.shadowOf(a).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,picker.getAction());assertEquals("text/plain",picker.getType());assertTrue(picker.getStringExtra(Intent.EXTRA_TITLE).startsWith("Offline-BMS-diagnostics-"));
            Method details=MainActivity.class.getDeclaredMethod("details");details.setAccessible(true);details.invoke(a);Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(containsTextPart(dialog.getWindow().getDecorView(),"SAVED CONNECTION ATTEMPT"));dialog.getButton(-3).performClick();assertTrue(((String)getPrivate(a,"textExport")).contains("T1 temperature"));
            setPrivate(a,"monitor",null);details.invoke(a);Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(containsTextPart(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"JK test"));
        }
    }
    @Test public void absentTemperatureLabelsSurviveHistoryAndShowCorrectSensor() throws Exception {
        try(Screen screen=new Screen("Dark",false)){
            MainActivity a=screen.activity();byte[] frame=jkFourCells();frame[182]=3;le(frame,134,0x7fff,2);le(frame,130,0x8000,2);le(frame,132,230,2);JkProtocol.Sample decoded=JkProtocol.parse(frame,"Auto");
            Reading reading=new Reading(System.currentTimeMillis(),decoded.basic,decoded.cells,"Test battery [AA:BB:CC:DD:EE:01]");Reading restored=Reading.fromJson(reading.json());assertArrayEquals(new String[]{"T2"},restored.basic.temperatureLabels);assertEquals(23,restored.maxTemp(),.00001);
            setPrivate(a,"reading",restored);Method show=MainActivity.class.getDeclaredMethod("showTab",String.class);show.setAccessible(true);show.invoke(a,"Overview");render(a);assertTrue(containsTextPart(a.getWindow().getDecorView(),"T2 23.0"));assertFalse(containsTextPart(a.getWindow().getDecorView(),"MOS 23.0"));
            org.json.JSONObject malformed=reading.json().put("temperature_labels",new org.json.JSONArray().put("MOS").put("T2"));try{Reading.fromJson(malformed);fail("Mismatched labels accepted");}catch(org.json.JSONException expected){}
        }
    }
    private static byte[] jkFourCells(){
        byte[] f=new byte[300];f[0]=0x55;f[1]=(byte)0xaa;f[2]=(byte)0xeb;f[3]=(byte)0x90;f[4]=2;
        int[] cells={3313,3312,3310,3308};for(int i=0;i<4;i++)le(f,6+i*2,cells[i],2);
        le(f,118,13243,4);f[141]=90;le(f,142,252500,4);le(f,146,280000,4);f[166]=1;f[167]=1;
        int sum=0;for(int i=0;i<299;i++)sum+=f[i]&255;f[299]=(byte)sum;return f;
    }
    private static void le(byte[] data,int offset,long value,int count){for(int i=0;i<count;i++)data[offset+i]=(byte)(value>>(8*i));}
    private static void notifyJk(BluetoothGattCallback callback,BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){if(android.os.Build.VERSION.SDK_INT>=33)callback.onCharacteristicChanged(g,c,value);else{c.setValue(value);callback.onCharacteristicChanged(g,c);}}

    @Test public void savedParametersAreReadOnlyWhenDisconnected() throws Exception {
        for(String theme:new String[]{"Dark","Light"})try(Screen screen=new Screen(theme,true)){
            MainActivity a=screen.activity();new ParameterStore(a).latest(parameterFixture());
            showParameters(a);render(a);
            View root=a.getWindow().getDecorView();
            assertTrue(containsText(root,"Saved reference • read before editing"));
            assertTrue(containsText(root,"Cell overvoltage protection"));
            assertFalse(findButton(root,"Read parameters from battery").isEnabled());
            assertFalse(findButton(root,"Edit").isEnabled());
        }
    }
    @Test public void parameterReviewCancelDoesNotStartWrite() throws Exception {
        try(Screen screen=new Screen("Dark",true)){
            MainActivity a=screen.activity();render(a);MonitorService service=screen.service.get();
            JbdSettings.Snapshot s=parameterFixture();service.address=s.address;
            setPrivate(a,"selected",s.address);JbdProtocol.Basic basic=new JbdProtocol.Basic();basic.cellCount=16;basic.capacityAh=280;basic.version=0x24;
            setPrivate(service.ble,"basic",basic);setPrivate(service.ble,"ready",true);service.ble.parameters=s;
            showParameters(a);render(a);
            Method edit=MainActivity.class.getDeclaredMethod("editParameter",JbdSettings.Field.class);edit.setAccessible(true);edit.invoke(a,JbdSettings.field("cell_over"));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog input=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            EditText number=findInput(input.getWindow().getDecorView());assertNotNull(number);number.setText("3.650");input.getButton(-1).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog review=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(containsText(review.getWindow().getDecorView(),"Save to battery?"));
            assertTrue(containsText(review.getWindow().getDecorView(),"3.750 V → 3.650 V"));
            review.getButton(-2).performClick();assertFalse(service.ble.parametersBusy());assertEquals(3750,JbdSettings.u16(service.ble.parameters.get(0x24)));
        }
    }
    @Test public void parameterSnapshotsPreserveOriginalValues() throws Exception {
        try(Screen screen=new Screen("Light",true)){
            ParameterStore store=new ParameterStore(screen.activity());JbdSettings.Snapshot original=parameterFixture();
            assertTrue(store.before(original));Map<Integer,byte[]> next=original.raw();next.put(0x24,JbdSettings.word(3650));
            store.latest(new JbdSettings.Snapshot(original.address,original.model,original.firmware,16,280,false,original.timestamp,next));
            assertEquals(3750,JbdSettings.u16(store.get(original.address,true).get(0x24)));
            assertEquals(3650,JbdSettings.u16(store.get(original.address,false).get(0x24)));
            org.json.JSONObject json=new org.json.JSONObject(store.export(original.address));
            assertTrue(json.has("before_last_write"));assertFalse(json.has("password"));assertFalse(json.getJSONObject("latest").has("password"));
        }
    }
    private static JbdSettings.Snapshot parameterFixture(){
        Map<Integer,byte[]> values=new LinkedHashMap<>();int[][] rows={{0x2f,16},{0x10,28000},{0x24,3750},{0x25,3600},{0x26,2200},{0x27,2600},{0x20,6000},{0x21,5760},{0x22,3520},{0x23,4160},{0x28,20000},{0x29,45536},{0x2d,0xab0c}};
        for(int[] row:rows)values.put(row[0],JbdSettings.word(row[1]));return new JbdSettings.Snapshot("AA:BB:CC:DD:EE:01","SP24S004L24S200A","2.4",16,280,false,System.currentTimeMillis(),values);
    }
    private static void showParameters(MainActivity a)throws Exception{Method show=MainActivity.class.getDeclaredMethod("showTab",String.class);show.setAccessible(true);show.invoke(a,"Parameters");}
    private static void setPrivate(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object getPrivate(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static EditText findInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText found=findInput(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private static void collectInputs(View v,List<EditText> out){if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collectInputs(((ViewGroup)v).getChildAt(i),out);}
    private static Button findButton(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button found=findButton(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}

    private static final class Screen implements AutoCloseable {
        final ServiceController<MonitorService> service;
        final ActivityController<MainActivity> controller;
        Screen(String theme,boolean retained){
            Application app=RuntimeEnvironment.getApplication();
            android.content.SharedPreferences.Editor e=app.getSharedPreferences("offline_bms",Context.MODE_PRIVATE).edit().putString("theme",theme);
            if(retained)e.putString("last_address","AA:BB:CC:DD:EE:01").putString("last_name","Saved battery");
            e.commit();
            service=Robolectric.buildService(MonitorService.class).create();
            Shadows.shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app,MonitorService.class),service.get().onBind(new Intent(app,MonitorService.class)));
            controller=Robolectric.buildActivity(MainActivity.class).setup();
        }
        MainActivity activity(){return controller.get();}
        @Override public void close() throws Exception {
            MainActivity last=activity();controller.pause().stop().destroy();awaitIo(last);
            service.destroy();awaitIo(service.get());
        }
    }
    private static void render(MainActivity activity){
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View view=activity.getWindow().getDecorView();
        view.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2200,View.MeasureSpec.EXACTLY));
        view.layout(0,0,1080,2200);Bitmap bitmap=Bitmap.createBitmap(1080,2200,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));bitmap.recycle();
        assertFalse(activity.isFinishing());assertEquals(2200,view.getHeight());
    }
    private static void assertSystemBars(MainActivity activity,boolean light){
        WindowInsetsController c=activity.getWindow().getDecorView().getWindowInsetsController();assertNotNull(c);
        int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        assertEquals(light?mask:0,c.getSystemBarsAppearance()&mask);
    }
    private static boolean containsText(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(containsText(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
    private static boolean containsTextPart(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(containsTextPart(((ViewGroup)view).getChildAt(i),text))return true;return false;}
    private static void awaitIo(Object owner) throws Exception {
        Field f=owner.getClass().getDeclaredField("io");f.setAccessible(true);
        assertTrue("Database worker did not close",((ExecutorService)f.get(owner)).awaitTermination(5,TimeUnit.SECONDS));
    }
}
