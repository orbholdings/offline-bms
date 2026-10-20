package com.simon.offlinebms;

import java.nio.file.*;
import java.util.*;

/** Independent packed vectors from Victron's AC Charger bit-field specification. */
public final class VictronChargerTests {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void close(double actual,double expected,String message){check(Math.abs(actual-expected)<.000001,message+": "+actual+" vs "+expected);}
    private interface Work{void run()throws Exception;}
    private static void rejects(Work work,String message)throws Exception{boolean rejected=false;try{work.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,message);}
    private static byte[] hex(String s){s=s.trim();byte[] out=new byte[s.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return out;}
    public static void main(String[] args)throws Exception{
        byte[] plain=hex("0300a0852528a505ffffffc10d");
        VictronProtocol.Data d=VictronProtocol.parse(plain,8);
        check(d.typeName().equals("AC charger"),"AC charger record label");
        check(d.state==3&&d.alarmsKnown&&d.alarms==0,"Bulk state and known zero charger error");
        close(d.values.get("Output 1 voltage"),14.4,"13-bit output 1 voltage");
        close(d.values.get("Output 1 current"),30,"Unaligned 11-bit output 1 current");
        close(d.values.get("Output 1 power"),432,"Output 1 DC power");
        close(d.values.get("Output 2 voltage"),13.2,"Independent output 2 voltage");
        close(d.values.get("Output 2 current"),4.5,"Independent output 2 current");
        close(d.values.get("Output 2 power"),59.4,"Independent output 2 DC power");
        check(!d.values.containsKey("Output 3 voltage")&&!d.values.containsKey("Output 3 current")&&!d.values.containsKey("Output 3 power"),"Unavailable third output omitted");
        close(d.values.get("Temperature"),25,"7-bit temperature bias");
        close(d.values.get("AC input current"),2.7,"9-bit AC current crosses a byte boundary");
        check(d.values.size()==8,"No missing measurements invented");
        byte[] packet=hex(Files.readString(Path.of(args[0],"victron-ac-charger.hex")));
        VictronProtocol.Data decrypted=VictronProtocol.decode(packet,VictronProtocol.key("000102030405060708090a0b0c0d0e0f"));
        check(decrypted.type==8&&decrypted.model==0xa343&&decrypted.counter==0x5678,"Encrypted AC record header");
        check(decrypted.values.equals(d.values)&&decrypted.units.equals(d.units),"Independent AES-CTR reference fixture matches all measurements");
        rejects(()->VictronProtocol.decode(packet,VictronProtocol.key("ff0102030405060708090a0b0c0d0e0f")),"Wrong readout key marker rejected");
        for(int length=0;length<13;length++){final int n=length;rejects(()->VictronProtocol.parse(Arrays.copyOf(plain,n),8),"Truncated charger record rejected at "+n+" bytes");}
        byte[] extended=Arrays.copyOf(plain,20);Arrays.fill(extended,13,20,(byte)0xff);
        check(VictronProtocol.parse(extended,8).values.equals(d.values),"Future trailing record extensions accepted");
        byte[] missing=new byte[13];Arrays.fill(missing,(byte)0xff);VictronProtocol.Data unavailable=VictronProtocol.parse(missing,8);
        check(unavailable.values.isEmpty(),"All AC unavailable sentinels omitted");
        check(unavailable.state==-1&&!unavailable.alarmsKnown,"Unknown state and charger error stay unknown");
        VictronProtocol.Data max=VictronProtocol.parse(hex("0506fedffffedffffedfff7eff"),8);
        for(int output=1;output<=3;output++){close(max.values.get("Output "+output+" voltage"),81.9,"Maximum output "+output+" voltage");close(max.values.get("Output "+output+" current"),204.6,"Maximum output "+output+" current");}
        close(max.values.get("Temperature"),86,"Maximum temperature");close(max.values.get("AC input current"),51,"Maximum AC current");
        check(max.state==5&&max.alarmsKnown&&max.alarms==6,"Nonzero charger error preserved");
        VictronProtocol.Data zero=VictronProtocol.parse(new byte[13],8);close(zero.values.get("Temperature"),-40,"Minimum temperature");close(zero.values.get("AC input current"),0,"Zero AC current is valid");close(zero.values.get("Output 1 voltage"),0,"Zero voltage is valid");
        String canonical="AA:BB:CC:DD:EE:FF";
        for(String address:new String[]{"aabbccddeeff","AA:BB:CC:DD:EE:FF","aa-bb-cc-dd-ee-ff","  aabbccddeeff\n"})check(VictronProtocol.normalizeAddress(address).equals(canonical),"Address normalized: "+address.trim());
        for(String address:new String[]{"","1234","aabbccddeefg","aa:bb-cc:dd:ee:ff","aa:bb:cc:dd:ee","aa:bb:cc:dd:ee:ff:00","xxaabbccddeeff","aa bb cc dd ee ff"})rejects(()->VictronProtocol.normalizeAddress(address),"Malformed address rejected");
        rejects(()->VictronProtocol.normalizeAddress(null),"Missing address rejected");
        System.out.println("PASS: "+checks+" Victron AC charger and address checks");
    }
}
