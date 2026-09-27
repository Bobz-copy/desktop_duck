package com.cfks.goosedroid.brain.backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Lector mínimo de Server-Sent Events: entrega el contenido de cada línea
 * "data:" y se detiene cuando el consumidor lo pide o el flujo termina.
 */
final class SseReader {
    private static final String DATA_PREFIX = "data:";

    interface DataHandler {
        /**
         * @return false para dejar de leer
         */
        boolean onData(String data) throws IOException;
    }

    private SseReader() {
    }

    static void read(InputStream stream, DataHandler handler) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith(DATA_PREFIX)) continue;
            String data = line.substring(DATA_PREFIX.length()).trim();
            if (data.isEmpty()) continue;
            if (!handler.onData(data)) return;
        }
    }
}
