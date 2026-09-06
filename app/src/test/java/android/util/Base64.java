package android.util;

/** JVM bridge for apksig's Base64.NO_WRAP calls. The signature verifier itself is not mocked. */
public final class Base64 {
    public static byte[] decode(String value, int flags) {
        if (flags != 2) throw new UnsupportedOperationException("Only Base64.NO_WRAP is tested");
        return java.util.Base64.getMimeDecoder().decode(value);
    }

    public static String encodeToString(byte[] value, int flags) {
        if (flags != 2) throw new UnsupportedOperationException("Only Base64.NO_WRAP is tested");
        return java.util.Base64.getEncoder().encodeToString(value);
    }
}
