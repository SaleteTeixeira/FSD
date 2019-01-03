package client;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class Client {
    private final static Random random = new Random();
    private final static String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static Collection<Long> genKeys(final int numKeys, final int upperBound) {
        final Collection<Long> keys = new ArrayList<>();
        for (int j = 0; j < numKeys; j++) {
            keys.add((long) random.nextInt(upperBound));
        }
        return keys;
    }

    private static byte[] genBytes(final int length) {
        final StringBuilder stringBuilder = new StringBuilder(length);
        for (int j = 0; j < length; j++) {
            stringBuilder.append(charSet.charAt(random.nextInt(charSet.length())));
        }
        return stringBuilder.toString().getBytes();
    }

    private static void printGetResponse(final Map<Long, byte[]> values) {
        System.out.print("Resposta a um get: ");
        values.forEach((k, v) -> {
            System.out.print(k);
            if (v != null) {
                System.out.print("=" + new String(v));
            } else {
                System.out.print("=null");
            }
            System.out.print(' ');
        });
        System.out.println();
    }

    private static void printGetRequest(final Collection<Long> keys) {
        System.out.print("A enviar um get: ");
        keys.forEach(k -> {
            System.out.print(k);
            System.out.print(' ');
        });
        System.out.println();
    }

    private static void printPutRequest(final Map<Long, byte[]> values) {
        System.out.print("A enviar um put: ");
        values.forEach((k, v) -> {
            System.out.print(k);
            System.out.print('=' + new String(v) + ' ');
        });
        System.out.println();
    }

    public static void main(final String[] args) {
        final int numOps = 5; // Total number of gets + puts
        final int maxNumKeys = 20; // Maximum number of keys in a single request
        //final int keyUpperBound = Integer.MAX_VALUE; // Upper bound of a given key
        final int keyUpperBound = 10; // Upper bound of a given key
        final int valueLength = 20; // Length of each byte[] in put request

        final Store store = new Store();
        final Random random = new Random();

        for (int i = 0; i < numOps; i++) {
            final int op = random.nextInt(2);
            final int numKeys = random.nextInt(maxNumKeys + 1);
            final Collection<Long> keys = genKeys(numKeys, keyUpperBound);

            switch (op) {
                case 0: // Get
                    printGetRequest(keys);

                    if (Boolean.parseBoolean(System.getProperty("blocking"))) {
                        try {
                            printGetResponse(store.get(keys).get());
                        } catch (final InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                    } else {
                        store.get(keys).thenAccept(Client::printGetResponse);
                    }
                    break;

                case 1: // Put
                    final Map<Long, byte[]> values = new HashMap<>();
                    keys.forEach(k -> values.put(k, genBytes(valueLength)));

                    printPutRequest(values);

                    if (Boolean.parseBoolean(System.getProperty("blocking"))) {
                        try {
                            System.out.println("Resposta a um put: " + store.put(values).get());
                        } catch (final InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                    } else {
                        store.put(values).thenAccept((bool) -> {
                            System.out.println("Resposta a um put: " + bool);
                        });
                    }
                    break;

                default:
                    System.out.println("Ups");
                    System.exit(1);
                    break;
            }
        }
    }
}
