package com.tanzir.javario;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** File icon that shows the file's own extension (JAVA, XML, MD ...). */
public class ExtIcon extends Drawable {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String label;

    public ExtIcon(String ext) {
        label = ext.isEmpty() ? "FILE" : (ext.length() > 4 ? ext.substring(0, 4) : ext).toUpperCase();
        bg.setColor(colorFor(ext));
        tx.setColor(0xFFFFFFFF);
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    public static int colorFor(String e) {
        switch (e) {
            case "java": return 0xFFE76F51;
            case "xml": return 0xFF2A9D8F;
            case "md": return 0xFF6C757D;
            case "txt": return 0xFF8D99AE;
            case "json": return 0xFFE9A03B;
            case "jar": return 0xFF8E5CF7;
            case "gradle": return 0xFF1B998B;
            case "properties": return 0xFF6A994E;
            case "html": return 0xFFE34F26;
            case "css": return 0xFF2965F1;
            case "js": return 0xFFC9A400;
            case "kt": return 0xFF7F52FF;
            case "png": case "jpg": case "jpeg": case "gif": case "webp": return 0xFF3A86FF;
            case "zip": return 0xFF9D4EDD;
            case "class": return 0xFF495057;
            case "csv": return 0xFF2D9D5B;
            case "yml": case "yaml": return 0xFFCB4B16;
            default: return 0xFF6B7280;
        }
    }

    @Override public void draw(Canvas c) {
        RectF b = new RectF(getBounds());
        float in = b.height() * 0.08f;
        b.inset(in, in * 2.2f);
        float r = b.height() * 0.22f;
        c.drawRoundRect(b, r, r, bg);
        tx.setTextSize(b.height() * (label.length() > 3 ? 0.36f : label.length() > 2 ? 0.42f : 0.5f));
        float y = b.centerY() - (tx.descent() + tx.ascent()) / 2f;
        c.drawText(label, b.centerX(), y, tx);
    }

    @Override public void setAlpha(int a) { bg.setAlpha(a); tx.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 64; }
    @Override public int getIntrinsicHeight() { return 64; }
}
