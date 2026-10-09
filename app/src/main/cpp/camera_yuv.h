#pragma once
#include <stdint.h>
#include <stddef.h>

namespace CameraYuv {
struct Plane {const uint8_t* data;size_t length;int row,pixel;};
static inline bool fits(const Plane& p,int w,int h){
    return p.data&&w>0&&h>0&&p.row>0&&p.pixel>0&&p.pixel<=4&&
        size_t(w-1)*p.pixel+1<=size_t(p.row)&&size_t(h-1)*p.row+size_t(w-1)*p.pixel<p.length;
}
static inline int clip(int value){return value<0?0:(value>255?255:value);}
// Camera2 planes are top-down. Unity Color32 arrays are bottom-up RGBA bytes.
static inline bool convert(Plane y,Plane u,Plane v,int w,int h,uint32_t* rgba,size_t count){
    if(!rgba||w<1||h<1||w>4096||h>4096||size_t(w)*h!=count||
        !fits(y,w,h)||!fits(u,(w+1)/2,(h+1)/2)||!fits(v,(w+1)/2,(h+1)/2))return false;
    for(int row=0;row<h;row++)for(int col=0;col<w;col++){
        int yy=int(y.data[size_t(row)*y.row+size_t(col)*y.pixel])-16;
        if(yy<0)yy=0;
        int uu=int(u.data[size_t(row/2)*u.row+size_t(col/2)*u.pixel])-128;
        int vv=int(v.data[size_t(row/2)*v.row+size_t(col/2)*v.pixel])-128;
        int r=clip((298*yy+409*vv+128)>>8),g=clip((298*yy-100*uu-208*vv+128)>>8),b=clip((298*yy+516*uu+128)>>8);
        rgba[size_t(h-1-row)*w+col]=uint32_t(r)|(uint32_t(g)<<8)|(uint32_t(b)<<16)|0xff000000u;
    }
    return true;
}
}
