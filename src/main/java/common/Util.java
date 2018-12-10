package common;

import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.Map;

public class Util {
    public static Address getCoordinator() {
        return Address.from("localhost:11110");
    }

    public static Serializer getSerializer() {
        return Serializer.builder()
                .withTypes(GetRequest.class)
                .withTypes(GetReply.class)
                .withTypes(PutRequest.class)
                .withTypes(PutReply.class)
                .build();
    }

    public static String valuesToString(final Map<Long, byte[]> values) {
        final StringBuilder s = new StringBuilder();

        for (final Map.Entry<Long, byte[]> a : values.entrySet()) {
            s.append(a.getKey());
            String b = "null";
            if (a.getValue() != null) {
                b = new String(a.getValue());
            }
            s.append(" ").append(b).append(", ");
        }

        return s.toString();
    }
}
