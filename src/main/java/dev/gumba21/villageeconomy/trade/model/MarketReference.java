package dev.gumba21.villageeconomy.trade.model;

import java.util.Objects;
import java.util.UUID;

public record MarketReference(UUID villageId) {
    public MarketReference {
        Objects.requireNonNull(villageId, "villageId");
    }
}
