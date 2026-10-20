package com.simon.offlinebms;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Runs on an emulator; uses the test package's database, never a real battery. */
public final class LocalDataTests extends Instrumentation {
    int checks;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        HistoryStore store=null;
        Bundle result=new Bundle();
        try {
            getContext().deleteDatabase("bms_history.db");
            store=new HistoryStore(getContext());
            Reading demo=Reading.demo();
            Reading a=new Reading(System.currentTimeMillis(),demo.basic,demo.cells,"=battery,local");
            store.add(a);check(store.count()==1,"SQLite saves complete samples");
            Reading incomplete=new Reading(System.currentTimeMillis(),demo.basic,new double[0],"incomplete");store.add(incomplete);check(store.count()==1,"Incomplete samples cannot enter history");
            Reading other=new Reading(System.currentTimeMillis(),demo.basic,demo.cells,"other battery");store.add(other);
            Reading old=new Reading(System.currentTimeMillis()-HistoryStore.KEEP_MS-10000,demo.basic,demo.cells,"old battery");store.add(old);store.prune(30);check(store.count()==2,"Readings older than 30 days are removed");
            List<double[]> chart=store.chart(a.address,System.currentTimeMillis()-3600000);check(chart.size()==1,"Charts isolate different batteries");check(chart.get(0)[1]==70,"SOC graph uses percent units");check(chart.get(0)[2]<0,"History preserves discharge power sign");
            ByteArrayOutputStream jsonOut=new ByteArrayOutputStream();store.export(jsonOut,true,null);JSONObject json=new JSONObject(jsonOut.toString("UTF-8"));JSONArray rows=json.getJSONArray("readings");check(rows.length()==2,"JSON export includes all retained samples");JSONObject row=rows.getJSONObject(0);check(row.getJSONArray("cell_voltages_v").length()==16,"JSON includes every cell");check(row.getJSONArray("temperatures_c").length()==2,"JSON includes temperatures");check(row.getDouble("current_a")==-12.4,"JSON current sign");
            ByteArrayOutputStream csvOut=new ByteArrayOutputStream();store.export(csvOut,false,null);String csv=csvOut.toString("UTF-8");String[] lines=csv.split("\r\n");check(lines.length==3,"CSV row count");check(lines[0].split(",").length==63,"CSV fixed column schema");check(csv.contains("\"'=battery,local\""),"Device name is quoted and spreadsheet-formula escaped");check(csv.contains("cell_16_v"),"CSV includes cell columns");check(csv.contains("temperature_16_c"),"CSV includes all sensor columns");
            Reading snapshotDemo=Reading.demo();ByteArrayOutputStream snapshotOut=new ByteArrayOutputStream();store.export(snapshotOut,true,a);JSONObject snapshot=new JSONObject(snapshotOut.toString("UTF-8"));check(snapshot.getJSONArray("readings").length()==1,"Latest reading export does not include history");check(snapshot.getInt("schema_version")==2,"JSON schema version");
            store.clear();check(store.count()==0,"Delete history removes all stored readings");
            result.putString("stream","PASS: "+checks+" local storage and export checks\n");finish(-1,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e);finish(0,result);}finally{if(store!=null)store.close();getContext().deleteDatabase("bms_history.db");}
    }
}
