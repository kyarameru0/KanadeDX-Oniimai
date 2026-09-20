package io.oniimai.kanade;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read the exact notices packaged in this module, including when hosted by Unity. */
final class LicenseText {
    static final String SOURCE_URL="https://github.com/kyarameru0/KanadeDX-Oniimai";
    private static final int MAX_BYTES=2*1024*1024;
    private static final String[] FILES={"PROJECT-NOTICE.txt","GPL-3.0-only.txt",
        "PN532-Aime-Reader-MPL-2.0.txt","Apache-2.0.txt","Mai2Touch-MIT.txt",
        "Miuix-Apache-2.0.txt","DEPENDENCY_NOTICES.txt","NDK-NOTICE.txt"};
    static String[] files(){return FILES.clone();}
    static String read(String apk,String name) throws IOException {
        if(apk==null||!Arrays.asList(FILES).contains(name))throw new IOException("Notice unavailable");
        try(ZipFile zip=new ZipFile(apk)){
            ZipEntry entry=zip.getEntry("META-INF/licenses/"+name);
            if(entry==null||entry.isDirectory()||entry.getSize()>MAX_BYTES)throw new IOException("Notice unavailable");
            try(InputStream input=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Notice load canceled");
                    if(count>MAX_BYTES-out.size())throw new IOException("Notice too large");
                    out.write(buffer,0,count);
                }
                return new String(out.toByteArray(),StandardCharsets.UTF_8).replace("\r\n","\n");
            }
        }
    }
}
