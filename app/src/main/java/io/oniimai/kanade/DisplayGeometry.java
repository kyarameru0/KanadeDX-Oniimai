package io.oniimai.kanade;

final class DisplayGeometry {
    static final int GAME_WIDTH=1080, GAME_HEIGHT=1920;
    /** Bounds after a quarter turn: the portrait game's height becomes output width. */
    static float[] rotatedBounds(int width,int height){
        if(width<=0||height<=0)return new float[]{0,0,0,0};
        float scale=Math.min(width/(float)GAME_HEIGHT,height/(float)GAME_WIDTH);
        float w=GAME_HEIGHT*scale,h=GAME_WIDTH*scale;
        return new float[]{(width-w)/2f,(height-h)/2f,w,h};
    }
    /** Direct producer pixels to the external window, with no TextureView pre-scaling. */
    static float[] surfaceMatrix(int width,int height,boolean clockwise){
        if(width<=0||height<=0)return new float[]{1,0,0,0,1,0,0,0,1};
        float[] b=rotatedBounds(width,height);float s=b[2]/GAME_HEIGHT;
        return clockwise
            ?new float[]{0,-s,b[0]+b[2],s,0,b[1],0,0,1}
            :new float[]{0,s,b[0],-s,0,b[1]+b[3],0,0,1};
    }
    /**
     * TextureView initially maps the entire portrait buffer onto its view rectangle.
     * Undo that non-uniform scale while rotating its CONTENT, not its view bounds.
     * Values use Android Matrix's row-major order. Positive screen Y points down.
     */
    static float[] rotationMatrix(int width,int height,boolean clockwise){
        if(width<=0||height<=0)return new float[]{1,0,0,0,1,0,0,0,1};
        float[] b=rotatedBounds(width,height);float left=b[0],top=b[1],w=b[2],h=b[3];
        return clockwise
            ?new float[]{0,-w/height,left+w,h/width,0,top,0,0,1}
            :new float[]{0,w/height,left,-h/width,0,top+h,0,0,1};
    }
}
