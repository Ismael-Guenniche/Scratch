package android.util;

public final class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;

    private Base64() {
    }

    public static String encodeToString(byte[] in, int flags) {
        java.util.Base64.Encoder e = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlEncoder()
                : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) {
            e = e.withoutPadding();
        }
        String s = e.encodeToString(in);
        return (flags & NO_WRAP) != 0 ? s : s + "\n";
    }

    public static byte[] encode(byte[] in, int flags) {
        return encodeToString(in, flags).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    public static byte[] decode(String s, int flags) {
        String t = s.replace("\n", "").replace("\r", "").trim();
        if ((flags & URL_SAFE) != 0) {
            return java.util.Base64.getUrlDecoder().decode(t);
        }
        return java.util.Base64.getMimeDecoder().decode(t);
    }

    public static byte[] decode(byte[] b, int flags) {
        return decode(new String(b, java.nio.charset.StandardCharsets.US_ASCII), flags);
    }
}
