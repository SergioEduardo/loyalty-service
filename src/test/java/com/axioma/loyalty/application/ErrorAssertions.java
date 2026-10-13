package com.axioma.loyalty.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.function.Consumer;

public final class ErrorAssertions {

    private ErrorAssertions() {
    }

    public static Consumer<Throwable> hasCode(ErrorCode code) {
        return error -> {
            assertThat(error).isInstanceOf(LoyaltyException.class);
            assertThat(((LoyaltyException) error).getCode()).isEqualTo(code);
        };
    }
}
