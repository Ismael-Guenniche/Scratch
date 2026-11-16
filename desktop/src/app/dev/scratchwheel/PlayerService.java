package dev.scratchwheel;

/**
 * En el escritorio no hace falta un servicio en primer plano ni Android Auto: el proceso vive mientras
 * la ventana esté abierta. Solo queda lo que Player consulta.
 */
final class PlayerService {
    static volatile boolean active;

    private PlayerService() {
    }
}
