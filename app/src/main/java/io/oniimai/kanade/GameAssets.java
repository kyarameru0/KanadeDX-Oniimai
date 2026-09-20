package io.oniimai.kanade;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontStyle;
import android.os.Build;
import android.util.Log;
import java.io.File;

/** System typography and an original, code-drawn empty-artwork icon. No packaged visual assets. */
final class GameAssets {
    private static final Typeface[] FONTS=new Typeface[3];
    private static Bitmap placeholder;
    private GameAssets(){}
    // No copied game assets or bundled vendor fonts. Keep no Activity/Context reference.
    static String apkPath;
    static void moduleApk(String path){apkPath=path;}
    static void bind(Context ignored){}
    static Typeface font(Context ignored){return typeface(500);}
    static Typeface regular(Context ignored){return typeface(400);}
    static Typeface semibold(Context ignored){return typeface(600);}
    private static synchronized Typeface typeface(int weight){
        int slot=(weight-400)/100;if(FONTS[slot]!=null)return FONTS[slot];
        // MiSans uses a different variable-axis scale from CSS/Android font weight.
        // These values match the Xiaomi system family: 400=330, 500=380, 600=450.
        int axis=weight==400?330:weight==500?380:450;
        Typeface result=null;
        try {
            File latin=new File("/system/fonts/MiSansLatinVF.ttf");
            if(Build.VERSION.SDK_INT>=29&&latin.canRead()){
                Typeface.CustomFallbackBuilder builder=new Typeface.CustomFallbackBuilder(family(latin,weight,axis));
                String[] scripts={"MiSansKoreanVF.ttf","MiSansVF.ttf","MiSansJapaneseVF.ttf"};
                for(String script:scripts){File file=new File("/system/fonts",script);if(file.canRead())builder.addCustomFallback(family(file,weight,axis));}
                result=builder.setSystemFallback("sans-serif").setStyle(new FontStyle(weight,FontStyle.FONT_SLANT_UPRIGHT)).build();
                Log.i("OniimaiFont","System MiSans Latin/Korean/CJK, weight="+weight+" axis="+axis);
            } else {
                // Android 9 and older MIUI builds can use the installed overlay plus system fallback.
                String[] candidates={"MiSansVF_Overlay.ttf","MiSansVF.ttf","MiLanProVF.ttf"};
                for(String name:candidates){File file=new File("/system/fonts",name);if(!file.canRead())continue;
                    Typeface.Builder builder=new Typeface.Builder(file).setWeight(weight).setFallback("sans-serif");
                    if(name.startsWith("MiSans"))builder.setFontVariationSettings("'wght' "+axis);
                    result=builder.build();if(result!=null){Log.i("OniimaiFont","System font "+file+", weight="+weight);break;}
                }
            }
        } catch(Exception unavailable){Log.w("OniimaiFont","System MiSans unavailable; using Android sans-serif",unavailable);}
        if(result==null)result=Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),weight,false);
        FONTS[slot]=result;return result;
    }
    @android.annotation.TargetApi(29)
    private static FontFamily family(File file,int weight,int axis) throws java.io.IOException {
        Font font=new Font.Builder(file).setWeight(weight).setSlant(FontStyle.FONT_SLANT_UPRIGHT)
            .setFontVariationSettings("'wght' "+axis).build();
        return new FontFamily.Builder(font).build();
    }
    static synchronized Bitmap placeholder(){
        if(placeholder==null){
            placeholder=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(placeholder);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(GameUi.PALE);canvas.drawRoundRect(new RectF(0,0,192,192),24,24,paint);
            // A restrained equalizer mark denotes artwork unavailable; it does not represent live data.
            int[] heights={36,64,86,54};paint.setColor(GameUi.BLUE);
            for(int i=0;i<heights.length;i++){
                float x=48+i*26,top=96-heights[i]/2f;
                canvas.drawRoundRect(new RectF(x,top,x+14,top+heights[i]),7,7,paint);
            }
        }
        return placeholder;
    }
}
