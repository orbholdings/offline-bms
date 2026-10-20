package com.simon.offlinebms;

import java.util.*;

/** Acknowledged one-parameter transaction; writes are never automatically retried. */
public final class JbdSettingsSession {
    enum Phase {NEW,AUTH,ENTER,READ,BACKUP,WRITE,CHECK,COMMIT,VERIFY_ENTER,VERIFY_READ,EXIT,CLEANUP,DONE}
    public static final class Action {
        public final byte[] packet;
        public final String message;
        public final boolean backup,complete,success,reconnect;
        public final JbdSettings.Snapshot snapshot;
        Action(byte[] p,String m,boolean b,boolean done,boolean ok,boolean reconnect,JbdSettings.Snapshot s){packet=p;message=m;backup=b;complete=done;success=ok;this.reconnect=reconnect;snapshot=s;}
    }
    private Phase phase=Phase.NEW;
    private String password,error="";
    private final JbdSettings.Snapshot basis,expected;
    private final JbdSettings.Field field;
    private final String requested;
    private final Map<Integer,byte[]> values=new LinkedHashMap<>();
    private byte[] desired;
    private int index,verifyIndex;
    private boolean entered,written,committed;
    public JbdSettingsSession(JbdSettings.Snapshot basis,String password){this(basis,password,null,null,null);}
    public JbdSettingsSession(JbdSettings.Snapshot basis,String password,JbdSettings.Snapshot expected,JbdSettings.Field field,String requested){JbdAuthentication.validatePassword(password);this.basis=basis;this.password=password==null?"":password;this.expected=expected;this.field=field;this.requested=requested;if(field!=null){if(expected==null||!basis.address.equals(expected.address))throw new IllegalArgumentException("Read this battery's parameters first");expected.change(field,requested);}}
    public boolean writeSent(){return written;}
    public Action start(){if(phase!=Phase.NEW)throw new IllegalStateException("Session already started");if(!password.isEmpty()){phase=Phase.AUTH;byte[] p=JbdSettings.password(password);password="";return send(p,"Unlocking JBD parameters…");}return enter(false);}
    private Action enter(boolean verify){phase=verify?Phase.VERIFY_ENTER:Phase.ENTER;return send(JbdSettings.enter(),verify?"Checking saved value…":"Reading battery parameters…");}
    private Action send(byte[] p,String m){return new Action(p,m,false,false,false,false,null);}
    private JbdSettings.Snapshot snapshot(){double cap=committed&&field!=null&&field.register==0x10?JbdSettings.u16(desired)/100.0:basis.telemetryCapacity;return new JbdSettings.Snapshot(basis.address,basis.model,basis.firmware,basis.cells,cap,basis.extended,System.currentTimeMillis(),values);}
    public JbdSettings.Snapshot beforeWrite(){if(phase!=Phase.BACKUP)throw new IllegalStateException("Not at backup boundary");return snapshot();}
    public Action backupReady(boolean saved){if(phase!=Phase.BACKUP)throw new IllegalStateException("Not awaiting backup");if(!saved)return fail("Could not save the original parameters on this phone; no parameter write was sent");phase=Phase.WRITE;written=true;return send(JbdSettings.write(field,desired),"Writing "+field.label+"…");}
    public Action receive(JbdProtocol.Frame frame){
        if(phase==Phase.DONE||phase==Phase.NEW||phase==Phase.BACKUP)return null;
        int reg=phase==Phase.AUTH?6:phase==Phase.ENTER||phase==Phase.VERIFY_ENTER?0:phase==Phase.READ?JbdSettings.READ_ORDER[index]:phase==Phase.WRITE||phase==Phase.CHECK?field.register:phase==Phase.VERIFY_READ?verifyIndex==0?field.register:0xaa:1;
        if(frame.register!=reg)return null;
        boolean data=phase==Phase.READ||phase==Phase.CHECK||phase==Phase.VERIFY_READ;
        if(!data&&frame.payload.length!=0)return null; // late duplicate read cannot acknowledge a write
        if(data&&frame.status==0&&frame.payload.length==0)return null; // duplicate write acknowledgement
        if(phase==Phase.CLEANUP){return finish(false,frame.status==0?error:error+" Factory-mode exit was not acknowledged; reconnect and read parameters.",frame.status!=0);}
        if(frame.status!=0){
            if(phase==Phase.READ){index++;return nextRead();}
            if(phase==Phase.VERIFY_READ&&verifyIndex==1){values.remove(0xaa);return exit();}
            return fail("The BMS refused the parameter command (0x"+Integer.toHexString(frame.status)+")"+(phase==Phase.AUTH||phase==Phase.ENTER?". Check its settings password and firmware compatibility":""));
        }
        switch(phase){
            case AUTH:return enter(false);
            case ENTER:entered=true;index=0;phase=Phase.READ;return nextRead();
            case READ:
                if(frame.payload.length==(reg==0xaa?22:2))values.put(reg,frame.payload.clone());
                index++;return nextRead();
            case WRITE:phase=Phase.CHECK;return send(JbdSettings.read(field.register),"Checking the written value…");
            case CHECK:
                if(!Arrays.equals(frame.payload,desired))return fail("Readback did not match the requested value");
                phase=Phase.COMMIT;return send(JbdSettings.exit(true),"Saving the value in the BMS…");
            case COMMIT:entered=false;committed=true;return enter(true);
            case VERIFY_ENTER:entered=true;verifyIndex=0;phase=Phase.VERIFY_READ;return send(JbdSettings.read(field.register),"Verifying the saved value…");
            case VERIFY_READ:
                if(verifyIndex==0){if(!Arrays.equals(frame.payload,desired))return fail("The saved value did not match; read parameters again");values.put(reg,frame.payload.clone());verifyIndex=1;return send(JbdSettings.read(0xaa),"Reading protection counters…");}
                if(frame.payload.length==22)values.put(0xaa,frame.payload.clone());else values.remove(0xaa);
                return exit();
            case EXIT:entered=false;return finish(true,field==null?"Parameters read from battery":"Saved and verified on battery",false);
            default:return null;
        }
    }
    private Action nextRead(){
        if(index<JbdSettings.READ_ORDER.length)return send(JbdSettings.read(JbdSettings.READ_ORDER[index]),"Reading parameters "+(index+1)+" / "+JbdSettings.READ_ORDER.length+"…");
        if(field==null)return exit();
        JbdSettings.Snapshot current=snapshot();
        try{
            if(System.currentTimeMillis()-expected.timestamp>300000)throw new IllegalArgumentException("Parameters are over five minutes old; read them again");
            for(JbdSettings.Field f:JbdSettings.FIELDS)if(expected.get(f.register)!=null&&!Arrays.equals(expected.get(f.register),current.get(f.register)))throw new IllegalArgumentException("Battery parameters changed since review; read them again");
            desired=current.change(field,requested);
            if(Arrays.equals(desired,current.get(field.register)))throw new IllegalArgumentException("The value is already set; no write is needed");
        }catch(IllegalArgumentException e){return fail(e.getMessage());}
        phase=Phase.BACKUP;return new Action(null,"Saving the original parameters on this phone…",true,false,false,false,null);
    }
    private Action exit(){phase=Phase.EXIT;return send(JbdSettings.exit(false),"Closing parameter session…");}
    private Action fail(String message){error=message+(written?". A write was sent; some changes may have taken effect. Read parameters again before another edit":"");password="";if(entered||phase==Phase.ENTER||phase==Phase.VERIFY_ENTER||phase==Phase.COMMIT){phase=Phase.CLEANUP;return send(JbdSettings.exit(false),error+" · closing parameter session…");}return finish(false,error,false);}
    public Action timeout(){if(phase==Phase.CLEANUP)return finish(false,error+" Factory-mode exit timed out; reconnect and read parameters.",true);return fail("Parameter response timed out; no command will be retried");}
    public Action interrupted(){password="";return finish(false,"Parameter session interrupted"+(written?"; the battery may have changed. Reconnect and read parameters":""),false);}
    private Action finish(boolean success,String message,boolean reconnect){phase=Phase.DONE;password="";return new Action(null,message,false,true,success,reconnect,success?snapshot():null);}
}
