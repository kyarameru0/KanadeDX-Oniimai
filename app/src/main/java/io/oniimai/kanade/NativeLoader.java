package io.oniimai.kanade;

import android.content.pm.ApplicationInfo;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Loads this module's ARM64 JNI library when an embedded module has no installed native directory. */
final class NativeLoader {
    private static final String LIBRARY="liboniimai_kanade.so";
    private static final String ENTRY="lib/arm64-v8a/"+LIBRARY;
    private static final long MAX_BYTES=16L*1024*1024;
    private NativeLoader() {}

    /** Call before loading Unity's Activity class; library basename preserves native_init registration. */
    static synchronized void load(ApplicationInfo module,ApplicationInfo target) throws IOException {
        if(module==null)throw new IOException("Module ApplicationInfo is unavailable");
        Throwable directFailure=null;
        if(module.nativeLibraryDir!=null&&!module.nativeLibraryDir.isEmpty()){
            try{System.load(new File(module.nativeLibraryDir,LIBRARY).getAbsolutePath());return;}
            catch(UnsatisfiedLinkError|SecurityException failure){directFailure=failure;}
        }
        try{
            if(module.sourceDir==null||module.sourceDir.isEmpty())throw new IOException("Module APK path is unavailable");
            if(target==null||target.dataDir==null||target.dataDir.isEmpty())throw new IOException("Target app data directory is unavailable");
            // Framework-owned dataDir may itself use Android's /data/data alias. Resolve it once,
            // then reject symlinks in every cache component owned by this helper.
            File dataRoot=new File(target.dataDir).getCanonicalFile();
            if(!dataRoot.isDirectory())throw new IOException("Target app data directory does not exist");
            File cache=directory(directory(dataRoot,"code_cache"),"oniimai-native");
            try(ZipFile apk=new ZipFile(module.sourceDir)){
                ZipEntry entry=uniqueEntry(apk);
                long size=entry.getSize(),crc=entry.getCrc();
                if(size<64||size>MAX_BYTES||crc<0)throw new IOException("Invalid module native library size or CRC");
                File generation=directory(cache,Long.toHexString(crc)+"-"+size);
                File library=new File(generation,LIBRARY);
                if(!matches(library,size,crc))extract(apk,entry,generation,library,size,crc);
                // Recheck after atomic publication (also handles another process publishing first).
                if(!matches(library,size,crc))throw new IOException("Module native cache verification failed");
                checkPath(library,false);
                try{Os.chmod(library.getAbsolutePath(),0444);}
                catch(ErrnoException failure){throw io("Cannot make module native cache read-only",failure);}
                System.load(library.getAbsolutePath());
            }
        }catch(IOException|RuntimeException|UnsatisfiedLinkError failure){
            if(directFailure!=null)failure.addSuppressed(directFailure);
            throw failure;
        }
    }

    private static ZipEntry uniqueEntry(ZipFile apk) throws IOException {
        ZipEntry result=null;
        Enumeration<? extends ZipEntry> entries=apk.entries();
        while(entries.hasMoreElements()){
            ZipEntry entry=entries.nextElement();
            if(!ENTRY.equals(entry.getName()))continue;
            if(result!=null||entry.isDirectory())throw new IOException("Ambiguous module ARM64 library entry");
            result=entry;
        }
        if(result==null)throw new IOException("Module APK does not contain "+ENTRY);
        return result;
    }

    private static File directory(File parent,String name) throws IOException {
        File directory=new File(parent,name);
        if(Files.isSymbolicLink(directory.toPath()))throw new IOException("Symbolic link in module native cache");
        try{Files.createDirectory(directory.toPath());}
        catch(FileAlreadyExistsException ignored){/* Validate the existing component below. */}
        checkPath(directory,true);
        return directory;
    }

    private static void checkPath(File path,boolean directory) throws IOException {
        if(Files.isSymbolicLink(path.toPath())||!path.getCanonicalFile().equals(path.getAbsoluteFile()))
            throw new IOException("Symbolic link in module native cache path");
        boolean valid=directory?Files.isDirectory(path.toPath(),LinkOption.NOFOLLOW_LINKS)
                :Files.isRegularFile(path.toPath(),LinkOption.NOFOLLOW_LINKS);
        if(!valid)throw new IOException("Unexpected module native cache file type");
    }

    private static boolean matches(File file,long size,long crc) throws IOException {
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return false;
        checkPath(file,false);
        FileDescriptor descriptor;
        try{descriptor=Os.open(file.getAbsolutePath(),OsConstants.O_RDONLY|OsConstants.O_CLOEXEC
                |OsConstants.O_NOFOLLOW|OsConstants.O_NONBLOCK,0);}
        catch(ErrnoException failure){throw io("Cannot open module native cache",failure);}
        try(FileInputStream input=new FileInputStream(descriptor)){
            StructStat stat;
            try{stat=Os.fstat(descriptor);}
            catch(ErrnoException failure){throw io("Cannot inspect module native cache",failure);}
            if(!OsConstants.S_ISREG(stat.st_mode))throw new IOException("Module native cache is not a regular file");
            return stat.st_size==size&&transferAndVerify(input,null,size,crc);
        }
    }

    private static void extract(ZipFile apk,ZipEntry entry,File directory,File destination,long size,long crc) throws IOException {
        checkPath(directory,true);
        File staging=new File(directory,".native-"+UUID.randomUUID()+".tmp");
        FileDescriptor descriptor;
        try{descriptor=Os.open(staging.getAbsolutePath(),OsConstants.O_WRONLY|OsConstants.O_CREAT
                |OsConstants.O_EXCL|OsConstants.O_NOFOLLOW|OsConstants.O_CLOEXEC,0600);}
        catch(ErrnoException failure){throw io("Cannot create module native cache",failure);}
        try{
            // Establish ownership of the descriptor before opening the ZIP stream so it always closes.
            try(FileOutputStream output=new FileOutputStream(descriptor);InputStream input=apk.getInputStream(entry)){
                if(!transferAndVerify(input,output,size,crc))throw new IOException("Module native library CRC or ELF mismatch");
                try{Os.fchmod(descriptor,0444);}
                catch(ErrnoException failure){throw io("Cannot protect module native cache",failure);}
                output.getFD().sync();
            }
            checkPath(directory,true);checkPath(staging,false);
            if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS))checkPath(destination,false);
            // Same-directory atomic rename never exposes a partially written shared library.
            Files.move(staging.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{
            // Only this invocation's unpredictable temporary file can be removed.
            Files.deleteIfExists(staging.toPath());
        }
    }

    private static boolean transferAndVerify(InputStream input,FileOutputStream output,long expectedSize,long expectedCrc) throws IOException {
        CRC32 crc=new CRC32();byte[] buffer=new byte[16*1024],header=new byte[20];
        long count=0;int headerSize=0,read;
        while((read=input.read(buffer))!=-1){
            if(read==0)continue;
            if(count+read>expectedSize||count+read>MAX_BYTES)throw new IOException("Module native library exceeds its declared size");
            int keep=Math.min(read,header.length-headerSize);
            if(keep>0){System.arraycopy(buffer,0,header,headerSize,keep);headerSize+=keep;}
            crc.update(buffer,0,read);if(output!=null)output.write(buffer,0,read);count+=read;
        }
        return count==expectedSize&&crc.getValue()==expectedCrc&&headerSize==header.length
                &&header[0]==0x7f&&header[1]=='E'&&header[2]=='L'&&header[3]=='F'
                &&header[4]==2&&header[5]==1&&header[6]==1 // ELF64, little endian, current version.
                &&header[16]==3&&header[17]==0 // Shared object.
                &&(header[18]&255)==183&&header[19]==0; // AArch64.
    }

    private static IOException io(String message,Exception cause){return new IOException(message,cause);}
}
