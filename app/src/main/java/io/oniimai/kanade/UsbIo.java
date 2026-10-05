package io.oniimai.kanade;

import android.hardware.usb.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** CDC-ACM implementation for the STM32 composite device, with independent handles per interface. */
public final class UsbIo {
    public interface Bytes {void accept(byte[] data,int length);}
    public interface Failure {void accept(String message);}
    public static final class Port {
        public final UsbDevice device; public final int controlId;public final boolean hid;
        public String name;
        private final String identity;
        Port(UsbDevice d,UsbInterface i,String name) {
            device=d;controlId=i.getId();hid=i.getInterfaceClass()==3;this.name=name;
            String serial="";try {serial=d.getSerialNumber();}catch(SecurityException ignored) {}
            identity=d.getVendorId()+":"+d.getProductId()+":"+controlId+":"+(serial==null?"":serial);
        }
        /** Current USB address, useful only while this enumeration is connected. */
        public String key() {return device.getDeviceName()+"#"+controlId;}
        /** VID/PID/interface/serial identity remains valid across bus-address changes. */
        public String stableId() {return identity;}
    }
    public static List<Port> ports(UsbManager manager) {
        List<Port> result=new ArrayList<>();
        for(UsbDevice d:manager.getDeviceList().values()) {
            UsbDeviceConnection conn=manager.hasPermission(d)?manager.openDevice(d):null;
            try {
                Map<Integer,String> names=conn==null?new HashMap<>():names(conn);
                for(int n=0;n<d.getInterfaceCount();n++) {
                    UsbInterface i=d.getInterface(n);
                    if(i.getAlternateSetting()!=0) continue;
                    if((i.getInterfaceClass()==2 && i.getInterfaceSubclass()==2) || i.getInterfaceClass()==3) {
                        String name=names.get(i.getId());
                        if(name==null || name.isEmpty()) name=i.getName();
                        if(name==null || name.isEmpty()) name=(i.getInterfaceClass()==3?"HID":"CDC-ACM")+" IF"+i.getId();
                        result.add(new Port(d,i,name));
                    }
                }
            } finally {if(conn!=null)conn.close();}
        }
        result.sort(Comparator.comparing(Port::key));return result;
    }
    /** Resolve a new device handle after unplug/replug; ambiguous identical devices require selection. */
    public static Port restore(UsbManager manager,Port selected) throws IOException {
        List<Port> available=ports(manager);String[] identities=new String[available.size()];
        for(int i=0;i<identities.length;i++)identities[i]=available.get(i).stableId();
        int found=PortSelection.unique(identities,selected.stableId());
        if(found==-2)throw new IOException(I18n.t(Msg.USB_DUPLICATE));
        if(found<0)throw new IOException(I18n.t(Msg.USB_WAITING_SAVED,selected.name));
        return available.get(found);
    }
    private static Map<Integer,String> names(UsbDeviceConnection conn) {
        Map<Integer,String> result=new HashMap<>();byte[] raw=conn.getRawDescriptors();
        if(raw==null)return result;
        byte[] languages=new byte[255];int count=conn.controlTransfer(0x80,6,0x300,0,languages,languages.length,300);
        int language=count>=4?Protocol.u16(languages,2):0x0409;
        for(int p=0;p+2<=raw.length;) {
            int len=Protocol.u(raw[p]),type=Protocol.u(raw[p+1]);if(len<2 || p+len>raw.length)break;
            if((type==4 && len>=9) || (type==11 && len>=8)) {
                int id=Protocol.u(raw[p+2]),index=Protocol.u(raw[p+(type==4?8:7)]);
                if(index>0) {
                    byte[] str=new byte[255];int n=conn.controlTransfer(0x80,6,0x300|index,language,str,str.length,300);
                    if(n>=2) {int end=Math.min(n,Protocol.u(str[0]));if(end>=2)result.put(id,new String(str,2,(end-2)&~1,StandardCharsets.UTF_16LE));}
                }
            }
            p+=len;
        }
        return result;
    }
    private static UsbInterface byId(UsbDevice d,int id) throws IOException {
        for(int n=0;n<d.getInterfaceCount();n++)if(d.getInterface(n).getId()==id && d.getInterface(n).getAlternateSetting()==0)return d.getInterface(n);
        throw new IOException(I18n.t(Msg.USB_NO_INTERFACE,id));
    }
    /**
     * Classifies CDC bulk reads. Android returns a negative count both for an idle timeout and for a
     * transfer error, so the time the call took tells them apart: an idle read waits roughly the whole
     * timeout, an error returns at once. Fast failures back off briefly, and only an unbroken run of them
     * lasting FAILURE_WINDOW_MS is reported, so one glitch never drops a working port.
     */
    static final class ReadHealth {
        static final int TIMEOUT_MS=200,FAST_MS=50,BACKOFF_MS=20,FAILURE_WINDOW_MS=1000;
        static final int DATA=0,IDLE=1,BACKOFF=2,FAILED=3;
        private long failingSince=-1;
        int observe(int count,long elapsedNanos,long nowNanos){
            if(count>0){failingSince=-1;return DATA;}
            boolean fast=elapsedNanos<TimeUnit.MILLISECONDS.toNanos(FAST_MS);
            // A zero-length packet is a successful transfer; pause only so a stream of them cannot spin.
            if(count==0){failingSince=-1;return fast?BACKOFF:IDLE;}
            // A full-length timeout is a quiet but healthy link.
            if(!fast){failingSince=-1;return IDLE;}
            if(failingSince<0){failingSince=nowNanos;return BACKOFF;}
            return nowNanos-failingSince>=TimeUnit.MILLISECONDS.toNanos(FAILURE_WINDOW_MS)?FAILED:BACKOFF;
        }
    }
    public static final class Cdc implements AutoCloseable {
        private final UsbDeviceConnection conn;private UsbInterface control,data;private UsbEndpoint in,out;
        private volatile boolean open=true;private Thread reader;
        private final AtomicBoolean closed=new AtomicBoolean();
        private boolean controlClaimed,dataClaimed,lineStateTouched;
        public Cdc(UsbManager manager,Port port,int baud) throws IOException {this(manager,port,baud,3);}
        /** Explicit per-port line state: Windows maimai NFC uses DTR only (1). */
        public Cdc(UsbManager manager,Port port,int baud,int lineState) throws IOException {
            if(lineState<0||lineState>3)throw new IllegalArgumentException("CDC line state");
            if(port.hid)throw new IOException(I18n.t(Msg.USB_NOT_SERIAL));
            if(!manager.hasPermission(port.device))throw new IOException(I18n.t(Msg.USB_PERMISSION_FIRST));
            conn=manager.openDevice(port.device);if(conn==null)throw new IOException(I18n.t(Msg.USB_OPEN_FAILED));
            try {
                control=byId(port.device,port.controlId);int dataId=-1;byte[] raw=conn.getRawDescriptors();int current=-1;
                if(raw!=null)for(int p=0;p+2<=raw.length;) {
                    int len=Protocol.u(raw[p]),type=Protocol.u(raw[p+1]);if(len<2 || p+len>raw.length)break;
                    if(type==4 && len>=9)current=Protocol.u(raw[p+2]);
                    if(type==0x24 && len>=5 && Protocol.u(raw[p+2])==6 && current==control.getId() && Protocol.u(raw[p+3])==control.getId()) dataId=Protocol.u(raw[p+4]);
                    p+=len;
                }
                if(dataId<0)dataId=control.getId()+1;
                data=byId(port.device,dataId);if(data.getInterfaceClass()!=10)throw new IOException(I18n.t(Msg.USB_NOT_CDC_DATA));
                controlClaimed=conn.claimInterface(control,true);
                if(!controlClaimed)throw new IOException(I18n.t(Msg.USB_BUSY));
                dataClaimed=conn.claimInterface(data,true);
                if(!dataClaimed)throw new IOException(I18n.t(Msg.USB_BUSY));
                for(int n=0;n<data.getEndpointCount();n++) {
                    UsbEndpoint e=data.getEndpoint(n);if(e.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)continue;
                    if(e.getDirection()==UsbConstants.USB_DIR_IN)in=e;else out=e;
                }
                if(in==null || out==null)throw new IOException(I18n.t(Msg.USB_NO_BULK));
                byte[] coding={(byte)baud,(byte)(baud>>8),(byte)(baud>>16),(byte)(baud>>24),0,0,8};
                if(conn.controlTransfer(0x21,0x20,0,control.getId(),coding,coding.length,1000)!=7)throw new IOException(I18n.t(Msg.USB_LINE_CODING));
                lineStateTouched=true;
                if(conn.controlTransfer(0x21,0x22,lineState,control.getId(),null,0,1000)<0)throw new IOException(I18n.t(Msg.USB_LINE_STATE));
            } catch(Exception e) {close();throw e instanceof IOException?(IOException)e:new IOException(e);}
        }
        public synchronized void start(Bytes callback,Failure failure) {
            if(!open||reader!=null)throw new IllegalStateException("CDC reader already started or closed");
            reader=new Thread(()->{
                byte[] b=new byte[1024];ReadHealth health=new ReadHealth();
                try {while(open) {
                    long started=System.nanoTime();
                    int n=conn.bulkTransfer(in,b,b.length,ReadHealth.TIMEOUT_MS);if(!open)break;
                    long now=System.nanoTime();
                    switch(health.observe(n,now-started,now)){
                        case ReadHealth.DATA:callback.accept(b,n);break;
                        case ReadHealth.BACKOFF:Thread.sleep(ReadHealth.BACKOFF_MS);break;
                        case ReadHealth.FAILED:throw new IOException(I18n.t(Msg.USB_READ_FAILING,n));
                        default:break;
                    }
                }}
                catch(Exception e) {if(open)failure.accept(e.toString());}
            },"Oniimai-CDC-"+control.getId());reader.start();
        }
        public synchronized void write(byte[] bytes) throws IOException {
            if(!open)throw new IOException(I18n.t(Msg.USB_CLOSED));
            for(int offset=0;offset<bytes.length;) {
                if(!open)throw new IOException(I18n.t(Msg.USB_CLOSED));
                int count=Math.min(32,bytes.length-offset);
                int n=conn.bulkTransfer(out,bytes,offset,count,1000);
                if(n<=0)throw new IOException(I18n.t(Msg.USB_WRITE_FAILED,offset,bytes.length));offset+=n;
            }
        }
        public void close() {
            if(!closed.compareAndSet(false,true))return;
            open=false;
            synchronized(this){
                // A failed claim must never change another channel's line state.
                // This matters when CDC siblings share one composite USB device.
                try {if(controlClaimed&&lineStateTouched)conn.controlTransfer(0x21,0x22,0,control.getId(),null,0,100);}catch(Exception ignored){}
                try {if(dataClaimed)conn.releaseInterface(data);}catch(Exception ignored){}
                try {if(controlClaimed)conn.releaseInterface(control);}catch(Exception ignored){}
                conn.close();
            }
            if(reader!=null && Thread.currentThread()!=reader)try {reader.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
    }
    public static final class Hid implements AutoCloseable {
        private final UsbDeviceConnection conn;private UsbInterface intf;private final UsbRequest request=new UsbRequest();
        private final UsbRequest outputRequest=new UsbRequest();private boolean interruptOutput;
        private final Object submissionLock=new Object();
        private volatile boolean open=true;private Thread reader;
        private final AtomicBoolean closed=new AtomicBoolean();
        private volatile boolean outputFailed;
        private volatile Output pendingOutput;
        private static final class Output {
            final ByteBuffer buffer=ByteBuffer.allocateDirect(Io4Output.REPORT_BYTES);
            final CompletableFuture<Integer> completion=new CompletableFuture<>();
            Output(byte[] report){buffer.put(report).flip();}
        }
        public Hid(UsbManager manager,Port port,Bytes listener,Failure failure) throws IOException {
            if(!manager.hasPermission(port.device))throw new IOException(I18n.t(Msg.USB_HID_PERMISSION));
            conn=manager.openDevice(port.device);if(conn==null)throw new IOException(I18n.t(Msg.USB_HID_OPEN_FAILED));
            try {
                intf=byId(port.device,port.controlId);
                if(!port.hid || !conn.claimInterface(intf,true))throw new IOException(I18n.t(Msg.USB_HID_BUSY));
                UsbEndpoint ep=null,out=null;
                for(int n=0;n<intf.getEndpointCount();n++){
                    UsbEndpoint e=intf.getEndpoint(n);if(e.getType()!=3)continue;
                    if(e.getDirection()==128)ep=e;else out=e;
                }
                if(ep==null || !request.initialize(conn,ep))throw new IOException(I18n.t(Msg.USB_HID_NO_ENDPOINT));
                // A missing OUT endpoint uses the standard HID SET_REPORT path.
                // Failure to initialise an existing endpoint does not guess another transport.
                if(out!=null){interruptOutput=outputRequest.initialize(conn,out);outputFailed=!interruptOutput;}
                final int capacity=Math.max(64,ep.getMaxPacketSize());
                reader=new Thread(()->{
                    ByteBuffer buffer=ByteBuffer.allocateDirect(capacity);boolean queued=false;
                    try {while(open) {
                        if(!queued){
                            synchronized(submissionLock){
                                if(!open)break;buffer.clear();
                                if(!request.queue(buffer))throw new IOException(I18n.t(Msg.USB_HID_QUEUE));queued=true;
                            }
                        }
                        UsbRequest done;try {done=conn.requestWait(1000);}catch(TimeoutException timeout){continue;}
                        if(!open)break;
                        // requestWait returns completions for every endpoint on this handle.
                        // Only this thread drains them, so output never steals a button report.
                        if(done==outputRequest){Output output=pendingOutput;if(output!=null)output.completion.complete(output.buffer.position());continue;}
                        if(done!=request)throw new IOException(I18n.t(Msg.USB_HID_CLOSED));queued=false;
                        int n=buffer.position();buffer.flip();byte[] bytes=new byte[n];buffer.get(bytes);if(n>0)listener.accept(bytes,n);
                    }}catch(Exception e){
                        outputFailed=true;Output output=pendingOutput;if(output!=null)output.completion.completeExceptionally(e);
                        if(open)failure.accept(e.toString());
                    }
                },"Oniimai-HID");reader.start();
            } catch(Exception e) {close();throw e instanceof IOException?(IOException)e:new IOException(e);}
        }
        /** Blocking, bounded output: call from the LED worker, never the UI or HID input callback.
         * An output error disables further writes on this handle without closing button input.
         */
        public synchronized void writeCeiling(int rgb)throws IOException {
            if(Thread.currentThread()==reader)throw new IOException("IO4 output cannot run on its input reader");
            if(!open||closed.get())throw new IOException("IO4 handle is closed");
            if(outputFailed)throw new IOException("IO4 ceiling output unavailable");
            byte[] report=Io4Output.ceiling(rgb);
            if(!interruptOutput){
                int sent;
                synchronized(submissionLock){
                    if(!open||closed.get())throw new IOException("IO4 handle is closed");
                    try{sent=conn.controlTransfer(0x21,0x09,0x0200|Io4Output.REPORT_ID,intf.getId(),report,report.length,300);}
                    catch(RuntimeException e){outputFailed=true;throw new IOException("IO4 SET_REPORT failed",e);}
                }
                if(sent!=report.length){outputFailed=true;throw new IOException("IO4 SET_REPORT incomplete");}
                if(!open)throw new IOException("IO4 output canceled");
                return;
            }
            Output output=new Output(report);
            try {
                synchronized(submissionLock){
                    if(!open||closed.get())throw new IOException("IO4 handle is closed");
                    pendingOutput=output;
                    if(!outputRequest.queue(output.buffer))throw new IOException("IO4 output queue failed");
                }
                if(output.completion.get(500,TimeUnit.MILLISECONDS)!=report.length)throw new IOException("IO4 output report incomplete");
                if(!open)throw new IOException("IO4 output canceled");
                pendingOutput=null;
            }catch(InterruptedException e){Thread.currentThread().interrupt();disableOutput();throw new IOException("IO4 output interrupted",e);}
            catch(ExecutionException|TimeoutException e){disableOutput();throw new IOException("IO4 output did not complete",e);}
            catch(IOException|RuntimeException e){disableOutput();throw e instanceof IOException?(IOException)e:new IOException(e);}
        }
        private void disableOutput(){
            outputFailed=true;
            // Never reuse a canceled request: a late completion can still reach requestWait.
            // Its buffer stays strongly referenced until this HID handle is closed.
            try{outputRequest.cancel();}catch(Exception ignored){}
        }
        public void close(){
            if(!closed.compareAndSet(false,true))return;
            synchronized(submissionLock){
                open=false;
                Output output=pendingOutput;if(output!=null)output.completion.completeExceptionally(new IOException("IO4 handle closed"));
                try{outputRequest.cancel();}catch(Exception ignored){}
            }
            try{request.cancel();}catch(Exception ignored){}
            // A control transfer may already be running. Do not close its fd underneath it.
            synchronized(this){conn.close();}
            if(reader!=null&&Thread.currentThread()!=reader)try{reader.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            try{request.close();}catch(Exception ignored){}
            try{outputRequest.close();}catch(Exception ignored){}
            pendingOutput=null;
        }
    }
}
