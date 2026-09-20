package io.oniimai.kanade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Missing/corrupt/oversized notices must fail visibly, never read arbitrary entries. */
public final class LicenseTextTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private interface Read {void run() throws IOException;}
    private static void fails(Read action)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed);}
    public static void main(String[] args)throws Exception{
        Path zip=Files.createTempFile("oniimai-notices-", ".zip");
        try{
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){
                out.putNextEntry(new ZipEntry("META-INF/licenses/GPL-3.0-only.txt"));
                out.write("Licence\r\n한국어 · 中文\r\n".getBytes(StandardCharsets.UTF_8));out.closeEntry();
                out.putNextEntry(new ZipEntry("unlisted.txt"));out.write(7);out.closeEntry();
                out.putNextEntry(new ZipEntry("META-INF/licenses/NDK-NOTICE.txt"));
                out.write(new byte[2*1024*1024+1]);out.closeEntry();
            }
            check(LicenseText.read(zip.toString(),"GPL-3.0-only.txt").equals("Licence\n한국어 · 中文\n"));
            fails(()->LicenseText.read(zip.toString(),"PROJECT-NOTICE.txt"));
            fails(()->LicenseText.read(zip.toString(),"../../unlisted.txt"));
            fails(()->LicenseText.read(zip.toString(),"unlisted.txt"));
            fails(()->LicenseText.read(zip.toString(),"NDK-NOTICE.txt"));
            fails(()->LicenseText.read(null,"GPL-3.0-only.txt"));
            String[] files=LicenseText.files();files[0]="changed";
            check(LicenseText.files()[0].equals("PROJECT-NOTICE.txt"));
            Thread.currentThread().interrupt();
            try{fails(()->LicenseText.read(zip.toString(),"GPL-3.0-only.txt"));}finally{Thread.interrupted();}
            // Replacement succeeds after the reader closes its ZIP and stream, also on Windows.
            Files.write(zip,new byte[]{1,2,3});
            fails(()->LicenseText.read(zip.toString(),"GPL-3.0-only.txt"));
        }finally{Files.deleteIfExists(zip);}
        if(args.length==1){
            for(String file:LicenseText.files())check(!LicenseText.read(args[0],file).isEmpty());
        }
        System.out.println("PASS: "+checks+" packaged notice reader checks");
    }
}
