package android.graphics;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.OutputStream;

public final class Bitmap {
    public enum Config {
        ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE
    }

    public enum CompressFormat {
        JPEG, PNG, WEBP
    }

    public final BufferedImage img;
    private boolean recycled;

    public Bitmap(BufferedImage i) {
        img = i;
    }

    /** Convierte cualquier imagen a ARGB para que pintarla sea rápido. */
    public static Bitmap wrap(java.awt.Image src) {
        if (src == null) {
            return null;
        }
        if (src instanceof BufferedImage && ((BufferedImage) src).getType() == BufferedImage.TYPE_INT_ARGB) {
            return new Bitmap((BufferedImage) src);
        }
        int w = src.getWidth(null);
        int h = src.getHeight(null);
        if (w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return new Bitmap(o);
    }

    public static Bitmap createBitmap(int w, int h, Config c) {
        return new Bitmap(new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB));
    }

    public static Bitmap createBitmap(Bitmap src) {
        return createBitmap(src, 0, 0, src.getWidth(), src.getHeight());
    }

    public static Bitmap createBitmap(Bitmap src, int x, int y, int w, int h) {
        if (x == 0 && y == 0 && w == src.getWidth() && h == src.getHeight()) {
            return src;
        }
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(src.img, -x, -y, null);
        g.dispose();
        return new Bitmap(o);
    }

    public static Bitmap createBitmap(int[] colors, int w, int h, Config c) {
        Bitmap b = createBitmap(w, h, c);
        b.img.setRGB(0, 0, w, h, colors, 0, w);
        return b;
    }

    public static Bitmap createScaledBitmap(Bitmap src, int w, int h, boolean filter) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        BufferedImage cur = src.img;
        // Reducir por mitades da mejor calidad que un único salto bilineal.
        while (filter && (cur.getWidth() >= 2 * w && cur.getHeight() >= 2 * h)) {
            cur = scale(cur, Math.max(w, cur.getWidth() / 2), Math.max(h, cur.getHeight() / 2), true);
        }
        if (cur.getWidth() != w || cur.getHeight() != h || cur == src.img) {
            cur = scale(cur, w, h, filter);
        }
        return new Bitmap(cur);
    }

    private static BufferedImage scale(BufferedImage s, int w, int h, boolean filter) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, filter
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(s, 0, 0, w, h, null);
        g.dispose();
        return o;
    }

    public int getWidth() {
        return img.getWidth();
    }

    public int getHeight() {
        return img.getHeight();
    }

    public int getByteCount() {
        return img.getWidth() * img.getHeight() * 4;
    }

    public int getAllocationByteCount() {
        return getByteCount();
    }

    public Config getConfig() {
        return Config.ARGB_8888;
    }

    public boolean hasAlpha() {
        return true;
    }

    public boolean isMutable() {
        return true;
    }

    public int getPixel(int x, int y) {
        return img.getRGB(x, y);
    }

    public void setPixel(int x, int y, int c) {
        img.setRGB(x, y, c);
    }

    public void getPixels(int[] px, int off, int stride, int x, int y, int w, int h) {
        img.getRGB(x, y, w, h, px, off, stride);
    }

    public void setPixels(int[] px, int off, int stride, int x, int y, int w, int h) {
        img.setRGB(x, y, w, h, px, off, stride);
    }

    public void eraseColor(int c) {
        Graphics2D g = img.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.setColor(new java.awt.Color(c, true));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
    }

    public Bitmap copy(Config c, boolean mutable) {
        BufferedImage o = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return new Bitmap(o);
    }

    public boolean compress(CompressFormat f, int quality, OutputStream out) {
        try {
            if (f == CompressFormat.PNG) {
                return javax.imageio.ImageIO.write(img, "png", out);
            }
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(java.awt.Color.BLACK);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
            javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(Math.max(0.05f, Math.min(1f, quality / 100f)));
            javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(out);
            try {
                w.setOutput(ios);
                w.write(null, new javax.imageio.IIOImage(rgb, null, null), p);
            } finally {
                w.dispose();
                ios.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void recycle() {
        recycled = true;
    }

    public boolean isRecycled() {
        return recycled;
    }

    public void prepareToDraw() {
    }

    public void setHasAlpha(boolean b) {
    }
}
