package com.simon.offlinebms;

import java.util.*;

/** Independent wire examples plus a simulated BMS for failure-path checks. No radio access. */
public final class SettingsTests {
    static int passed;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;}
    static void invalid(Runnable r,String name){try{r.run();throw new AssertionError(name);}catch(IllegalArgumentException expected){passed++;}}
    static void near(double a,double b,String name){check(Math.abs(a-b)<1e-8,name);}
    static byte[] w(int n){return new byte[]{(byte)(n>>8),(byte)n};}
    static Map<Integer,byte[]> values(){Map<Integer,byte[]> p=new LinkedHashMap<>();int[][] words={
        {0x10,28000},{0x11,28000},{0x14,0},{0x12,3450},{0x13,2800},{0x32,3340},{0x33,3300},{0x34,3250},{0x35,3200},
        {0x18,3331},{0x19,3231},{0x1a,2731},{0x1b,2781},{0x1c,3431},{0x1d,3331},{0x1e,2531},{0x1f,2631},
        {0x20,6000},{0x21,5760},{0x22,3520},{0x23,4160},{0x24,3750},{0x25,3600},{0x26,2200},{0x27,2600},
        {0x28,20000},{0x29,45536},{0x2a,3400},{0x2b,15},{0x2c,10},{0x2d,0xab0c},{0x2e,31},{0x2f,16},{0x15,0x3450},{0x16,42},{0x17,379}};
        for(int[] v:words)p.put(v[0],w(v[1]));byte[] counters=new byte[22];counters[1]=7;p.put(0xaa,counters);return p;}
    static JbdSettings.Snapshot snapshot(Map<Integer,byte[]> p){return new JbdSettings.Snapshot("AA:BB:CC:DD:EE:01","SP24S004L24S200A","2.4",16,280,false,System.currentTimeMillis(),p);}
    static JbdProtocol.Frame reply(int reg,int status,byte[] p){return new JbdProtocol.Frame(reg,status,p);}
    static byte[] wire(int reg,int status,byte[] p){byte[] a=new byte[p.length+7];a[0]=(byte)0xdd;a[1]=(byte)reg;a[2]=(byte)status;a[3]=(byte)p.length;System.arraycopy(p,0,a,4,p.length);int sum=status+p.length;for(byte b:p)sum+=b&255;int crc=(-sum)&65535;a[a.length-3]=(byte)(crc>>8);a[a.length-2]=(byte)crc;a[a.length-1]=0x77;return a;}
    static class Bms {
        Map<Integer,byte[]> eeprom=values(),ram=new LinkedHashMap<>();boolean factory,backup=true,badReadback,badPersist,duplicateAck,refuseCommit,dropWrite;int writes,commits,normalExits,enters;JbdSettings.Snapshot before;
        JbdSettingsSession.Action run(JbdSettingsSession s){JbdSettingsSession.Action a=s.start();for(int n=0;n<200&&!a.complete;n++){
            if(a.backup){before=s.beforeWrite();a=s.backupReady(backup);continue;}
            byte[] packet=a.packet;if(packet==null)throw new AssertionError("Missing action");int mode=packet[1]&255,reg=packet[2]&255,len=packet[3]&255;byte[] p=Arrays.copyOfRange(packet,4,4+len);int sum=reg+len;for(byte b:p)sum+=b&255;int expected=(-sum)&65535;if(expected!=((packet[packet.length-3]&255)<<8|(packet[packet.length-2]&255)))throw new AssertionError("Request checksum");
            JbdProtocol.Frame r;
            if(mode==0xa5){if(!factory)throw new AssertionError("Read outside factory session");byte[] v=ram.get(reg);if(badReadback&&writes>0&&reg==0x24)v=w(3700);r=reply(reg,v==null?0x80:0,v==null?new byte[0]:v);}
            else if(reg==6){if(len!=7||p[0]!=6)throw new AssertionError("Password format");r=reply(reg,0,new byte[0]);}
            else if(reg==0){if(!Arrays.equals(p,w(0x5678)))throw new AssertionError("Factory key");factory=true;enters++;ram=new LinkedHashMap<>(eeprom);r=reply(reg,0,new byte[0]);}
            else if(reg==1){if(Arrays.equals(p,w(0x2828))){commits++;if(refuseCommit){a=s.receive(reply(reg,0x80,new byte[0]));continue;}eeprom=new LinkedHashMap<>(ram);eeprom.put(0xaa,new byte[22]);if(badPersist)eeprom.put(0x24,w(3700));}else if(Arrays.equals(p,w(0)))normalExits++;else throw new AssertionError("Unknown exit");factory=false;r=reply(reg,0,new byte[0]);}
            else{if(!factory||len!=2||!JbdSettings.writable(reg))throw new AssertionError("Unexpected hardware write");writes++;ram.put(reg,p);if(dropWrite){a=s.timeout();continue;}r=reply(reg,0,new byte[0]);}
            a=s.receive(r);if(a==null)throw new AssertionError("Unexpected ignored response");if(duplicateAck&&mode==0x5a&&reg>=0x10&&s.receive(r)!=null)throw new AssertionError("Duplicate write acknowledgement was consumed as readback");
        }if(!a.complete)throw new AssertionError("Session did not terminate");return a;}
    }
    public static void main(String[] args){
        check(JbdProtocol.hex(JbdSettings.enter()).equals("DD 5A 00 02 56 78 FF 30 77"),"Published factory entry vector");
        check(JbdProtocol.hex(JbdSettings.exit(false)).equals("DD 5A 01 02 00 00 FF FD 77"),"Non-committing exit vector");
        check(JbdProtocol.hex(JbdSettings.exit(true)).equals("DD 5A 01 02 28 28 FF AD 77"),"Published commit vector");
        check(JbdProtocol.hex(JbdSettings.read(0x24)).equals("DD A5 24 00 FF DC 77"),"Parameter read checksum");
        check(JbdProtocol.hex(JbdSettings.write(JbdSettings.field("cell_over"),w(3650))).equals("DD 5A 24 02 0E 42 FF 8A 77"),"Independent 3.650V write vector");
        for(int reg:new int[]{7,9,0x38,0x39,0x2f,0xb0,0xd0,0xad,0xae,0xaf,0xe0,0xe1,0xe2,0xfb})check(!JbdSettings.writable(reg),"Forbidden write "+reg);
        invalid(()->JbdSettings.read(0xe1),"Control registers cannot be parameter reads");
        JbdSettings.Snapshot s=snapshot(values());check(s.layoutProblem().isEmpty(),"Classic layout matches live battery");
        near(JbdSettings.field("charge_current").value(w(20000)),200,"Charge limit scale");near(JbdSettings.field("discharge_current").value(w(45536)),200,"Negative discharge sign decoded as magnitude");
        check(JbdSettings.u16(s.change(JbdSettings.field("discharge_current"),"210.00"))==44536,"Discharge limit encodes negative current");
        check(JbdSettings.u16(s.change(JbdSettings.field("charge_cold"),"-5.0"))==2681,"Kelvin encoding below freezing");
        check(JbdSettings.u16(s.change(JbdSettings.field("design_capacity"),"300.00"))==30000,"Capacity in 10mAh steps");
        check(JbdSettings.u16(s.change(JbdSettings.field("balance_enabled"),"0"))==0xab08,"Unknown function bits preserved");
        invalid(()->s.change(JbdSettings.field("cell_over"),"3.500"),"Protection release ordering");
        invalid(()->s.change(JbdSettings.field("pack_under"),"60.00"),"Pack threshold ordering");
        invalid(()->s.change(JbdSettings.field("charge_cold"),"10.0"),"Temperature release ordering");
        invalid(()->s.change(JbdSettings.field("soc_80"),"3.900"),"SOC curve ordering");
        invalid(()->s.change(JbdSettings.field("balance_start"),"4.000"),"Balance within protection thresholds");
        invalid(()->s.change(JbdSettings.field("cell_over"),"3.6509"),"No silent precision rounding");
        invalid(()->s.change(JbdSettings.field("discharge_current"),"-10"),"Current magnitude is positive");
        invalid(()->s.change(JbdSettings.field("design_capacity"),"NaN"),"Non-finite input rejected");
        invalid(()->s.change(JbdSettings.field("design_capacity"),"1000"),"Classic capacity overflow rejected");
        Map<Integer,byte[]> wrong=values();wrong.put(0x2f,w(24));check(!snapshot(wrong).layoutProblem().isEmpty(),"Wrong cell layout locked");wrong=values();wrong.put(0x10,w(2800));check(!snapshot(wrong).layoutProblem().isEmpty(),"Wrong capacity scale locked");
        JbdSettings.Snapshot extended=new JbdSettings.Snapshot(s.address,s.model,s.firmware,16,280,true,s.timestamp,values());check(!extended.layoutProblem().isEmpty(),"Extended layout locked");
        byte[] copy=s.get(0x24);copy[0]=0;check(JbdSettings.u16(s.get(0x24))==3750,"Snapshot data is immutable to callers");
        JbdSettings.Assembler a=new JbdSettings.Assembler();byte[] frame=wire(0xaa,0,new byte[22]);check(a.feed(Arrays.copyOfRange(frame,0,20)).isEmpty(),"Counter fragmentation waits");check(a.feed(Arrays.copyOfRange(frame,20,frame.length)).get(0).payload.length==22,"Counter fragmentation completes");
        a.reset();byte[] corrupt=wire(0x24,0,w(3750));corrupt[4]^=1;check(a.feed(corrupt).isEmpty(),"Corrupt parameter frame rejected");check(a.feed(wire(0x24,0x80,new byte[0])).get(0).status==0x80,"Error checksum includes status");check(a.feed(wire(0xe1,0,w(0))).isEmpty(),"Unknown settings frame ignored");
        Bms b=new Bms();JbdSettingsSession.Action result=b.run(new JbdSettingsSession(s,""));check(result.success&&b.writes==0&&b.commits==0&&b.normalExits==1,"Read session never commits or resets counters");check(result.snapshot.get(0xaa)[1]==7,"Read preserves protection counters");
        b=new Bms();result=b.run(new JbdSettingsSession(s,"123456",s,JbdSettings.field("cell_over"),"3.650"));check(result.success&&b.writes==1&&b.commits==1&&b.enters==2,"One acknowledged write and post-commit reentry");check(JbdSettings.u16(b.eeprom.get(0x24))==3650&&JbdSettings.u16(result.snapshot.get(0x24))==3650,"Persistent value read back");check(b.before.get(0xaa)[1]==7&&result.snapshot.get(0xaa)[1]==0,"Original counters preserved before commit");check(JbdSettings.u16(b.before.get(0x24))==3750,"Original value preserved before write");
        b=new Bms();b.eeprom.put(0x24,w(3700));result=b.run(new JbdSettingsSession(s,"",s,JbdSettings.field("cell_over"),"3.650"));check(!result.success&&b.writes==0&&b.commits==0,"Stale reviewed snapshot blocks writes");
        b=new Bms();b.backup=false;result=b.run(new JbdSettingsSession(s,"",s,JbdSettings.field("cell_over"),"3.650"));check(!result.success&&b.writes==0&&b.commits==0,"Failed durable backup blocks writes");
        b=new Bms();b.badReadback=true;result=b.run(new JbdSettingsSession(s,"",s,JbdSettings.field("cell_over"),"3.650"));check(!result.success&&b.writes==1&&b.commits==0,"Readback mismatch prevents commit");
        b=new Bms();b.refuseCommit=true;result=b.run(new JbdSettingsSession(s,"",s,JbdSettings.field("cell_over"),"3.650"));check(!result.success&&b.writes==1&&b.commits==1&&b.normalExits==1,"Refused commit reported with cleanup");
        b=new Bms();b.dropWrite=true;result=b.run(new JbdSettingsSession(s,"",s,JbdSettings.field("cell_over"),"3.650"));check(!result.success&&b.writes==1&&b.commits==0&&result.message.contains("may have taken effect"),"Lost write reply never retries or claims rollback");
        b=new Bms();b.badPersist=true;result=b.run(new JbdSettingsSession(s,"",s,JbdSettings.field("cell_over"),"3.650"));check(!result.success&&b.commits==1&&result.message.contains("may have taken effect"),"Post-commit mismatch cannot be reported as success or rollback");
        b=new Bms();b.duplicateAck=true;result=b.run(new JbdSettingsSession(s,"",s,JbdSettings.field("cell_over"),"3.650"));check(result.success&&b.writes==1,"Duplicate write ack does not masquerade as readback");
        JbdSettings.Snapshot stale=new JbdSettings.Snapshot(s.address,s.model,s.firmware,s.cells,s.telemetryCapacity,false,System.currentTimeMillis()-360000,values());b=new Bms();result=b.run(new JbdSettingsSession(s,"",stale,JbdSettings.field("cell_over"),"3.650"));check(!result.success&&b.writes==0,"Expired review blocks hardware writes");
        JbdSettings.Snapshot other=new JbdSettings.Snapshot("AA:BB:CC:DD:EE:02",s.model,s.firmware,s.cells,s.telemetryCapacity,false,s.timestamp,values());invalid(()->new JbdSettingsSession(other,"",s,JbdSettings.field("cell_over"),"3.650"),"Different battery cannot use reviewed settings");
        b=new Bms();b.eeprom.remove(0x35);result=b.run(new JbdSettingsSession(s,""));check(result.success&&result.snapshot.get(0x35)==null,"Unsupported optional parameter remains unavailable");
        invalid(()->resultSnapshotMissingCurve().change(JbdSettings.field("soc_80"),"3.340"),"Partial SOC curve cannot be edited");
        JbdSettingsSession timed=new JbdSettingsSession(s,"");timed.start();JbdSettingsSession.Action cleanup=timed.timeout();check(JbdProtocol.hex(cleanup.packet).equals(JbdProtocol.hex(JbdSettings.exit(false))),"Unacknowledged entry attempts safe exit");check(timed.timeout().reconnect,"Missing exit acknowledgement requires reconnection");
        JbdSettingsSession login=new JbdSettingsSession(s,"123456");login.start();result=login.receive(reply(6,0x80,new byte[0]));check(result.complete&&!result.success&&result.packet==null,"Rejected password does not enter factory mode");
        System.out.println("PASS: "+passed+" JBD parameter, transaction and failure-path checks");
    }
    static JbdSettings.Snapshot resultSnapshotMissingCurve(){Map<Integer,byte[]> p=values();p.remove(0x35);return snapshot(p);}
}
