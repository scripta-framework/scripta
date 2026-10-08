package dev.scriptaframework.core.bankaccount;

import java.util.Objects;
import java.util.UUID;

/** Identifies a bank account. */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId random() {
        return new AccountId(UUID.randomUUID());
    }
}
