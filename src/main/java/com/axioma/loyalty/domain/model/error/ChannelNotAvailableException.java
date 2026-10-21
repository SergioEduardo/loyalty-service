package com.axioma.loyalty.domain.model.error;

import com.axioma.loyalty.domain.model.VerificationChannel;
import java.util.List;

/**
 * CHANNEL_NOT_AVAILABLE: el canal pedido es desconocido o no está habilitado (sin distinguir). Lleva solo los
 * canales habilitados, nunca el pedido.
 */
public class ChannelNotAvailableException extends LoyaltyException implements ChannelAlternatives {

    private final List<VerificationChannel> availableChannels;

    public ChannelNotAvailableException(List<VerificationChannel> availableChannels) {
        super(ErrorCode.CHANNEL_NOT_AVAILABLE);
        this.availableChannels = List.copyOf(availableChannels);
    }

    @Override
    public VerificationChannel getAttemptedChannel() {
        return null;
    }

    @Override
    public List<VerificationChannel> getAvailableChannels() {
        return availableChannels;
    }
}
