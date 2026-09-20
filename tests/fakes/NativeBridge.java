package io.oniimai.kanade;
final class NativeBridge {
    static volatile int[] snapshot={255,1,4095,0xff0000,0x00ff00,0x0000ff,0x123456,0xd0e001,0xabcdef,0x567890,0xffffff,0x808080,0x404040,0x202020,0x123456};
    static int[] ledSnapshot(){return snapshot.clone();}
}
