package io.oniimai.kanade;

/** Physical output conversion, independent of USB and Android. RGB packed as 0xRRGGBB. */
final class LedFrames {
    static int scale(int rgb,int percent){
        percent=Math.max(0,Math.min(100,percent));
        return (((rgb>>>16&255)*percent+50)/100<<16)|(((rgb>>>8&255)*percent+50)/100<<8)|((rgb&255)*percent+50)/100;
    }
    static int[] physical(int[] colors,int brightness,int rotation,boolean reverse){
        if(colors==null||colors.length!=11)throw new IllegalArgumentException("11 LED colors required");
        int[] result=new int[11];rotation=Math.floorMod(rotation,8);
        for(int i=0;i<8;i++)result[Math.floorMod(rotation+(reverse?-i:i),8)]=scale(colors[i],brightness);
        for(int i=8;i<11;i++){int fet=scale(colors[i],brightness);result[i]=Math.max(fet>>>16&255,Math.max(fet>>>8&255,fet&255));}return result;
    }
    static byte[] color(int index,int rgb){
        if(index<0||index>31)throw new IllegalArgumentException("LED index 0..31");
        return new byte[]{(byte)index,(byte)(rgb>>16),(byte)(rgb>>8),(byte)rgb};
    }
}
