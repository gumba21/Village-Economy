package dev.gumba21.villageeconomy.village.lifecycle;

import java.util.UUID;

public record VillageLoadReconciliation(
        UUID villageId,
        boolean previousLoaded,
        boolean newLoaded,
        boolean changed,
        boolean rejectedUnload,
        String reason,
        VillageLoadEvidence evidence
) {
}
