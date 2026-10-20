package com.simon.offlinebms;

import android.content.Context;

/** One bounded, private connection snapshot, independent of the service lifecycle. */
public final class ConnectionDiagnostics {
    private static final String STORE="connection_diagnostics",KEY="last_attempt";
    private static final int MAX_CHARS=65536;
    private ConnectionDiagnostics(){}
    public static void save(Context context,String text){
        if(text==null||text.isEmpty())return;
        if(text.length()>MAX_CHARS)text=text.substring(0,MAX_CHARS-32)+"\n[Diagnostic log truncated]\n";
        context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().putString(KEY,text).apply();
    }
    public static String last(Context context){
        String text=context.getSharedPreferences(STORE,Context.MODE_PRIVATE).getString(KEY,"");
        return text.isEmpty()?"No connection attempt has been recorded yet. Connect to the battery, then export diagnostics.":"SAVED CONNECTION ATTEMPT (may no longer be active)\n"+text;
    }
}
