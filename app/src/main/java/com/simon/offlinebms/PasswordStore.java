package com.simon.offlinebms;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Optional per-battery credentials, encrypted with a non-exportable Android Keystore key. */
public final class PasswordStore {
    private static final String KEY="offline_bms_battery_credentials_v1";
    private final SharedPreferences prefs;
    public PasswordStore(Context c){prefs=c.getSharedPreferences("encrypted_battery_passwords",Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY))return (SecretKey)store.getKey(KEY,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return gen.generateKey();
    }
    public void put(String address,String password)throws Exception {
        if(password.isEmpty()){remove(address);return;}
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());c.updateAAD(address.getBytes(StandardCharsets.US_ASCII));
        byte[] value=c.doFinal(password.getBytes(StandardCharsets.US_ASCII));
        prefs.edit().putString(address,Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(value,Base64.NO_WRAP)).apply();
    }
    public String get(String address)throws Exception {
        String value=prefs.getString(address,"");if(value.isEmpty())return "";
        String[] parts=value.split(":",2);if(parts.length!=2){remove(address);return "";}
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));c.updateAAD(address.getBytes(StandardCharsets.US_ASCII));
        return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.US_ASCII);
    }
    public void remove(String address){prefs.edit().remove(address).apply();}
    public void clear(){prefs.edit().clear().apply();}
}
