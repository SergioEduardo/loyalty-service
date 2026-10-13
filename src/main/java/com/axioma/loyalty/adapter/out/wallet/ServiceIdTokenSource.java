package com.axioma.loyalty.adapter.out.wallet;

import java.io.IOException;

/** ID token de Google de la cuenta de servicio de runtime, para una audiencia. Bloqueante. */
public interface ServiceIdTokenSource {

    String idToken(String audience) throws IOException;
}
