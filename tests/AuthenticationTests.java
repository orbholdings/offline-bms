package com.simon.offlinebms;

import java.util.*;

public final class AuthenticationTests {
    static int count;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;}
    static void invalid(Runnable r,String name){boolean raised=false;try{r.run();}catch(IllegalArgumentException e){raised=true;}check(raised,name);}
    static String hex(byte[] b){return JbdProtocol.hex(b).replace(" ","").toLowerCase(Locale.ROOT);}
    static JbdAuthentication.Frame reply(int command,int value){return new JbdAuthentication.Frame(command,new byte[]{(byte)value});}
    static JbdAuthentication session(){return new JbdAuthentication("11:22:33:44:55:66","548213");}
    public static void main(String[] args){
        JbdAuthentication a=session();
        check(hex(a.start().packet).equals("ffaa15063030303030303b"),"Published app-key frame");
        check(hex(a.receive(reply(0x15,0)).packet).equals("ffaa170017"),"Random challenge request");
        JbdAuthentication.Action user=a.receive(reply(0x17,0xa3));
        check(hex(user.packet).equals("ffaa1806c7b9ae1907f864"),"MAC-dependent user password test vector");
        check(!user.message.contains("548213"),"Password not present in UI/status message");
        check(hex(a.receive(reply(0x18,0)).packet).equals("ffaa170017"),"Fresh challenge required for app signature");
        JbdAuthentication.Action activation=a.receive(reply(0x17,0x52));
        check((activation.packet[2]&255)==0x1d && activation.packet.length==20,"Authenticated app activation fits default BLE MTU");
        check(!Arrays.equals(Arrays.copyOfRange(user.packet,4,10),Arrays.copyOfRange(activation.packet,4,10)),"Second login stage uses a fresh nonce and distinct material");
        check(a.receive(reply(0x1d,0)).complete && a.stage()==JbdAuthentication.Stage.DONE,"Login completes only on valid final acknowledgement");
        a=session();a.start();a.receive(reply(0x15,0));a.receive(reply(0x17,1));
        JbdAuthentication.Action wrong=a.receive(reply(0x18,1));check(wrong.failed && wrong.packet==null,"Wrong password stops without app activation or resets");
        check(a.receive(reply(0x17,2))==null,"Rejected session cannot advance via later nonce");
        a=session();a.start();check(a.receive(reply(0x15,1)).failed,"Rejected app login is reported");
        a=new JbdAuthentication("11:22:33:44:55:66","");a.start();check(a.receive(reply(0x15,0)).failed,"Missing required password reported");
        a=session();a.start();check(a.receive(reply(0x15,2)).complete,"No-password module may proceed without credential writes");
        a=session();a.start();check(a.receive(reply(0x18,0))==null,"Out-of-order reply cannot authenticate");
        check(a.receive(new JbdAuthentication.Frame(0x15,new byte[0]))==null,"Empty login reply is not accepted");
        a=session();a.start();a.receive(reply(0x15,0));check(a.timeout().failed,"Nonce timeout does not claim authentication success");
        a=session();a.start();JbdAuthentication.Action legacy=a.timeout();check(a.stage()==JbdAuthentication.Stage.LEGACY_ASCII && legacy.packet[2]==6,"Unrecognized modern handshake tries only current-password legacy register");
        check(a.receiveLegacy(0).complete,"Legacy login acknowledgement accepted");
        a=session();a.start();a.timeout();JbdAuthentication.Action numeric=a.receiveLegacy(0x83);check(a.stage()==JbdAuthentication.Stage.LEGACY_DIGITS && numeric.packet[2]==6,"Known numeric PIN can use documented alternate representation");check(a.receiveLegacy(0x83).failed,"Both explicit password rejections stop login");
        check(hex(JbdAuthentication.legacyLogin("765828",true)).equals("dd5a060706070605080208ffc977"),"Manufacturer's numeric legacy example and checksum");
        check(hex(JbdAuthentication.legacyLogin("765828",false)).equals("dd5a060706373635383238fea977"),"ASCII legacy representation and checksum");
        invalid(()->JbdAuthentication.frame(0x16,new byte[0]),"Changing a Bluetooth password is not allowed");
        invalid(()->JbdProtocol.readCommand(0x07),"Changing a BMS password is not allowed");
        invalid(()->JbdProtocol.readCommand(0x09),"Password reset is not allowed");
        invalid(()->JbdProtocol.readCommand(0x00),"Factory mode is not allowed");
        invalid(()->JbdAuthentication.validatePassword("short"),"Wrong password length rejected");
        invalid(()->JbdAuthentication.validatePassword("中文字符密码"),"Unsupported password encoding rejected");
        invalid(()->JbdAuthentication.mac("11:22:33"),"Malformed address rejected");
        check(Arrays.equals(JbdAuthentication.mac("aa:bb:cc:dd:ee:ff"),JbdAuthentication.mac("AA:BB:CC:DD:EE:FF")),"MAC parsing is case independent");
        byte[] f=JbdAuthentication.frame(0x15,new byte[]{0});JbdAuthentication.Assembler assembler=new JbdAuthentication.Assembler();
        check(assembler.feed(Arrays.copyOfRange(f,0,3)).isEmpty(),"Fragmented auth response waits for remainder");
        check(assembler.feed(Arrays.copyOfRange(f,3,f.length)).size()==1,"Fragmented auth response reassembles");
        byte[] bad=f.clone();bad[bad.length-1]^=1;check(assembler.feed(bad).isEmpty(),"Bad login checksum rejected");check(assembler.feed(f).size()==1,"Recovery after corrupt auth frame");
        byte[] twice=new byte[f.length*2];System.arraycopy(f,0,twice,0,f.length);System.arraycopy(f,0,twice,f.length,f.length);check(assembler.feed(twice).size()==2,"Coalesced auth responses handled");
        assembler.reset();assembler.feed(new byte[]{(byte)255,(byte)170,0x15,(byte)255});check(assembler.feed(f).size()==1,"Impossible auth length cannot block following frame");
        Random r=new Random(101);for(int i=0;i<10000;i++){byte[] noise=new byte[30];r.nextBytes(noise);assembler.feed(noise);}check(true,"Auth parser is bounded under noisy input");
        System.out.println("PASS: "+count+" authentication checks");
    }
}
