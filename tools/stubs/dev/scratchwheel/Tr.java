package dev.scratchwheel;

/** Sustituto de Tr solo para los tests en la JVM (el real depende de Android): devuelve el texto tal cual. */
final class Tr {
    private Tr() {
    }

    static String t(String s) {
        return s;
    }
}
