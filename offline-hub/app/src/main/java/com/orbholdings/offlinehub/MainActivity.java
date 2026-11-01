package com.orbholdings.offlinehub;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.text.TextUtils;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(11, 16, 26);
    private final int panel = Color.rgb(25, 34, 49);
    private final int muted = Color.rgb(154, 169, 189);
    private final int teal = Color.rgb(73, 218, 191);
    private LinearLayout root, content;
    private String section = "Overview";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        render();
    }

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
        root = column(); root.setBackgroundColor(bg);
        setContentView(root);
        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL); pad(header,20,20,20,14);
        TextView name = text("◈  OFFLINE HUB",22,Color.WHITE,true);
        header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView status = text("●  DEMO · OFFLINE",11,teal,true); header.addView(status);
        root.addView(header);
        LinearLayout nav = row(); pad(nav,12,2,12,10);
        String[] tabs = {"Overview","Energy","Heating","Lights","Sensors"};
        for (String tab: tabs) {
            TextView button = text(tab,11,tab.equals(section)?teal:muted,tab.equals(section));
            button.setGravity(Gravity.CENTER); pad(button,5,12,5,12);
            button.setBackground(background(tab.equals(section)?panel:bg,12));
            nav.addView(button,new LinearLayout.LayoutParams(0,-2,1));
            button.setOnClickListener(v -> {section=tab; render();});
        }
        root.addView(nav);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content = column(); pad(content,16,12,16,20); scroll.addView(content);
        TextView heading = text(section,26,Color.WHITE,true); content.addView(heading);
        TextView subtitle = text("Your van, your data. No cloud required.",13,muted,false);
        pad(subtitle,0,5,0,20); content.addView(subtitle);
        if (section.equals("Overview")) {
            addTiles(new String[][] {
                {"BATTERY","82%","12.9 V  ·  +4.2 A","▰▰▰▰▰▰▰▰▱▱"},
                {"SOLAR","186 W","Charging · Demo","☀"},
                {"HEATER","19.5°C","Off · Not connected","♨"},
                {"LIGHTS","3 zones","No devices paired","◉"},
                {"FRESH WATER","68%","Sensor not connected","◒"},
                {"INSIDE TEMP","21.3°C","Demo reading","◌"}
            });
        } else if (section.equals("Energy")) {
            addTiles(new String[][] {{"HOUSE BATTERY","82%","Demo · 12.9 V","▰▰▰▰▰▰▰▰▱▱"},{"SOLAR INPUT","186 W","Demo only","☀"},{"BATTERY CURRENT","+4.2 A","Demo only","↗"}});
        } else if (section.equals("Heating")) {
            addTiles(new String[][] {{"DIESEL HEATER","Not connected","BLE discovery coming next","♨"},{"TEMPERATURE","19.5°C","Demo only · no heater commands enabled","◌"}});
        } else if (section.equals("Lights")) {
            addTiles(new String[][] {{"LIVING AREA","Not paired","Local lighting integration planned","◉"},{"SLEEPING AREA","Not paired","Local lighting integration planned","◉"}});
        } else {
            addTiles(new String[][] {{"FRESH WATER","68%","Demo only","◒"},{"TEMPERATURE","21.3°C","Demo only","◌"},{"DOORS","Not paired","ESP32 sensors planned","▣"}});
        }
        TextView footer = text("PROTOTYPE v0.1  ·  All readings are simulated. No hardware controls.",12,muted,false);
        pad(footer,4,24,4,10); content.addView(footer);
    }
    private void addTiles(String[][] tiles) {
        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
            && getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density >= 700;
        int cols = wide ? 3 : 2;
        for (int i=0;i<tiles.length;i+=cols) {
            LinearLayout r = row(); content.addView(r);
            for (int j=0;j<cols;j++) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,dp(172),1);
                p.setMargins(dp(5),dp(5),dp(5),dp(5));
                if (i+j>=tiles.length) {r.addView(new View(this),p);continue;}
                String[] item=tiles[i+j];
                LinearLayout card=column(); pad(card,17,16,12,12);
                card.setBackground(background(panel,18));
                TextView label=text(item[0],11,muted,true); card.addView(label);
                TextView value=text(item[1],25,Color.WHITE,true); pad(value,0,14,0,7);card.addView(value);
                TextView detail=text(item[2],12,muted,false);detail.setMaxLines(2);detail.setEllipsize(TextUtils.TruncateAt.END);card.addView(detail);
                TextView mark=text(item[3],16,teal,false);pad(mark,0,9,0,0);card.addView(mark);
                r.addView(card,p);
            }
        }
    }
}
