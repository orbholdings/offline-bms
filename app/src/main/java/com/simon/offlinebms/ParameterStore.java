package com.simon.offlinebms;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Latest read and durable pre-write snapshot; no credentials or automatic restore. */
public final class ParameterStore {
    private final SharedPreferences prefs;
    public ParameterStore(Context c){prefs=c.getSharedPreferences("jbd_parameters",Context.MODE_PRIVATE);}
    public static JSONObject json(JbdSettings.Snapshot s)throws JSONException{JSONObject j=new JSONObject();j.put("schema",1).put("address",s.address).put("model",s.model).put("firmware",s.firmware).put("cells",s.cells).put("telemetry_capacity_ah",s.telemetryCapacity).put("extended",s.extended).put("timestamp",s.timestamp);JSONObject values=new JSONObject();for(Map.Entry<Integer,byte[]> e:s.raw().entrySet())values.put(String.format(Locale.ROOT,"%02X",e.getKey()),JbdProtocol.hex(e.getValue()).replace(" ",""));j.put("registers",values);return j;}
    public static JbdSettings.Snapshot parse(String text)throws JSONException{JSONObject j=new JSONObject(text);if(j.getInt("schema")!=1)throw new JSONException("Unknown schema");Map<Integer,byte[]> values=new LinkedHashMap<>();JSONObject data=j.getJSONObject("registers");Iterator<String> keys=data.keys();while(keys.hasNext()){String k=keys.next();int reg=Integer.parseInt(k,16);String h=data.getString(k);if(!h.matches("(?:[0-9a-fA-F]{2}){1,64}"))throw new JSONException("Invalid register data");byte[] p=new byte[h.length()/2];for(int i=0;i<p.length;i++)p[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16);values.put(reg,p);}return new JbdSettings.Snapshot(j.getString("address"),j.optString("model"),j.optString("firmware"),j.getInt("cells"),j.getDouble("telemetry_capacity_ah"),j.getBoolean("extended"),j.getLong("timestamp"),values);}
    public JbdSettings.Snapshot get(String address,boolean before){try{return parse(prefs.getString((before?"before:":"latest:")+address,""));}catch(Exception e){return null;}}
    public boolean before(JbdSettings.Snapshot snapshot){try{return prefs.edit().putString("before:"+snapshot.address,json(snapshot).toString()).commit();}catch(Exception e){return false;}}
    public void latest(JbdSettings.Snapshot snapshot){try{prefs.edit().putString("latest:"+snapshot.address,json(snapshot).toString()).apply();}catch(JSONException ignored){}}
    public String export(String address)throws JSONException{JSONObject out=new JSONObject().put("format","Offline BMS JBD parameter snapshots").put("schema",1);JbdSettings.Snapshot latest=get(address,false),before=get(address,true);if(latest!=null)out.put("latest",json(latest));if(before!=null)out.put("before_last_write",json(before));out.put("note","Snapshots are reference copies. No automatic hardware restore. Credentials are excluded.");return out.toString(2);}
}
