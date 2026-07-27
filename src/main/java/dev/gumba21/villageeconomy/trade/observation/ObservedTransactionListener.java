package dev.gumba21.villageeconomy.trade.observation;

import dev.gumba21.villageeconomy.trade.model.ObservedTransaction;

@FunctionalInterface
public interface ObservedTransactionListener {
    void onTransaction(ObservedTransaction transaction);
}
