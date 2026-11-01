package com.orbholdings.offlinehub;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local device inventory; no network, account, or cloud service. */
public final class SensorStore {
    public static final String[] TYPES = {"Temperature", "Humidity", "Water level", "Door", "Motion", "Voltage", "Current", "Custom"};
    public static final String[] TRANSPORTS = {"BLE", "Local Wi-Fi", "ESP32 hub", "Manual"};
    public static final class Sensor {
        public String id, name, zone, type, transport;
        public Sensor(String id,String name,String zone,String type,String transport) {
            this.id=id; this.name=name; this.zone=zone; this.type=type; this.transport=transport;
        }
    }
    private final SharedPreferences prefs;
    public SensorStore(Context context) { prefs=context.getSharedPreferences("offline_hub_sensors",Context.MODE_PRIVATE); }
    public List<Sensor> list() {
        List<Sensor> out=new ArrayList<>();
        try {
            JSONArray a=new JSONArray(prefs.getString("inventory","[]"));
            for(int i=0;i<a.length();i++) {
                JSONObject o=a.getJSONObject(i);
                out.add(new Sensor(o.getString("id"),o.getString("name"),o.optString("zone",""),o.getString("type"),o.getString("transport")));
            }
        } catch(Exception ignored) { /* Corrupt storage does not cause a crash; never silently overwrite it. */ }
        return out;
    }
    public void add(String name,String zone,String type,String transport) {
        if(name==null||name.trim().isEmpty()) throw new IllegalArgumentException("Name required");
        List<Sensor> all=list();
        all.add(new Sensor(UUID.randomUUID().toString(),name.trim(),zone.trim(),type,transport));
        save(all);
    }
    public void remove(String id) {
        List<Sensor> all=list();
        for(int i=all.size()-1;i>=0;i--) if(all.get(i).id.equals(id)) all.remove(i);
        save(all);
    }
    private void save(List<Sensor> all) {
        JSONArray a=new JSONArray();
        try {
            for(Sensor s:all) {
                JSONObject o=new JSONObject();
                o.put("id",s.id);o.put("name",s.name);o.put("zone",s.zone);
                o.put("type",s.type);o.put("transport",s.transport);a.put(o);
            }
            if(!prefs.edit().putString("inventory",a.toString()).commit()) throw new IllegalStateException("Unable to save sensors");
        } catch(org.json.JSONException e) { throw new IllegalStateException("Unable to serialize sensors",e); }
    }
}
