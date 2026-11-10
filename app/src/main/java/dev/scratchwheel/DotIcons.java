package dev.scratchwheel;

/** Iconos de matriz de puntos 7x7 (cada fila es una cadena de 0/1). */
public final class DotIcons {
    public static final int N = 7;

    public static final String[] PLAY = {
        "0100000", "0111000", "0111110", "0111111", "0111110", "0111000", "0100000"};
    public static final String[] PAUSE = {
        "0110110", "0110110", "0110110", "0110110", "0110110", "0110110", "0110110"};
    public static final String[] NEXT = {
        "1000010", "1100010", "1110010", "1111010", "1110010", "1100010", "1000010"};
    public static final String[] PREV = {
        "0100001", "0100011", "0100111", "0101111", "0100111", "0100011", "0100001"};
    public static final String[] FFW = {
        "1001000", "1101100", "1110110", "1111011", "1110110", "1101100", "1001000"};
    public static final String[] REW = {
        "0001001", "0011011", "0110111", "1101111", "0110111", "0011011", "0001001"};
    public static final String[] SHUFFLE = {
        "0000110", "1100011", "0110110", "0011100", "0110110", "1100011", "0000110"};
    public static final String[] REPEAT = {
        "0011101", "0100011", "1000001", "1000001", "1000001", "0100010", "0011100"};
    public static final String[] REPEAT_ONE = {
        "0011101", "0100011", "1000001", "1001001", "1000001", "0100010", "0011100"};
    public static final String[] HEART = {
        "0110110", "1001001", "1000001", "1000001", "0100010", "0010100", "0001000"};
    public static final String[] HEART_FILLED = {
        "0110110", "1111111", "1111111", "1111111", "0111110", "0011100", "0001000"};
    public static final String[] QUEUE = {
        "1011111", "0000000", "1011111", "0000000", "1011111", "0000000", "1011111"};
    public static final String[] MENU = {
        "0000000", "0001000", "0000000", "0001000", "0000000", "0001000", "0000000"};
    public static final String[] PITCH = {
        "0100010", "0100010", "1110010", "0100010", "0100111", "0100010", "0100010"};
    public static final String[] MOON = {
        "0011100", "0111000", "1110000", "1110000", "1110000", "0111001", "0011110"};

    public static final String[] LOCK = {
        "0011100", "0100010", "0100010", "1111111", "1110111", "1110111", "1111111"};
    public static final String[] DISC = {
        "0011100", "0100010", "1000001", "1001001", "1000001", "0100010", "0011100"};
    public static final String[] COVER = {
        "1111111", "1000001", "1011001", "1000001", "1000101", "1001011", "1111111"};
    public static final String[] DOWN = {
        "0000000", "0000000", "1100011", "0110110", "0011100", "0001000", "0000000"};
    public static final String[] FOLDER = {
        "0000000", "1110000", "1111111", "1000001", "1000001", "1000001", "1111111"};

    public static final String[] VOL = {
        "0001001", "0011010", "1111101", "1111101", "1111101", "0011010", "0001001"};
    public static final String[] SEARCH = {
        "0111000", "1000100", "1000100", "1000100", "0111010", "0000101", "0000010"};
    public static final String[] PLUS = {
        "0001000", "0001000", "0001000", "1111111", "0001000", "0001000", "0001000"};
    public static final String[] CLOSE = {
        "1000001", "0100010", "0010100", "0001000", "0010100", "0100010", "1000001"};

    private DotIcons() {
    }

    public static boolean valid(String[] icon) {
        if (icon.length != N) {
            return false;
        }
        for (String r : icon) {
            if (r.length() != N || !r.matches("[01]+")) {
                return false;
            }
        }
        return true;
    }
}
