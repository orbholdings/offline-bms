package com.orbholdings.offlinehub;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.*;
import android.app.AlertDialog;
import android.text.TextUtils;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(11, 16, 26);
    private final int panel = Color.rgb(25, 34, 49);
    private final int muted = Color.rgb(154, 169, 189);
    private final int teal = Color.rgb(73, 218, 191);
    private LinearLayout root, content;
    private String section = "Overview";
    private JbdMonitor jbd;
    private HeaterProbe heater;
    private String heaterStatus="Not connected",heaterReport="";
    private BatteryGauge batteryGauge;
    private String batteryStatus="Not connected";
    private boolean live=false;
    private int soc=82;private float voltage=12.9f,current=4.2f;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        jbd=new JbdMonitor(this,new JbdMonitor.Listener(){
            @Override public void status(String s){runOnUiThread(()->{batteryStatus=s;if(s.startsWith("Disconnected")||s.startsWith("Connecting")||s.startsWith("Scanning")||s.startsWith("Not a compatible"))live=false;if(section.equals("Energy")||section.equals("Overview"))render();});}
            @Override public void reading(int p,float v,float a){runOnUiThread(()->{soc=p;voltage=v;current=a;live=true;batteryStatus="Connected · JBD";if(batteryGauge!=null)batteryGauge.setReading(p,v,a);if(section.equals("Energy")||section.equals("Overview"))render();});}
        });
        heater=new HeaterProbe(this,new HeaterProbe.Listener(){
            @Override public void status(String s){runOnUiThread(()->{heaterStatus=s;if(section.equals("Heating"))render();});}
            @Override public void devices(java.util.List<String> devices){runOnUiThread(()->{if(devices.isEmpty()){new AlertDialog.Builder(MainActivity.this).setMessage("No BLE devices found. Wake the heater controller and retry.").setPositiveButton("OK",null).show();return;}new AlertDialog.Builder(MainActivity.this).setTitle("Select diesel heater").setItems(devices.toArray(new String[0]),(dialog,index)->heater.connect(devices.get(index))).setNegativeButton("Cancel",null).show();});}
            @Override public void report(String s){runOnUiThread(()->{heaterReport=s;if(section.equals("Heating"))render();});}
        });
        render();
    }
    @Override protected void onDestroy(){if(jbd!=null)jbd.disconnect();if(heater!=null)heater.disconnect();super.onDestroy();}

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private void pad(View v, int a, int b, int c, int d) { v.setPadding(dp(a), dp(b), dp(c), dp(d)); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(1); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(0); return l; }
    private void render() {
        root=column(); root.setBackgroundColor(bg); setContentView(root);
        if(Build.VERSION.SDK_INT>=29){root.setOnApplyWindowInsetsListener((v,insets)->{
            int bottom=insets.getSystemWindowInsetBottom();
            v.setPadding(0,0,0,Math.max(dp(10),bottom));return insets;
        });} else {root.setFitsSystemWindows(true);}
        LinearLayout header=row(); header.setGravity(Gravity.CENTER_VERTICAL);pad(header,22,18,22,16);
        TextView brand=text("◈  OFFLINE HUB",21,Color.WHITE,true);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView state=text(live?"●  JBD LIVE":batteryStatus.startsWith("JBD connected")||batteryStatus.startsWith("Connected")?"●  CONNECTED":"●  DEMO",12,teal,true);header.addView(state);root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=column();pad(content,18,10,18,26);scroll.addView(content);
        TextView title=text(section.equals("Overview")?"Your van at a glance":section,29,Color.WHITE,true);content.addView(title);
        TextView subtitle=text("LOCAL CONTROL  ·  NO CLOUD",11,teal,true);pad(subtitle,0,7,0,20);content.addView(subtitle);
        if(section.equals("Overview")||section.equals("Energy")) {
            batteryHero();
            TextView connect=text("◉  SCAN & CONNECT JBD BATTERY",13,teal,true);
            connect.setGravity(Gravity.CENTER);pad(connect,12,15,12,15);connect.setBackground(background(panel,16));
            content.addView(connect);connect.setOnClickListener(v->jbd.scan(devices->{
                if(devices.isEmpty()){new AlertDialog.Builder(this).setMessage("No Bluetooth devices found. Make sure the JBD is awake and not connected to another app.").setPositiveButton("OK",null).show();return;}
                new AlertDialog.Builder(this).setTitle("Select JBD battery").setItems(devices.toArray(new String[0]),(d,index)->jbd.connect(devices.get(index))).setNegativeButton("Cancel",null).show();
            }));
            TextView connection=text("STATUS  ·  "+batteryStatus,11,muted,false);pad(connection,6,12,0,0);content.addView(connection);
            sectionLabel("POWER FLOW");
            LinearLayout flow=row();flow.setGravity(Gravity.CENTER);pad(flow,14,17,14,17);
            flow.setBackground(background(panel,18));
            TextView power=text(live?String.format(java.util.Locale.UK,"◈ BATTERY  ·  %.0f W %s",Math.abs(voltage*current),current<0?"DISCHARGING":"CHARGING"):"☀  186 W    →    ◈ BATTERY    →    ⚡  54 W",13,teal,true);
            power.setGravity(Gravity.CENTER);flow.addView(power,new LinearLayout.LayoutParams(-1,-2));
            content.addView(flow);
            sectionLabel(section.equals("Energy")?"ENERGY DETAILS":"YOUR SYSTEM");
            addTiles(new String[][] {
                {"SOLAR INPUT",live?"—":"186 W",live?"Not connected":"Demo charging","☀"},
                {"BATTERY CURRENT",live?String.format(java.util.Locale.UK,"%+.2f A",current):"+4.2 A",live?"Live JBD reading":"Demo reading","↗"},
                {"DIESEL HEATER","19.5°C","Disconnected","♨"},
                {"FRESH WATER","68%","Demo reading","◒"},
                {"INTERIOR","21.3°C","Demo temperature","◌"},
                {"LIGHTING","3 zones","Not paired","✦"}
            });
        } else if(section.equals("Heating")) {
            sectionLabel("CLIMATE");
            addTiles(new String[][]{{"DIESEL HEATER",heaterStatus.startsWith("Heater connected")||heaterStatus.startsWith("Status query")||heaterStatus.startsWith("Heater telemetry")?"BLE connected":"Offline",heaterStatus,"♨"},{"CABIN TEMP","—","Awaiting validated heater telemetry","◌"}});
            TextView scanHeater=text("◉  SCAN & CONNECT DIESEL HEATER",13,teal,true);
            scanHeater.setGravity(Gravity.CENTER);pad(scanHeater,12,17,12,17);scanHeater.setBackground(background(panel,16));content.addView(scanHeater);
            scanHeater.setOnClickListener(v->heater.scan());
            info("HEATER STATUS  ·  "+heaterStatus);
            if(!heaterReport.isEmpty()){
                TextView details=text(heaterReport,11,Color.WHITE,false);pad(details,12,14,12,14);
                details.setTextIsSelectable(true);details.setBackground(background(panel,14));content.addView(details);
                TextView copy=text("COPY DIAGNOSTIC REPORT",13,teal,true);pad(copy,12,18,12,18);content.addView(copy);
                copy.setOnClickListener(v->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Heater BLE diagnostics",heaterReport));Toast.makeText(this,"Heater report copied",Toast.LENGTH_SHORT).show();});
            }
            info("Read-only discovery. No heater start, stop or temperature commands are sent.");
        } else if(section.equals("Lights")) {
            sectionLabel("LIGHTING ZONES");
            addTiles(new String[][]{{"LIVING AREA","Offline","No device paired","✦"},{"SLEEPING AREA","Offline","No device paired","✦"}});
        } else {
            renderSensors();
        }
        info(live?"JBD BATTERY LIVE  ·  Other tiles are demo or disconnected. Read-only monitoring.":"DEMO BUILD  ·  Simulated values unless a battery is connected. No device control.");
        LinearLayout nav=row();nav.setGravity(Gravity.CENTER);pad(nav,8,6,8,8);
        String[] tabs={"Overview","Energy","Heating","Lights","Sensors"};
        String[] icons={"⌂","ϟ","♨","✦","◈"};
        for(int i=0;i<tabs.length;i++){
            final String tab=tabs[i];boolean active=tab.equals(section);
            LinearLayout item=column();item.setGravity(Gravity.CENTER);pad(item,2,5,2,5);
            item.setBackground(background(active?panel:bg,15));
            TextView symbol=text(icons[i],20,active?teal:muted,true);symbol.setGravity(Gravity.CENTER);item.addView(symbol);
            TextView label=text(tab,11,active?teal:muted,active);label.setGravity(Gravity.CENTER);item.addView(label);
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
            item.setOnClickListener(v->{section=tab;render();});
        }
        root.addView(nav);
    }
    private void sectionLabel(String label){
        TextView t=text(label,11,muted,true);pad(t,3,22,0,11);content.addView(t);
    }
    private void info(String message){
        TextView t=text(message,11,muted,false);pad(t,5,18,5,8);content.addView(t);
    }
    private void batteryHero(){
        LinearLayout hero=column();pad(hero,22,22,22,23);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(27,56,72),Color.rgb(24,35,53)});
        gradient.setCornerRadius(dp(24));hero.setBackground(gradient);
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text("◈  HOUSE BATTERY",12,teal,true);
        top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(text(live?"● LIVE":batteryStatus.startsWith("JBD connected")||batteryStatus.startsWith("Connected")?"● WAITING":"● DEMO",11,live?teal:muted,true));hero.addView(top);
        batteryGauge=new BatteryGauge(this);
        if(live)batteryGauge.setReading(soc,voltage,current);
        else if(batteryStatus.startsWith("JBD connected")||batteryStatus.startsWith("Connected"))batteryGauge.setWaiting();
        hero.addView(batteryGauge,new LinearLayout.LayoutParams(-1,dp(265)));
        TextView description=text(live?"Live JBD reading":"Battery not connected  ·  Simulated readings",11,muted,false);
        description.setGravity(Gravity.CENTER);hero.addView(description);
        content.addView(hero);
    }
    private void addTiles(String[][] tiles){
        int width=getResources().getDisplayMetrics().widthPixels;
        boolean wide=width/getResources().getDisplayMetrics().density>=700;
        int cols=wide?3:2;
        for(int i=0;i<tiles.length;i+=cols){
            LinearLayout line=row();content.addView(line);
            for(int j=0;j<cols;j++){
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(145),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));
                if(i+j>=tiles.length){line.addView(new View(this),p);continue;}
                String[] tile=tiles[i+j];LinearLayout card=column();pad(card,15,14,12,12);
                card.setBackground(background(panel,19));
                LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);
                TextView label=text(tile[0],10,muted,true);head.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                head.addView(text(tile[3],17,teal,false));card.addView(head);
                TextView value=text(tile[1],23,Color.WHITE,true);pad(value,0,17,0,7);card.addView(value);
                TextView detail=text(tile[2],11,muted,false);detail.setMaxLines(2);card.addView(detail);
                line.addView(card,p);
            }
        }
    }
    private void renderSensors() {
        SensorStore store=new SensorStore(this);
        TextView add=text("+ Add sensor",16,teal,true);
        pad(add,14,14,14,14);add.setBackground(background(panel,14));
        content.addView(add);
        add.setOnClickListener(v -> showAddSensor(store));
        for (SensorStore.Sensor s:store.list()) {
            LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);pad(r,14,14,14,14);
            r.setBackground(background(panel,12));
            LinearLayout details=column();
            details.addView(text(s.name,17,Color.WHITE,true));
            details.addView(text(s.zone+" · "+s.type+" · "+s.transport+" · Not connected",12,muted,false));
            r.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView delete=text("Remove",12,muted,false);pad(delete,8,8,8,8);
            r.addView(delete);delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Remove "+s.name+"?").setMessage("This removes its saved configuration.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Remove",(d,w)->{store.remove(s.id);render();}).show());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,dp(8),0,dp(4));content.addView(r,lp);
        }
        TextView note=text("Saved sensors are configuration only. Live discovery and readings are not implemented yet.",12,muted,false);
        pad(note,4,12,4,16);content.addView(note);
    }
    private void showAddSensor(SensorStore store) {
        LinearLayout form=column();pad(form,22,8,22,0);
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Sensor name");form.addView(name);
        EditText zone=new EditText(this);zone.setSingleLine(true);zone.setHint("Location / zone");form.addView(zone);
        Spinner type=new Spinner(this);
        type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,SensorStore.TYPES));
        form.addView(type);
        Spinner transport=new Spinner(this);
        transport.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,SensorStore.TRANSPORTS));
        form.addView(transport);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Add sensor").setView(form)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if(name.getText().toString().trim().isEmpty()) {name.setError("Enter a name");return;}
            try {
                store.add(name.getText().toString(),zone.getText().toString(),
                    type.getSelectedItem().toString(),transport.getSelectedItem().toString());
                dialog.dismiss();render();
            } catch(Exception ex) {Toast.makeText(this,"Could not save sensor",Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }

}
