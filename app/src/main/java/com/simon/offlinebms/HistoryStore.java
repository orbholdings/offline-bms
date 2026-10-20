package com.simon.offlinebms;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.util.JsonReader;
import android.util.JsonToken;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public final class HistoryStore extends SQLiteOpenHelper {
    public static final int MAX_SAMPLES=260000;
    public static final long KEEP_MS=30L*86400000;
    private int writes;
    public HistoryStore(Context c){super(c,"bms_history.db",null,2);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE readings (id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL, device TEXT NOT NULL, soc REAL NOT NULL, power REAL NOT NULL, voltage REAL NOT NULL, payload TEXT NOT NULL, address TEXT NOT NULL, session TEXT NOT NULL DEFAULT '', current REAL, temperature REAL, delta REAL, remaining REAL)");
        indexes(db);extras(db);
    }
    private void indexes(SQLiteDatabase db){db.execSQL("CREATE INDEX IF NOT EXISTS reading_time ON readings(timestamp)");db.execSQL("CREATE INDEX IF NOT EXISTS reading_battery_time ON readings(address,timestamp)");}
    private void extras(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL, address TEXT NOT NULL, kind TEXT NOT NULL, message TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sessions (session TEXT PRIMARY KEY, address TEXT NOT NULL, name TEXT NOT NULL, start INTEGER, end INTEGER, samples INTEGER, charge_wh REAL, discharge_wh REAL, charge_ah REAL, discharge_ah REAL, observed_ms INTEGER, reason TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS equipment (id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL, address TEXT NOT NULL, payload TEXT NOT NULL)");db.execSQL("CREATE INDEX IF NOT EXISTS equipment_time ON equipment(address,timestamp)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){if(oldV<2){
        for(String sql:new String[]{"ALTER TABLE readings ADD COLUMN address TEXT NOT NULL DEFAULT ''","ALTER TABLE readings ADD COLUMN session TEXT NOT NULL DEFAULT ''","ALTER TABLE readings ADD COLUMN current REAL","ALTER TABLE readings ADD COLUMN temperature REAL","ALTER TABLE readings ADD COLUMN delta REAL","ALTER TABLE readings ADD COLUMN remaining REAL"})db.execSQL(sql);
        try(Cursor c=db.rawQuery("SELECT id,payload FROM readings",null)){while(c.moveToNext())try{Reading r=Reading.fromJson(new JSONObject(c.getString(1)));ContentValues v=extraValues(r,"");db.update("readings",v,"id=?",new String[]{c.getString(0)});}catch(JSONException e){ContentValues v=new ContentValues();v.put("address","legacy:unidentified");db.update("readings",v,"id=?",new String[]{c.getString(0)});}}
        indexes(db);extras(db);
    }}
    private static ContentValues extraValues(Reading r,String session){ContentValues v=new ContentValues();v.put("address",r.address);v.put("session",session);v.put("current",r.basic.current);if(Double.isFinite(r.maxTemp()))v.put("temperature",r.maxTemp());v.put("delta",r.deltaMv());v.put("remaining",r.basic.remainingAh);return v;}
    private static ContentValues values(Reading r,String session)throws JSONException{ContentValues v=extraValues(r,session);v.put("timestamp",r.timestamp);v.put("device",r.device);v.put("soc",r.basic.soc);v.put("power",r.basic.power());v.put("voltage",r.basic.voltage);v.put("payload",r.json().toString());return v;}
    public synchronized void add(Reading r)throws JSONException{add(r,"",30);}
    public synchronized void add(Reading r,String session,int days)throws JSONException{
        if(r.isDemo||r.cells.length==0)return;SQLiteDatabase db=getWritableDatabase();db.insertOrThrow("readings",null,values(r,session));if(++writes%100==1)prune(db,days);
    }
    private void prune(SQLiteDatabase db,int days){long cutoff=System.currentTimeMillis()-Math.max(1,Math.min(90,days))*86400000L;db.delete("readings","timestamp < ?",new String[]{Long.toString(cutoff)});db.execSQL("DELETE FROM readings WHERE id < (SELECT id FROM readings ORDER BY id DESC LIMIT 1 OFFSET "+(MAX_SAMPLES-1)+")");db.delete("equipment","timestamp < ?",new String[]{Long.toString(cutoff)});db.execSQL("DELETE FROM equipment WHERE id < (SELECT id FROM equipment ORDER BY id DESC LIMIT 1 OFFSET "+(MAX_SAMPLES-1)+")");db.delete("events","timestamp < ?",new String[]{Long.toString(cutoff)});db.execSQL("DELETE FROM events WHERE id < (SELECT id FROM events ORDER BY id DESC LIMIT 1 OFFSET 4999)");db.execSQL("DELETE FROM sessions WHERE end < "+cutoff);}
    public synchronized void prune(int days){prune(getWritableDatabase(),days);}
    public synchronized int count(){return count("");}
    public synchronized int count(String address){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM readings"+(address.isEmpty()?"":" WHERE address=?"),address.isEmpty()?null:new String[]{address})){c.moveToFirst();return c.getInt(0);}}
    public synchronized void clear(){SQLiteDatabase db=getWritableDatabase();db.delete("readings",null,null);db.delete("sessions",null,null);db.delete("events",null,null);db.delete("equipment",null,null);}
    public synchronized void clear(String address){for(String table:new String[]{"readings","sessions","events","equipment"})getWritableDatabase().delete(table,"address=?",new String[]{address});}
    public synchronized Reading latest(String address){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM readings"+(address.isEmpty()?"":" WHERE address=?")+" ORDER BY timestamp DESC,id DESC LIMIT 1",address.isEmpty()?null:new String[]{address})){return c.moveToFirst()?Reading.fromJson(new JSONObject(c.getString(0))):null;}catch(JSONException e){return null;}}
    public synchronized List<String[]> batteries(){List<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT address,MAX(device),MAX(timestamp) FROM readings GROUP BY address ORDER BY MAX(timestamp) DESC",null)){while(c.moveToNext())out.add(new String[]{c.getString(0),c.getString(1),c.getString(2)});}return out;}
    public synchronized String lastDevice(){try(Cursor c=getReadableDatabase().rawQuery("SELECT address FROM readings ORDER BY timestamp DESC LIMIT 1",null)){return c.moveToFirst()?c.getString(0):"";}}
    public synchronized List<double[]> chart(String address,long from){
        List<double[]> rows=new ArrayList<>();long bucket=Math.max(10000,(System.currentTimeMillis()-from)/700);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT MIN(timestamp),AVG(soc),AVG(power),AVG(voltage),AVG(current),AVG(temperature),AVG(delta),AVG(remaining) FROM readings WHERE timestamp>=? AND address=? GROUP BY CAST(timestamp / ? AS INTEGER) ORDER BY MIN(timestamp)",new String[]{Long.toString(from),address,Long.toString(bucket)})){while(c.moveToNext()){double[] row=new double[8];for(int i=0;i<8;i++)row[i]=c.isNull(i)?Double.NaN:c.getDouble(i);rows.add(row);}}return rows;
    }
    public synchronized EnergyStats stats(String address,long from){EnergyStats s=new EnergyStats();try(Cursor c=getReadableDatabase().rawQuery("SELECT timestamp,voltage,current,session FROM readings WHERE timestamp>=? AND address=? ORDER BY timestamp,id",new String[]{Long.toString(from),address})){while(c.moveToNext())if(!c.isNull(2))s.add(c.getLong(0),c.getDouble(1),c.getDouble(2),c.getString(3));}return s;}
    public synchronized void event(String address,String kind,String message){ContentValues v=new ContentValues();v.put("timestamp",System.currentTimeMillis());v.put("address",address);v.put("kind",kind);v.put("message",message);getWritableDatabase().insert("events",null,v);}
    public synchronized List<String[]> events(String address){List<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT timestamp,kind,message FROM events WHERE address=? ORDER BY timestamp DESC,id DESC LIMIT 50",new String[]{address})){while(c.moveToNext())out.add(new String[]{c.getString(0),c.getString(1),c.getString(2)});}return out;}
    public synchronized void session(String id,String address,String name,EnergyStats s,String reason){if(s.samples==0)return;ContentValues v=new ContentValues();v.put("session",id);v.put("address",address);v.put("name",name);v.put("start",s.firstTime);v.put("end",s.lastTime);v.put("samples",s.samples);v.put("charge_wh",s.chargeWh);v.put("discharge_wh",s.dischargeWh);v.put("charge_ah",s.chargeAh);v.put("discharge_ah",s.dischargeAh);v.put("observed_ms",s.observedMs);v.put("reason",reason);getWritableDatabase().insertWithOnConflict("sessions",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public synchronized List<String[]> sessions(String address){List<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT start,end,samples,charge_wh,discharge_wh,observed_ms,reason FROM sessions WHERE address=? ORDER BY start DESC LIMIT 30",new String[]{address})){while(c.moveToNext()){String[] row=new String[7];for(int i=0;i<7;i++)row[i]=c.getString(i);out.add(row);}}return out;}
    public synchronized void export(OutputStream output,boolean json,Reading snapshot)throws IOException,JSONException{export(output,json,snapshot,"",0);}
    public synchronized void export(OutputStream output,boolean json,Reading snapshot,String address,long from)throws IOException,JSONException{
        if(snapshot!=null&&snapshot.isDemo)throw new IOException("Demo readings cannot be exported");Writer w=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));
        if(json)w.write("{\"app\":\"Offline BMS\",\"schema_version\":2,\"exported_at\":\""+Instant.now()+"\",\"current_sign\":\"positive charging; negative discharging\",\"readings\":[");
        else{w.write("timestamp_utc,device,address,voltage_v,current_a,power_w,soc_percent,remaining_ah,capacity_ah,cycles,charge_enabled,discharge_enabled,protection_flags,balance_mask,cell_delta_mv");for(int i=1;i<=32;i++)w.write(",cell_"+i+"_v");for(int i=1;i<=16;i++)w.write(",temperature_"+i+"_c");w.write("\r\n");}
        if(snapshot!=null){if(json)w.write(snapshot.json().toString());else csvRow(w,snapshot.json());}
        else try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM readings WHERE timestamp>=?"+(address.isEmpty()?"":" AND address=?")+" ORDER BY timestamp,id",address.isEmpty()?new String[]{Long.toString(from)}:new String[]{Long.toString(from),address})){boolean first=true;while(c.moveToNext()){JSONObject j=new JSONObject(c.getString(0));if(!j.has("address"))j.put("address",Reading.fromJson(j).address);if(json){if(!first)w.write(',');w.write(j.toString());first=false;}else csvRow(w,j);}}
        if(json)w.write("]}");w.flush();
    }
    private static void csvRow(Writer w,JSONObject j)throws IOException,JSONException{
        List<String> v=new ArrayList<>();v.add(Instant.ofEpochMilli(j.getLong("timestamp_unix_ms")).toString());v.add(j.getString("device"));v.add(j.optString("address",""));for(String k:new String[]{"voltage_v","current_a","power_w","soc_percent","remaining_ah","capacity_ah","cycles","charge_enabled","discharge_enabled","protection_flags","balance_mask","cell_delta_mv"})v.add(j.has(k)?j.get(k).toString():"");JSONArray cells=j.getJSONArray("cell_voltages_v"),temps=j.getJSONArray("temperatures_c");for(int i=0;i<32;i++)v.add(i<cells.length()?cells.get(i).toString():"");for(int i=0;i<16;i++)v.add(i<temps.length()?temps.get(i).toString():"");for(int i=0;i<v.size();i++){if(i>0)w.write(',');String x=v.get(i);if((i==1||i==2)&&x.matches("^[\\s]*[=+@-].*"))x="'"+x;w.write('"');w.write(x.replace("\"","\"\""));w.write('"');}w.write("\r\n");
    }
    public synchronized void backup(OutputStream out,ProfileStore profiles)throws IOException,JSONException{
        Writer w=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));w.write("{\"backup_version\":1,\"app\":\"Offline BMS\",\"created_at\":\""+Instant.now()+"\",\"profiles\":"+profiles.json()+",\"readings\":[");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload,session FROM readings ORDER BY timestamp,id",null)){boolean first=true;while(c.moveToNext()){JSONObject j=new JSONObject(c.getString(0));j.put("session_id",c.getString(1));if(!first)w.write(',');w.write(j.toString());first=false;}}w.write("],\"equipment\":[");try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM equipment ORDER BY timestamp,id",null)){boolean first=true;while(c.moveToNext()){if(!first)w.write(',');w.write(c.getString(0));first=false;}}w.write("]}");w.flush();
    }
    public synchronized int restore(InputStream input,ProfileStore profiles,int days)throws IOException,JSONException{
        List<ProfileStore.Profile> imported=new ArrayList<>();int added=0,total=0;boolean version=false,hasReadings=false;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        InputStream bounded=new FilterInputStream(input){long bytes;private void check(int n)throws IOException{if(n>0 && (bytes+=n)>512L*1024*1024)throw new IOException("Backup exceeds 512 MB");}@Override public int read()throws IOException{int n=super.read();check(n<0?0:1);return n;}@Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);check(n);return n;}};
        try(JsonReader reader=new JsonReader(new InputStreamReader(bounded,StandardCharsets.UTF_8))){reader.beginObject();while(reader.hasNext()){String key=reader.nextName();if(key.equals("backup_version")){if(reader.nextInt()!=1)throw new IOException("Unsupported backup version");version=true;}
            else if(key.equals("profiles")){if(!version)throw new IOException("Not an Offline BMS backup");reader.beginArray();while(reader.hasNext()){if(imported.size()>=100)throw new IOException("Too many battery profiles");imported.add(ProfileStore.Profile.parse((JSONObject)readValue(reader,0)));}reader.endArray();}
            else if(key.equals("readings")){if(!version||hasReadings)throw new IOException("Invalid backup");hasReadings=true;reader.beginArray();while(reader.hasNext()){if(++total>MAX_SAMPLES)throw new IOException("Too many readings");JSONObject j=(JSONObject)readValue(reader,0);Reading r=Reading.fromJson(j);String session=j.optString("session_id","");if(session.length()>80)throw new IOException("Invalid session");try(Cursor c=db.rawQuery("SELECT 1 FROM readings WHERE address=? AND timestamp=? LIMIT 1",new String[]{r.address,Long.toString(r.timestamp)})){if(!c.moveToFirst()){db.insertOrThrow("readings",null,values(r,session));added++;}}}reader.endArray();}
            else if(key.equals("equipment")){if(!version)throw new IOException("Invalid backup");int count=0;reader.beginArray();while(reader.hasNext()){if(++count>MAX_SAMPLES)throw new IOException("Too many equipment readings");EquipmentReading r=EquipmentReading.parse((JSONObject)readValue(reader,0));try(Cursor c=db.rawQuery("SELECT 1 FROM equipment WHERE address=? AND timestamp=? LIMIT 1",new String[]{r.address,Long.toString(r.timestamp)})){if(!c.moveToFirst()){ContentValues v=new ContentValues();v.put("timestamp",r.timestamp);v.put("address",r.address);v.put("payload",r.json().toString());db.insertOrThrow("equipment",null,v);added++;}}}reader.endArray();}
            else reader.skipValue();}
            reader.endObject();if(!version||!hasReadings||reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Incomplete backup");
            int newProfiles=0;Set<String> addresses=new HashSet<>();for(ProfileStore.Profile p:imported)if(profiles.get(p.address)==null&&addresses.add(p.address))newProfiles++;if(profiles.all().size()+newProfiles>100)throw new IOException("Maximum of 100 saved batteries");
            prune(db,days);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        for(ProfileStore.Profile p:imported)if(profiles.get(p.address)==null)profiles.put(p);return added;
    }
    private static Object readValue(JsonReader r,int depth)throws IOException,JSONException{
        if(depth>5)throw new IOException("Backup nesting is too deep");switch(r.peek()){
            case BEGIN_OBJECT:JSONObject j=new JSONObject();r.beginObject();int count=0;while(r.hasNext()){if(++count>80)throw new IOException("Too many fields");String k=r.nextName();if(k.length()>80)throw new IOException("Invalid field");j.put(k,readValue(r,depth+1));}r.endObject();return j;
            case BEGIN_ARRAY:JSONArray a=new JSONArray();r.beginArray();while(r.hasNext()){if(a.length()>=64)throw new IOException("Array too large");a.put(readValue(r,depth+1));}r.endArray();return a;
            case STRING:String s=r.nextString();if(s.length()>2500)throw new IOException("Text too long");return s;
            case NUMBER:String n=r.nextString();try{if(n.matches("-?[0-9]+"))return Long.parseLong(n);double d=Double.parseDouble(n);if(!Double.isFinite(d))throw new NumberFormatException();return d;}catch(NumberFormatException e){throw new IOException("Invalid number");}
            case BOOLEAN:return r.nextBoolean();case NULL:r.nextNull();return JSONObject.NULL;default:throw new IOException("Invalid backup value");
        }
    }
    public synchronized void equipment(EquipmentReading r,int days)throws JSONException{ContentValues v=new ContentValues();v.put("timestamp",r.timestamp);v.put("address",r.address);v.put("payload",r.json().toString());SQLiteDatabase db=getWritableDatabase();db.insertOrThrow("equipment",null,v);if(++writes%100==1)prune(db,days);db.delete("equipment","timestamp < ?",new String[]{Long.toString(System.currentTimeMillis()-Math.max(1,Math.min(90,days))*86400000L)});db.execSQL("DELETE FROM equipment WHERE id < (SELECT id FROM equipment ORDER BY id DESC LIMIT 1 OFFSET "+(MAX_SAMPLES-1)+")");}
    public synchronized EquipmentReading latestEquipment(String address){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM equipment WHERE address=? ORDER BY timestamp DESC,id DESC LIMIT 1",new String[]{address})){return c.moveToFirst()?EquipmentReading.parse(new JSONObject(c.getString(0))):null;}catch(JSONException e){return null;}}
    public synchronized void exportEquipment(OutputStream output,boolean json)throws IOException,JSONException{Writer w=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));if(json)w.write("{\"app\":\"Offline BMS\",\"schema_version\":2,\"equipment_readings\":[");else w.write("timestamp_utc,address,name,record_type,state,alarm_flags,values_json,units_json,details_json\r\n");try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM equipment ORDER BY timestamp,id",null)){boolean first=true;while(c.moveToNext()){if(json){if(!first)w.write(',');w.write(c.getString(0));}else{JSONObject j=new JSONObject(c.getString(0));String[] values={Instant.ofEpochMilli(j.getLong("timestamp_unix_ms")).toString(),j.getString("address"),j.getString("name"),j.get("record_type").toString(),j.get("state").toString(),j.get("alarm_flags").toString(),j.getJSONObject("values").toString(),j.getJSONObject("units").toString(),j.optJSONObject("details")==null?"{}":j.getJSONObject("details").toString()};for(int i=0;i<values.length;i++){if(i>0)w.write(',');String v=values[i];if(i==2&&v.matches("^[\\s]*[=+@-].*"))v="'"+v;w.write('"');w.write(v.replace("\"","\"\""));w.write('"');}w.write("\r\n");}first=false;}}if(json)w.write("]}");w.flush();}
}
