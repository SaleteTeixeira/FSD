package common;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Store {

    CompletableFuture<Boolean> put(Map<Long,byte[]> values);

    CompletableFuture<Map<Long,byte[]>> get(Collection<Long> keys);
}
