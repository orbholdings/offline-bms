package com.simon.offlinebms;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original implementation of the documented JBD login, separate from BMS controls. */
public final class JbdAuthentication {
    public enum Stage { APP_KEY, USER_RANDOM, USER_PASSWORD, APP_RANDOM, APP_SIGNATURE, LEGACY_ASCII, LEGACY_DIGITS, DONE, FAILED }
    public static final class Action {
        public final byte[] packet;
        public final String message;
        public final boolean complete,failed;
        private Action(byte[] p,String m,boolean done,boolean error){packet=p;message=m;complete=done;failed=error;}
    }
    public static final class Frame {
        public final int command;
        public final byte[] data;
        public Frame(int cmd,byte[] data){this.command=cmd;this.data=data.clone();}
    }
    public static final class Assembler {
        private final byte[] bytes=new byte[128];
        private int count;
        public int rejected;
        public void reset(){count=0;rejected=0;}
        private void remove(int n){System.arraycopy(bytes,n,bytes,0,count-n);count-=n;}
        public List<Frame> feed(byte[] chunk){
            List<Frame> out=new ArrayList<>();
            if(chunk==null)return out;
            for(byte b:chunk){
                if(count==bytes.length){count=0;rejected++;}
                bytes[count++]=b;
                while(count>0){
                    if((bytes[0]&255)!=255){remove(1);continue;}
                    if(count<2)break;
                    if((bytes[1]&255)!=170){remove(1);continue;}
                    if(count<4)break;
                    int command=bytes[2]&255,n=bytes[3]&255;
                    if(n>64 || (command!=0x15&&command!=0x17&&command!=0x18&&command!=0x1d)){rejected++;remove(1);continue;}
                    if(count<n+5)break;
                    int sum=0;for(int i=2;i<4+n;i++)sum+=(bytes[i]&255);
                    if((sum&255)!=(bytes[4+n]&255)){rejected++;remove(1);continue;}
                    out.add(new Frame(command,Arrays.copyOfRange(bytes,4,4+n)));remove(n+5);
                }
            }
            return out;
        }
    }
    private static final byte[] APP_SIGNATURE={0x4a,0x42,0x44,0x62,0x74,0x70,0x77,0x64,0x21,0x40,0x23,0x32,0x30,0x32,0x33};
    private final byte[] address;
    private final String password;
    private Stage stage=Stage.APP_KEY;
    private boolean userAccepted;
    public JbdAuthentication(String address,String password){this.address=mac(address);this.password=password==null?"":password;validatePassword(this.password);}
    public static void validatePassword(String password){
        if(password==null || password.isEmpty())return;
        if(password.length()!=6)throw new IllegalArgumentException("Enter the six-character battery password, or leave it empty.");
        for(char c:password.toCharArray())if(c<32||c>126)throw new IllegalArgumentException("The battery password must contain six ASCII characters.");
    }
    public static byte[] mac(String value){
        if(value==null || !value.matches("(?i)[0-9a-f]{2}(?::[0-9a-f]{2}){5}"))throw new IllegalArgumentException("Invalid Bluetooth address");
        byte[] out=new byte[6];String[] parts=value.split(":");for(int i=0;i<6;i++)out[i]=(byte)Integer.parseInt(parts[i],16);return out;
    }
    public static byte[] frame(int command,byte[] payload){
        if(command!=0x15&&command!=0x17&&command!=0x18&&command!=0x1d)throw new IllegalArgumentException("Only login commands are allowed");
        if(payload.length>15)throw new IllegalArgumentException("Login payload is too long");
        byte[] p=new byte[payload.length+5];p[0]=(byte)255;p[1]=(byte)170;p[2]=(byte)command;p[3]=(byte)payload.length;System.arraycopy(payload,0,p,4,payload.length);
        int sum=command+payload.length;for(byte v:payload)sum+=v&255;p[p.length-1]=(byte)sum;return p;
    }
    public static byte[] transform(byte[] value,byte[] address,int nonce){
        if(address.length!=6)throw new IllegalArgumentException("Invalid Bluetooth address bytes");
        byte[] out=new byte[value.length];
        for(int i=0;i<value.length;i++)out[i]=(byte)(((value[i]&255)^(i<6?(address[i]&255):0))+nonce);
        return out;
    }
    public static byte[] legacyLogin(String password,boolean digits){
        validatePassword(password);if(password==null||password.isEmpty())throw new IllegalArgumentException("A password is required");
        if(digits&&!password.matches("[0-9]{6}"))throw new IllegalArgumentException("Numeric password representation requires six digits");
        byte[] p=new byte[14];p[0]=(byte)0xdd;p[1]=0x5a;p[2]=6;p[3]=7;p[4]=6;
        int sum=6+7+6;
        for(int i=0;i<6;i++){p[5+i]=(byte)(digits?password.charAt(i)-'0':password.charAt(i));sum+=p[5+i]&255;}
        int checksum=(-sum)&65535;p[11]=(byte)(checksum>>8);p[12]=(byte)checksum;p[13]=0x77;return p;
    }
    public Stage stage(){return stage;}
    public boolean isLegacy(){return stage==Stage.LEGACY_ASCII||stage==Stage.LEGACY_DIGITS;}
    public int expectedCommand(){switch(stage){case APP_KEY:return 0x15;case USER_RANDOM:case APP_RANDOM:return 0x17;case USER_PASSWORD:return 0x18;case APP_SIGNATURE:return 0x1d;default:return -1;}}
    public Action start(){stage=Stage.APP_KEY;userAccepted=false;return send(frame(0x15,"000000".getBytes(StandardCharsets.US_ASCII)),"Checking Bluetooth password support…");}
    private Action send(byte[] packet,String message){return new Action(packet,message,false,false);}
    private Action done(String message){stage=Stage.DONE;return new Action(null,message,true,false);}
    private Action fail(String message){stage=Stage.FAILED;return new Action(null,message,false,true);}
    private Action random(boolean app){stage=app?Stage.APP_RANDOM:Stage.USER_RANDOM;return send(frame(0x17,new byte[0]),app?"Password accepted · finishing Bluetooth login…":"Authenticating battery password…");}
    private Action legacy(boolean numeric){stage=numeric?Stage.LEGACY_DIGITS:Stage.LEGACY_ASCII;return send(legacyLogin(password,numeric),"Trying the legacy battery password format…");}
    public Action receive(Frame response){
        if(response.command!=expectedCommand()||response.data.length<1)return null;
        int value=response.data[0]&255;
        switch(stage){
            case APP_KEY:
                if(value==2)return done("Bluetooth module requires no password · reading battery…");
                if(value==0){if(password.isEmpty())return fail("This battery requires a password. Open Connect and enter its six-character password.");return random(false);}
                return fail("The Bluetooth module rejected the app login. Export connection diagnostics so its protocol can be checked.");
            case USER_RANDOM:
                stage=Stage.USER_PASSWORD;return send(frame(0x18,transform(password.getBytes(StandardCharsets.US_ASCII),address,value)),"Checking battery password…");
            case USER_PASSWORD:
                if(value!=0)return fail("The battery rejected that password. Check the password on the Connect screen.");
                userAccepted=true;return random(true);
            case APP_RANDOM:
                if(!userAccepted)return fail("Battery password was not accepted.");
                stage=Stage.APP_SIGNATURE;return send(frame(0x1d,transform(APP_SIGNATURE,address,value)),"Password accepted · finishing Bluetooth login…");
            case APP_SIGNATURE:
                if(value==0)return done("Password accepted · reading battery…");
                return fail("The battery accepted the password but rejected the final Bluetooth login step. Export connection diagnostics.");
            default:return null;
        }
    }
    public Action receiveLegacy(int status){
        if(!isLegacy())return null;
        if(status==0)return done("Password accepted · reading battery…");
        if(stage==Stage.LEGACY_ASCII&&password.matches("[0-9]{6}"))return legacy(true);
        return fail("The battery rejected the password in its supported legacy formats. Check it on the Connect screen.");
    }
    public Action timeout(){
        switch(stage){
            case APP_KEY:return password.isEmpty()?done("Classic Bluetooth module · reading battery…"):legacy(false);
            case LEGACY_ASCII:if(password.matches("[0-9]{6}"))return legacy(true);return done("Legacy login was not acknowledged · checking battery readings…");
            case LEGACY_DIGITS:return done("Legacy login was not acknowledged · checking battery readings…");
            default:return fail("Bluetooth connected, but the battery did not answer the login step ("+stage.name()+"). Export connection diagnostics.");
        }
    }
}
