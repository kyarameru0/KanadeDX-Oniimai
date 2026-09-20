package io.oniimai.kanade;

import java.util.Arrays;

public final class AimeTraceTest {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static void feed(AimeProtocol.Parser parser,byte[] wire){parser.feed(wire,wire.length);}
    public static void main(String[] args){
        AimeTrace trace=new AimeTrace();byte[] id=AimeProtocolTest.hex("deadbeefcafebabe");
        trace.sent(0,1,AimeProtocol.FELICA,AimeProtocol.felicaPoll(id));
        check(trace.snapshot()[0].endsWith("tx node=0 seq=1 cmd=71 len=14 inner=6 op=0 system=ffff request=1 slot=15"),"Polling parameters retained without IDm");
        AimeProtocol.Parser parser=new AimeProtocol.Parser(trace::received);
        feed(parser,AimeProtocolTest.response(0,1,0x71,0,AimeProtocolTest.hex("1401deadbeefcafebabe00f100000001430088b4")));
        check(trace.snapshot()[1].endsWith("rx node=0 seq=1 cmd=71 status=0 len=20 inner=20 op=1 system=88b4"),"Response shape and system retained without IDm or PMm");
        feed(parser,AimeProtocolTest.response(0,2,0x71,0,new byte[]{0}));
        check(trace.snapshot()[2].endsWith("len=1 inner=0"),"Empty FeliCa response distinguished from malformed identities");
        trace.sent(0,3,0x71,AimeProtocol.felicaRead(id,0x82));
        check(trace.snapshot()[3].endsWith("inner=16 op=6 block=130"),"Read block number only");
        feed(parser,AimeProtocolTest.response(0,3,0x71,0,AimeProtocolTest.hex("1d07deadbeefcafebabe00000101234567890123456789012345678901")));
        check(trace.snapshot()[4].endsWith("inner=29 op=7 flags=0,0"),"Read status retained without data");
        trace.sent(0,4,0x54,new byte[]{1,2,3,4,5,6});
        check(trace.snapshot()[5].endsWith("cmd=54 len=6"),"Authentication keys omitted");
        String text=Arrays.toString(trace.snapshot());
        check(!text.contains("deadbeef")&&!text.contains("0123456789")&&!text.contains("00f1000000014300"),"No synthetic IDs, card data or PMm emitted");
        String[] detached=trace.snapshot();detached[0]="changed";
        check(!trace.snapshot()[0].equals("changed"),"Snapshot cannot mutate history");
        for(int i=0;i<100;i++)trace.failure(-2);
        check(trace.snapshot().length==AimeTrace.LIMIT&&!Arrays.toString(trace.snapshot()).contains("seq=1"),"Long-running reader retains bounded recent history only");
        System.out.println("PASS: "+checks+" NFC metadata privacy/bounds checks");
    }
}
