package com.simon.offlinebms;
import java.nio.file.*;
import java.util.*;
public final class VictronFormatsTests {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    private static void close(double a,double b,String name){check(Math.abs(a-b)<.000001,name+": "+a+" vs "+b);}
    interface Work{void run()throws Exception;}
    private static void rejects(Work work,String name)throws Exception{boolean yes=false;try{work.run();}catch(IllegalArgumentException e){yes=true;}check(yes,name);}
    private static byte[] hex(String s){s=s.trim();byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    public static void main(String[] args)throws Exception{
        byte[] lithium=hex("785634124000c62312e0ff2795522a41"),protect=hex("000100400080002c05280502000000"),orion=hex("0300a0052c012805540101000000");
        VictronProtocol.Data a=VictronProtocol.parse(lithium,5);check(a.typeName().equals("SmartLithium"),"SmartLithium named");close(a.values.get("Cell 1 voltage"),3.30,"7-bit first cell");close(a.values.get("Cell 8 voltage"),3.34,"7-bit eighth cell crosses byte boundary");
        check(a.details.get("Cell 4").equals("Below 2.61 V")&&!a.values.containsKey("Cell 4 voltage"),"Low clipped cell does not invent an exact value");check(a.details.get("Cell 5").equals("Above 3.85 V")&&!a.values.containsKey("Cell 5 voltage"),"High clipped cell does not invent an exact value");check(!a.values.containsKey("Cell 6 voltage"),"Unavailable cell omitted");close(a.values.get("Battery voltage"),26.42,"12-bit lithium pack voltage");close(a.values.get("Temperature"),25,"Lithium temperature bias");check(a.details.get("Balancer").equals("Balancing"),"Balancer state");check(a.details.get("BMS status flags").equals("0x12345678")&&a.alarms==0x40,"Status and error flags remain distinct");
        VictronProtocol.Data b=VictronProtocol.parse(protect,9);close(b.values.get("Input voltage"),13.24,"Protect signed input voltage");close(b.values.get("Output voltage"),13.20,"Protect output voltage");check(b.details.get("Alarm flags").equals("0x40")&&b.details.get("Warning flags").equals("0x80"),"Protect warning and alarm identities preserved");check(b.details.get("Off reason").equals("0x2")&&b.alarms!=0,"Protect off reason and aggregate warning");
        VictronProtocol.Data c=VictronProtocol.parse(orion,15);check(c.state==3&&c.alarmsKnown&&c.alarms==0,"Orion state and error");close(c.values.get("Output voltage"),14.4,"Orion output voltage");close(c.values.get("Output current"),30,"Orion output current");close(c.values.get("Input voltage"),13.2,"Orion input voltage");close(c.values.get("Input current"),34,"Orion input current");close(c.values.get("Output power"),432,"Orion derived output watts");close(c.values.get("Input power"),448.8,"Orion derived input watts");check(c.details.get("Off reason").equals("0x1"),"Orion off reason");
        byte[] discharge=orion.clone();discharge[4]=(byte)0xe8;discharge[5]=(byte)0xfd;close(VictronProtocol.parse(discharge,15).values.get("Output current"),-53.6,"Orion signed negative output current");
        int[] types={5,9,15};byte[][] payloads={lithium,protect,orion};VictronProtocol.Data[] expected={a,b,c};
        for(int i=0;i<types.length;i++){
            final int type=types[i];final byte[] payload=payloads[i];byte[] packet=hex(Files.readString(Path.of(args[0],String.format("victron-type-%02x.hex",type))));
            VictronProtocol.Data decoded=VictronProtocol.decode(packet,VictronProtocol.key("000102030405060708090a0b0c0d0e0f"));check(decoded.values.equals(expected[i].values)&&decoded.details.equals(expected[i].details),"Independent encrypted type "+type);
            for(int n=0;n<payload.length;n++){final int len=n;rejects(()->VictronProtocol.parse(Arrays.copyOf(payload,len),type),"Short type "+type+" length "+len);}
            check(VictronProtocol.parse(Arrays.copyOf(payload,24),type).values.equals(expected[i].values),"Trailing extension accepted for "+type);
        }
        byte[] missing=new byte[16];Arrays.fill(missing,(byte)0xff);check(VictronProtocol.parse(missing,5).values.isEmpty(),"Lithium missing values omitted");missing[3]=0x7f;missing[5]=0x7f;check(VictronProtocol.parse(missing,15).values.isEmpty(),"Orion unavailable inputs and outputs omitted");
        for(int type:new int[]{1,2,3,4,5,6,8,9,10,11,12,13,15})check(!VictronProtocol.parse(new byte[16],type).typeName().startsWith("Unsupported"),"Supported type "+type);
        rejects(()->VictronProtocol.parse(new byte[16],7),"Provisional GX format stays explicit");rejects(()->VictronProtocol.parse(new byte[16],14),"Unassigned record rejected");
        System.out.println("PASS: "+checks+" extended Victron format checks");
    }
}
