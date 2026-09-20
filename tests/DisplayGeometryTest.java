package io.oniimai.kanade;

public final class DisplayGeometryTest {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<0.003,message+": "+actual+" != "+expected);}
    static double[] point(float[] m,int w,int h,double x,double y){
        // TextureView first stretches the portrait producer into the view rectangle.
        x=x*w/DisplayGeometry.GAME_WIDTH;y=y*h/DisplayGeometry.GAME_HEIGHT;
        return new double[]{m[0]*x+m[1]*y+m[2],m[3]*x+m[4]*y+m[5]};
    }
    static void pointAt(float[] m,int w,int h,double x,double y,double ex,double ey,String name){
        double[] p=point(m,w,h,x,y);near(p[0],ex,name+" x");near(p[1],ey,name+" y");
    }
    static void bounds(int w,int h,double x,double y,double ow,double oh){
        float[] b=DisplayGeometry.rotatedBounds(w,h);near(b[0],x,"left");near(b[1],y,"top");near(b[2],ow,"width");near(b[3],oh,"height");
    }
    static void directPoint(float[] m,double x,double y,double ex,double ey,String name){
        near(m[0]*x+m[1]*y+m[2],ex,name+" x");
        near(m[3]*x+m[4]*y+m[5],ey,name+" y");
    }
    public static void main(String[] args){
        check(DisplayGeometry.GAME_WIDTH==1080&&DisplayGeometry.GAME_HEIGHT==1920,"Unity keeps the original portrait render buffer");
        bounds(1920,1080,0,0,1920,1080);bounds(3840,2160,0,0,3840,2160);bounds(1280,720,0,0,1280,720);
        bounds(2560,1440,0,0,2560,1440);bounds(1920,1200,0,60,1920,1080);bounds(3440,1440,440,0,2560,1440);
        bounds(0,1080,0,0,0,0);bounds(1920,0,0,0,0,0);bounds(-1,720,0,0,0,0);
        for(int[] host:new int[][]{{2868,1320},{2340,1080},{1920,1080},{3840,2160},{1024,768},{1080,1920},{3840,1080},{1536,864}}){
            int w=host[0],h=host[1];float[] b=DisplayGeometry.rotatedBounds(w,h);
            double l=b[0],t=b[1],r=l+b[2],bottom=t+b[3];
            check(l>=-0.001&&t>=-0.001&&r<=w+0.001&&bottom<=h+0.001,"no cropping");
            near(b[2]/b[3],16.0/9,"output ratio after rotation");
            for(boolean clockwise:new boolean[]{true,false}){
                float[] m=DisplayGeometry.rotationMatrix(w,h,clockwise);
                float[] direct=DisplayGeometry.surfaceMatrix(w,h,clockwise);
                directPoint(direct,0,0,clockwise?r:l,clockwise?t:bottom,"direct top-left");
                directPoint(direct,1080,0,clockwise?r:l,clockwise?bottom:t,"direct top-right");
                directPoint(direct,0,1920,clockwise?l:r,clockwise?t:bottom,"direct bottom-left");
                directPoint(direct,1080,1920,clockwise?l:r,clockwise?bottom:t,"direct bottom-right");
                directPoint(direct,540,960,w/2.0,h/2.0,"direct center");
                check(direct[0]*direct[4]-direct[1]*direct[3]>0,"direct rotation must not mirror");
                near(Math.hypot(direct[0],direct[3]),Math.hypot(direct[1],direct[4]),"direct uniform scaling");
                pointAt(m,w,h,0,0,clockwise?r:l,clockwise?t:bottom,"source top-left");
                pointAt(m,w,h,1080,0,clockwise?r:l,clockwise?bottom:t,"source top-right");
                pointAt(m,w,h,0,1920,clockwise?l:r,clockwise?t:bottom,"source bottom-left");
                pointAt(m,w,h,1080,1920,clockwise?l:r,clockwise?bottom:t,"source bottom-right");
                pointAt(m,w,h,540,960,w/2.0,h/2.0,"center");
                check(m[0]*m[4]-m[1]*m[3]>0,"rotation does not mirror the image");
                double[] p=point(m,w,h,0,0),px=point(m,w,h,100,0),py=point(m,w,h,0,100);
                near(Math.hypot(px[0]-p[0],px[1]-p[1]),Math.hypot(py[0]-p[0],py[1]-p[1]),"equal scale on both source axes");
                near((px[0]-p[0])*(py[0]-p[0])+(px[1]-p[1])*(py[1]-p[1]),0,"right angle preserved");
            }
        }
        for(int[] empty:new int[][]{{0,0},{0,1080},{1920,0},{-5,100}}){
            float[] m=DisplayGeometry.rotationMatrix(empty[0],empty[1],true);
            check(m[0]==1&&m[4]==1&&m[8]==1,"empty layout uses identity");
            for(float v:m)check(!Float.isNaN(v)&&!Float.isInfinite(v),"finite coefficients");
            float[] direct=DisplayGeometry.surfaceMatrix(empty[0],empty[1],false);
            check(direct[0]==1&&direct[4]==1&&direct[8]==1,"empty direct layout uses identity");
            for(float v:direct)check(Float.isFinite(v),"finite direct coefficients");
        }
        System.out.println("PASS: "+checks+" rotated display geometry checks");
    }
}
