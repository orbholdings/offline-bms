package com.simon.offlinebms;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

public final class ProfileStore {
    public static final class Profile {
        public String address,name,chemistry="Unknown / other",notes="",protocol="Auto";
        public double capacityAh,nominalV,reservePercent=10;
        public final AlertEngine.Config alerts=new AlertEngine.Config();
        public Profile(String address,String name){this.address=address;this.name=name;}
        public JSONObject json()throws JSONException{
            JSONObject j=new JSONObject();j.put("address",address);j.put("name",name);j.put("chemistry",chemistry);j.put("notes",notes);j.put("protocol",protocol);j.put("capacity_ah",capacityAh);j.put("nominal_v",nominalV);j.put("reserve_percent",reservePercent);
            j.put("alerts_enabled",alerts.enabled);j.put("soc_alert",alerts.socEnabled);j.put("temp_alert",alerts.tempEnabled);j.put("spread_alert",alerts.spreadEnabled);j.put("cell_alert",alerts.cellsEnabled);j.put("low_soc",alerts.lowSoc);j.put("high_temp_c",alerts.highTemp);j.put("spread_mv",alerts.spreadMv);j.put("low_cell_v",alerts.lowCell);j.put("high_cell_v",alerts.highCell);return j;
        }
        public static Profile parse(JSONObject j)throws JSONException{
            String address=j.getString("address");if(!address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))throw new JSONException("Invalid battery address");
            Profile p=new Profile(address.toUpperCase(Locale.ROOT),j.getString("name"));
            if(p.name.trim().isEmpty()||p.name.length()>80)throw new JSONException("Invalid battery name");
            p.chemistry=j.optString("chemistry","Unknown / other");p.notes=j.optString("notes","");if(p.chemistry.length()>80||p.notes.length()>2000)throw new JSONException("Profile text is too long");
            p.protocol=j.optString("protocol","Auto");if(!Arrays.asList("Auto","JBD","JK02_24S","JK02_32S","Victron").contains(p.protocol))throw new JSONException("Unsupported protocol");
            p.capacityAh=number(j,"capacity_ah",0,0,100000);p.nominalV=number(j,"nominal_v",0,0,650);p.reservePercent=number(j,"reserve_percent",10,0,100);
            AlertEngine.Config a=p.alerts;a.enabled=j.optBoolean("alerts_enabled",false);a.socEnabled=j.optBoolean("soc_alert",true);a.tempEnabled=j.optBoolean("temp_alert",false);a.spreadEnabled=j.optBoolean("spread_alert",false);a.cellsEnabled=j.optBoolean("cell_alert",false);
            a.lowSoc=number(j,"low_soc",20,0,100);a.highTemp=number(j,"high_temp_c",55,-50,150);a.spreadMv=number(j,"spread_mv",100,1,6500);a.lowCell=number(j,"low_cell_v",2.8,0,6.5);a.highCell=number(j,"high_cell_v",3.65,0,6.5);if(a.lowCell>=a.highCell)throw new JSONException("Low cell limit must be below high cell limit");return p;
        }
        private static double number(JSONObject j,String key,double value,double min,double max)throws JSONException{double n=j.optDouble(key,value);if(!Double.isFinite(n)||n<min||n>max)throw new JSONException("Invalid "+key);return n;}
    }
    private final SharedPreferences prefs;
    public ProfileStore(Context c){prefs=c.getSharedPreferences("bms_profiles",Context.MODE_PRIVATE);}
    public synchronized List<Profile> all(){List<Profile> out=new ArrayList<>();for(Map.Entry<String,?> e:prefs.getAll().entrySet())try{if(e.getValue() instanceof String)out.add(Profile.parse(new JSONObject((String)e.getValue())));}catch(JSONException ignored){}out.sort(Comparator.comparing(p->p.name.toLowerCase(Locale.ROOT)));return out;}
    public synchronized Profile get(String address){if(address==null)return null;try{return Profile.parse(new JSONObject(prefs.getString(address.toUpperCase(Locale.ROOT),"")));}catch(JSONException e){return null;}}
    public synchronized void put(Profile p)throws JSONException{Profile checked=Profile.parse(p.json());if(get(checked.address)==null&&all().size()>=100)throw new JSONException("Maximum of 100 saved batteries");prefs.edit().putString(checked.address,checked.json().toString()).apply();}
    public synchronized void remove(String address){prefs.edit().remove(address.toUpperCase(Locale.ROOT)).apply();}
    public synchronized JSONArray json()throws JSONException{JSONArray a=new JSONArray();for(Profile p:all())a.put(p.json());return a;}
}
