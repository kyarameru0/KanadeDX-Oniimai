package io.oniimai.kanade;

import java.util.ArrayDeque;

/** Bounded command metadata only; never stores payloads or physical card identities. */
final class AimeTrace {
    static final int LIMIT=64;
    private final ArrayDeque<String> events=new ArrayDeque<>();
    private final long origin=System.nanoTime();

    synchronized void sent(int address,int sequence,int command,byte[] data){
        StringBuilder value=new StringBuilder("tx node=").append(address).append(" seq=").append(sequence)
            .append(" cmd=").append(Integer.toHexString(command)).append(" len=").append(data.length);
        if(address==AimeProtocol.ADDRESS&&command==AimeProtocol.START&&data.length==1)value.append(" radio=").append(data[0]&255);
        if(address==AimeProtocol.ADDRESS&&command==AimeProtocol.FELICA&&data.length>=10){
            int op=data[9]&255;value.append(" inner=").append(data[8]&255).append(" op=").append(op);
            if(op==0&&data.length==14)value.append(" system=").append(Integer.toHexString((data[10]&255)<<8|(data[11]&255)))
                .append(" request=").append(data[12]&255).append(" slot=").append(data[13]&255);
            if(op==6&&data.length==24)value.append(" block=").append(data[23]&255);
        }
        add(value.toString());
    }

    synchronized void received(AimeProtocol.Reply reply){
        byte[] data=reply.payload;
        StringBuilder value=new StringBuilder("rx node=").append(reply.address).append(" seq=").append(reply.sequence)
            .append(" cmd=").append(Integer.toHexString(reply.command)).append(" status=").append(reply.status).append(" len=").append(data.length);
        if(reply.address==AimeProtocol.ADDRESS&&reply.command==AimeProtocol.DETECT&&data.length>=1){
            value.append(" cards=").append(data[0]&255);
            if((data[0]&255)>0&&data.length>=3)value.append(" type=").append(Integer.toHexString(data[1]&255)).append(" idLength=").append(data[2]&255);
        }
        if(reply.address==AimeProtocol.ADDRESS&&reply.command==AimeProtocol.FELICA&&data.length>=1){
            value.append(" inner=").append(data[0]&255);
            if(data.length>=2){
                int op=data[1]&255;value.append(" op=").append(op);
                if(op==1&&data.length==20&&(data[0]&255)==20)value.append(" system=").append(Integer.toHexString((data[18]&255)<<8|(data[19]&255)));
                if((op==7||op==9)&&data.length>=12)value.append(" flags=").append(data[10]&255).append(',').append(data[11]&255);
            }
        }
        add(value.toString());
    }

    synchronized void failure(int status){add("transport status="+status);}
    synchronized String[] snapshot(){return events.toArray(new String[0]);}
    private void add(String event){
        if(events.size()==LIMIT)events.removeFirst();
        events.addLast("+"+Math.max(0,(System.nanoTime()-origin)/1000000)+"ms "+event);
    }
}
