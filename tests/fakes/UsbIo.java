package io.oniimai.kanade;
import java.io.IOException;
import java.util.function.Consumer;

/** JVM-only fake. This file is never compiled into the Android app. */
public final class UsbIo {
    public interface Bytes {void accept(byte[] data,int length);}
    public interface Failure {void accept(String message);}
    public static final class Port {}
    public static volatile Port replacement;
    public static Port restore(android.hardware.usb.UsbManager manager,Port selected){return replacement==null?selected:replacement;}
    public static final class Hid {
        public volatile int rgb=-1,writes;
        public volatile boolean fail;
        public void writeCeiling(int color)throws IOException {if(fail)throw new IOException("test output failure");rgb=color;writes++;}
    }
    public static final class Cdc {
        public static volatile Consumer<Cdc> opened;
        public Cdc(){selected=null;}
        public final Port selected;
        public Cdc(android.hardware.usb.UsbManager manager,Port port,int baud){selected=port;if(opened!=null)opened.accept(this);}
        public Cdc(android.hardware.usb.UsbManager manager,Port port,int baud,int lineState){this(manager,port,baud);if(lineState!=1)throw new AssertionError("NFC must use DTR only");}
        public Consumer<byte[]> onWrite;public Bytes incoming;public Failure failure;public boolean closed;
        public void start(Bytes bytes,Failure fail){incoming=bytes;failure=fail;}
        public void write(byte[] bytes) throws IOException {if(closed)throw new IOException("closed");if(onWrite!=null)onWrite.accept(bytes);}
        public void receive(byte[] bytes){incoming.accept(bytes,bytes.length);}
        public void close(){closed=true;}
    }
}
