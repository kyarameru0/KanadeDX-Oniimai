#pragma once
#include <stdint.h>
#include <stddef.h>

// Unity Color32 rows start at the bottom. Correct the sensor's vertical flip,
// rotate clockwise into display orientation, then center-crop without stretching.
// No allocation, managed references, or camera I/O in this independently tested code.
namespace CameraPixels {
static constexpr int WIDTH=1280,HEIGHT=960,MAX_EDGE=8192;
static constexpr size_t MAX_PIXELS=16*1024*1024;
// Use the same selected rectangle for the guide, live preview and final icon.
// There is no P1/P2 offset: moving the displayed image moves the crop inversely.
static inline bool viewCrop(int sw,int sh,int dw,int dh,float viewWidth,float viewHeight,
                            float imageX,float imageY,int& left,int& bottom){
    if(sw<=0||sh<=0||sw>MAX_EDGE||sh>MAX_EDGE||dw<=0||dh<=0||dw>sw||dh>sh||
       !(viewWidth>0&&viewWidth<=MAX_EDGE&&viewHeight>0&&viewHeight<=MAX_EDGE))return false;
    float dx=-imageX*sw/viewWidth,dy=-imageY*sh/viewHeight;
    if(!(dx>=-sw&&dx<=sw&&dy>=-sh&&dy<=sh))return false; // Reject NaN/Inf too.
    left=(sw-dw)/2+int(dx);bottom=(sh-dh)/2+int(dy);
    left=left<0?0:left>sw-dw?sw-dw:left;
    bottom=bottom<0?0:bottom>sh-dh?sh-dh:bottom;
    return true;
}
static inline bool shape(int w,int h,size_t count){
    return w>16&&h>16&&w<=MAX_EDGE&&h<=MAX_EDGE&&size_t(w)*size_t(h)==count&&count<=MAX_PIXELS;
}
static inline bool mirrorHorizontal(const uint32_t* source,uint32_t* dest,int w,int h,size_t count){
    if(!source||!dest||source==dest||w<=0||h<=0||w>MAX_EDGE||h>MAX_EDGE||size_t(w)*h!=count||count>MAX_PIXELS)return false;
    for(int y=0;y<h;y++)for(int x=0;x<w;x++)dest[size_t(y)*w+x]=source[size_t(y)*w+w-1-x];
    return true;
}
// A captured memorial frame already owns its output buffer. Mirror once before
// publishing it so the game's JPEG/result path sees the same facing as preview.
static inline bool mirrorHorizontalInPlace(uint32_t* pixels,int w,int h,size_t count){
    if(!pixels||w<=0||h<=0||w>MAX_EDGE||h>MAX_EDGE||size_t(w)*h!=count||count>MAX_PIXELS)return false;
    for(int y=0;y<h;y++)for(int x=0;x<w/2;x++){
        size_t a=size_t(y)*w+x,b=size_t(y)*w+w-1-x;
        uint32_t pixel=pixels[a];pixels[a]=pixels[b];pixels[b]=pixel;
    }
    return true;
}
// Copy a photo crop at native pixel scale. Offsets move the selected region,
// not the image; clamp adjustment controls to real camera pixels.
static inline bool crop(const uint32_t* source,int sw,int sh,size_t sourceCount,
                        uint32_t* dest,int dw,int dh,size_t destCount,int dx,int dy){
    if(!source||!dest||source==dest||sw<=0||sh<=0||dw<=0||dh<=0||
       sw>MAX_EDGE||sh>MAX_EDGE||dw>sw||dh>sh||size_t(sw)*sh!=sourceCount||
       size_t(dw)*dh!=destCount||sourceCount>MAX_PIXELS)return false;
    int64_t x=int64_t(sw-dw)/2+dx,y=int64_t(sh-dh)/2+dy;
    x=x<0?0:x>sw-dw?sw-dw:x;y=y<0?0:y>sh-dh?sh-dh:y;
    for(int row=0;row<dh;row++)for(int col=0;col<dw;col++)
        dest[size_t(row)*dw+col]=source[size_t(y+row)*sw+size_t(x)+col];
    return true;
}
static inline bool copy(const uint32_t* source,int sw,int sh,size_t sourceCount,
                        uint32_t* dest,int dw,int dh,size_t destCount,int rotation,bool flip){
    if(!source||!dest||sw<=0||sh<=0||dw<=0||dh<=0||sw>MAX_EDGE||sh>MAX_EDGE||dw>MAX_EDGE||dh>MAX_EDGE||
       size_t(sw)*sh!=sourceCount||size_t(dw)*dh!=destCount||sourceCount>MAX_PIXELS||destCount>MAX_PIXELS||
       (rotation!=0&&rotation!=90&&rotation!=180&&rotation!=270)||source==dest)return false;
    const int rw=(rotation%180)?sh:sw,rh=(rotation%180)?sw:sh;
    int cw=rw,ch=rh;
    if(int64_t(rw)*dh>int64_t(rh)*dw)cw=int(int64_t(rh)*dw/dh);
    else ch=int(int64_t(rw)*dh/dw);
    if(cw<1||ch<1)return false;
    const int left=(rw-cw)/2,bottom=(rh-ch)/2;
    for(int y=0;y<dh;y++){
        int ry=bottom+int((int64_t(2*y+1)*ch)/(2*dh));
        for(int x=0;x<dw;x++){
            int rx=left+int((int64_t(2*x+1)*cw)/(2*dw)),sx=rx,sy=ry;
            if(rotation==90){sx=sw-1-ry;sy=rx;}
            else if(rotation==180){sx=sw-1-rx;sy=sh-1-ry;}
            else if(rotation==270){sx=ry;sy=sh-1-rx;}
            if(flip)sy=sh-1-sy;
            dest[size_t(y)*dw+x]=source[size_t(sy)*sw+sx];
        }
    }
    return true;
}
}
