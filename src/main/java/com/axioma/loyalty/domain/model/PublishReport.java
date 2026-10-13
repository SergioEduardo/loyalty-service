package com.axioma.loyalty.domain.model;

/**
 * Resultado de una ejecución de la publicación de avisos: cuántos reclamó, cuántos confirmó Pub/Sub
 * y cuántos fallaron (quedan pendientes con espera, o FAILED). disabled: sin tópico configurado.
 */
public record PublishReport(int claimed, int published, int failed, boolean disabled) {

    public static final PublishReport DISABLED = new PublishReport(0, 0, 0, true);

    public static PublishReport of(int published, int failed) {
        return new PublishReport(published + failed, published, failed, false);
    }
}
