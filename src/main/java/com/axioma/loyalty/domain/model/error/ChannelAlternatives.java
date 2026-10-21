package com.axioma.loyalty.domain.model.error;

import com.axioma.loyalty.domain.model.VerificationChannel;
import java.util.List;

/**
 * Error del envío del código que la persona puede resolver con otro canal: la respuesta agrega
 * availableChannels (los habilitados, en el orden configurado) y, si hubo intento, channel.
 */
public interface ChannelAlternatives {

    /** El canal por el que se intentó enviar, o null si no hubo intento (CHANNEL_NOT_AVAILABLE). */
    VerificationChannel getAttemptedChannel();

    /** Los canales habilitados; null si el error no los lleva (la respuesta no agrega campos). */
    List<VerificationChannel> getAvailableChannels();
}
