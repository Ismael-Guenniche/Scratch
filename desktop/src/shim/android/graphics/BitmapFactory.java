package android.graphics;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize;
        public int outWidth = -1;
        public int outHeight = -1;
        public String outMimeType;
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
        public boolean inMutable;
        public boolean inScaled;
        public int inDensity;
        public int inTargetDensity;
    }

    static {
        ImageIO.setUseCache(false);
    }

    private static Bitmap read(Object input, Options o) {
        ImageInputStream iis = null;
        try {
            iis = ImageIO.createImageInputStream(input);
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) {
                return null;
            }
            ImageReader r = it.next();
            try {
                r.setInput(iis, true, true);
                int w = r.getWidth(0);
                int h = r.getHeight(0);
                int ss = o == null ? 1 : Math.max(1, o.inSampleSize);
                if (o != null) {
                    o.outWidth = w;
                    o.outHeight = h;
                    if (o.inJustDecodeBounds) {
                        return null;
                    }
                    o.outWidth = (w + ss - 1) / ss;
                    o.outHeight = (h + ss - 1) / ss;
                }
                ImageReadParam p = r.getDefaultReadParam();
                if (ss > 1) {
                    p.setSourceSubsampling(ss, ss, 0, 0);
                }
                BufferedImage img = r.read(0, p);
                return Bitmap.wrap(img);
            } finally {
                r.dispose();
            }
        } catch (Throwable e) {
            return null;
        } finally {
            if (iis != null) {
                try {
                    iis.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static Bitmap decodeByteArray(byte[] data, int off, int len) {
        return decodeByteArray(data, off, len, null);
    }

    public static Bitmap decodeByteArray(byte[] data, int off, int len, Options o) {
        if (data == null) {
            return null;
        }
        return read(new ByteArrayInputStream(data, off, len), o);
    }

    public static Bitmap decodeStream(InputStream in) {
        return decodeStream(in, null, null);
    }

    public static Bitmap decodeStream(InputStream in, Rect pad, Options o) {
        if (in == null) {
            return null;
        }
        return read(in, o);
    }

    public static Bitmap decodeFile(String path) {
        return decodeFile(path, null);
    }

    public static Bitmap decodeFile(String path, Options o) {
        File f = new File(path);
        if (!f.isFile()) {
            return null;
        }
        return read(f, o);
    }

    public static Bitmap decodeResource(android.content.res.Resources res, int id) {
        return res.bitmap(id);
    }

    public static Bitmap decodeResource(android.content.res.Resources res, int id, Options o) {
        Bitmap b = res.bitmap(id);
        if (o != null && b != null) {
            o.outWidth = b.getWidth();
            o.outHeight = b.getHeight();
        }
        return b;
    }
}
