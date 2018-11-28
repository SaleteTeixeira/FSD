package common;

import io.atomix.utils.serializer.Serializer;

public class Util {
    public static Serializer getSerializer() {
        return Serializer.builder()
                .withTypes(GetRequest.class)
                .withTypes(GetReply.class)
                .withTypes(PutRequest.class)
                .withTypes(PutReply.class)
                .build();
    }
}
