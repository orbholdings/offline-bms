package com.simon.offlinebms;
import java.io.File;
import java.lang.reflect.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ArchiveParseTest {
    @Test public void parsesDeliveredArchive()throws Exception{if(System.getProperty("bms.currentApk")!=null)parse(System.getProperty("bms.currentApk"));else org.junit.Assume.assumeTrue("Set bms.currentApk to a signed release",false);}
    @Test public void parsesKnownWorkingArchive()throws Exception{if(System.getProperty("bms.previousApk")!=null)parse(System.getProperty("bms.previousApk"));else org.junit.Assume.assumeTrue("Set bms.previousApk to the working release",false);}
    private void parse(String path)throws Exception{
        Class<?> parserClass=Class.forName("android.content.pm.PackageParser");Object parser=parserClass.getConstructor().newInstance();
        Object pkg;
        try{pkg=parserClass.getMethod("parsePackage",File.class,int.class).invoke(parser,new File(path),0);}catch(InvocationTargetException e){throw new AssertionError("Android package parse failed: "+path,e.getCause());}
        assertNotNull(pkg);assertEquals("com.simon.offlinebms",pkg.getClass().getField("packageName").get(pkg));
        Object appInfo=pkg.getClass().getField("applicationInfo").get(pkg);
        assertEquals(31,appInfo.getClass().getField("minSdkVersion").getInt(appInfo));assertEquals(35,appInfo.getClass().getField("targetSdkVersion").getInt(appInfo));
        Method collect=parserClass.getMethod("collectCertificates",pkg.getClass(),boolean.class);
        // Certificate extraction only here: SDK apksigner verifies cryptographic digests separately.
        // Android's mmap verifier relies on an Android-only public DirectByteBuffer constructor.
        try{collect.invoke(null,pkg,true);}catch(InvocationTargetException e){throw new AssertionError("Android certificate extraction failed: "+path,e.getCause());}
        Object signing=pkg.getClass().getField("mSigningDetails").get(pkg);assertNotNull(signing);assertEquals(1,((Object[])signing.getClass().getField("signatures").get(signing)).length);
    }
}
