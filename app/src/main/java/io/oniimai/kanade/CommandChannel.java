package io.oniimai.kanade;

import java.io.IOException;
import java.util.concurrent.*;

/** One outstanding request; debug messages are independent. Timeout poisons the channel to prevent stale ACK reuse. */
public final class CommandChannel implements AutoCloseable {
    public interface Logger {void log(String message);}
    private final UsbIo.Cdc port;private volatile CompletableFuture<byte[]> waiting;private volatile int expected=-1;
    private volatile boolean closed;private final Logger logger;
    public CommandChannel(UsbIo.Cdc port,UsbIo.Bytes debug,Logger logger,UsbIo.Failure failure) {
        this.port=port;this.logger=logger;
        Protocol.CommandParser parser=new Protocol.CommandParser((cmd,status,data)->{
            if(cmd==Protocol.DEBUG_DATA){if(status==0)debug.accept(data,data.length);return;}
            logger.log("RX cmd="+String.format("%02X",cmd)+" status="+status+" bytes="+data.length);
            CompletableFuture<byte[]> pending=waiting;
            if(pending!=null && cmd==expected){if(status==0)pending.complete(data);else pending.completeExceptionally(new IOException(I18n.t(Msg.COMMAND_DEVICE_ERROR,status)));}
        });
        port.start(parser::feed,message->{closed=true;fail(new IOException(message));port.close();failure.accept(message);});
    }
    public synchronized byte[] request(int cmd,byte[] data) throws IOException {
        if(closed)throw new IOException(I18n.t(Msg.COMMAND_CLOSED));
        CompletableFuture<byte[]> future=new CompletableFuture<>();expected=cmd;waiting=future;
        try {
            byte[] packet=Protocol.command(cmd,data);logger.log("TX cmd="+String.format("%02X",cmd)+" bytes="+data.length);port.write(packet);
            return future.get(4,TimeUnit.SECONDS);
        } catch(TimeoutException e) {closed=true;port.close();throw new IOException(I18n.t(Msg.COMMAND_TIMEOUT));}
        catch(ExecutionException e){throw new IOException(e.getCause().getMessage(),e.getCause());}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(I18n.t(Msg.COMMAND_CANCELLED),e);}
        catch(IOException e){closed=true;port.close();throw e;}
        finally {waiting=null;expected=-1;}
    }
    public byte[] request(int cmd) throws IOException {return request(cmd,new byte[0]);}
    public boolean isClosed(){return closed;}
    private void fail(IOException error){CompletableFuture<byte[]> future=waiting;if(future!=null)future.completeExceptionally(error);}
    public void close(){closed=true;fail(new IOException(I18n.t(Msg.COMMON_DISCONNECT)));port.close();}
}
