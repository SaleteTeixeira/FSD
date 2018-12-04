package common;

import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

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
}
