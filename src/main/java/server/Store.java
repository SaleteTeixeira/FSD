package server;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class Store {

    private final Map<Long, byte[]> data;
    private final Map<Long, Integer> clock;

    Store() {
        this.data = new HashMap<>();
        this.clock = new HashMap<>();
    }

    void put(final Map<Long, byte[]> values, final int transactionID) {
        values.forEach((k, v) -> {
            if (!(this.clock.containsKey(k) && this.clock.get(k) >= transactionID)) {
                this.data.put(k, v);
                this.clock.put(k, transactionID);
            }
        });
    }

    Map<Long, byte[]> get(final Collection<Long> keys) {
        final Map<Long, byte[]> temp = new HashMap<>();
        keys.forEach(k -> {
            final byte[] v = this.data.get(k);
            temp.put(k, v);
        });

        return temp;
    }
}
