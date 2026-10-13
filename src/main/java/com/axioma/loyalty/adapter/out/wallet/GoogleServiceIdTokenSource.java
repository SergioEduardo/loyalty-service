package com.axioma.loyalty.adapter.out.wallet;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ID tokens con Application Default Credentials (en Cloud Run, el servidor de metadatos de la cuenta
 * sa-loyalty-service; sin llaves). Formato completo, para que lleve email y email_verified, que el
 * wallet-service exige. Las credenciales se crean al primer uso y renuevan el token antes de que venza.
 */
public class GoogleServiceIdTokenSource implements ServiceIdTokenSource {

    private final Map<String, IdTokenCredentials> byAudience = new ConcurrentHashMap<>();

    @Override
    public String idToken(String audience) throws IOException {
        IdTokenCredentials credentials = byAudience.get(audience);
        if (credentials == null) {
            credentials = create(audience);
            byAudience.putIfAbsent(audience, credentials);
        }
        credentials.refreshIfExpired();
        return credentials.getIdToken().getTokenValue();
    }

    private static IdTokenCredentials create(String audience) throws IOException {
        GoogleCredentials adc = GoogleCredentials.getApplicationDefault();
        if (!(adc instanceof IdTokenProvider provider)) {
            throw new IOException("Las credenciales de ADC no emiten ID tokens: " + adc.getClass().getSimpleName());
        }
        return IdTokenCredentials.newBuilder()
                .setIdTokenProvider(provider)
                .setTargetAudience(audience)
                .setOptions(List.of(IdTokenProvider.Option.FORMAT_FULL))
                .build();
    }
}
