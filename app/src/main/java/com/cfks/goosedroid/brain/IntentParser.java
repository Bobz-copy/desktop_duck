package com.cfks.goosedroid.brain;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Convierte el texto de un modelo en una intención validada.
 *
 * Los modelos chicos rodean el JSON con texto, lo envuelven en bloques de
 * código o directamente contestan en prosa: nada de eso debe romper al ganso.
 */
public final class IntentParser {
    private static final String ELLIPSIS = "…";
    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";
    private static final String CODE_FENCE = "```";

    private IntentParser() {
    }

    /**
     * @return la intención, o null si el texto no contiene nada utilizable
     */
    public static BrainIntent parse(String modelOutput) {
        return parse(modelOutput, BrainIntent.MAX_SAY_LENGTH);
    }

    /**
     * @param maxSayLength tope de caracteres de la frase (el diario admite más)
     * @return la intención, o null si el texto no contiene nada utilizable
     */
    public static BrainIntent parse(String modelOutput, int maxSayLength) {
        if (modelOutput == null) return null;
        String text = stripReasoning(modelOutput).trim();
        if (text.isEmpty()) return null;

        String json = extractFirstJsonObject(text);
        if (json != null) {
            BrainIntent intent = parseJson(json, maxSayLength);
            if (intent != null) return intent;
        }

        // Sin JSON válido: tratar la respuesta como una frase suelta
        String speech = cleanSpeech(stripCodeFence(text), maxSayLength);
        if (speech.isEmpty() || speech.indexOf('{') >= 0) return null;
        return BrainIntent.ofSpeech(speech);
    }

    private static BrainIntent parseJson(String json, int maxSayLength) {
        try {
            JSONObject object = new JSONObject(json);
            BrainIntent intent = new BrainIntent(
                    cleanSpeech(optStringOrEmpty(object, "say"), maxSayLength),
                    BrainMood.fromModelText(optStringOrEmpty(object, "mood")),
                    BrainAction.fromModelText(optStringOrEmpty(object, "action")),
                    cleanSpeech(optStringOrEmpty(object, "remember"),
                            BrainIntent.MAX_REMEMBER_LENGTH));
            return intent.isEmpty() ? null : intent;
        } catch (JSONException e) {
            return null;
        }
    }

    /** Un valor null o no textual cuenta como vacío. */
    private static String optStringOrEmpty(JSONObject object, String key) {
        if (object.isNull(key)) return "";
        Object value = object.opt(key);
        return value instanceof String ? (String) value : "";
    }

    /** Algunos modelos razonan entre etiquetas antes de contestar. */
    static String stripReasoning(String text) {
        String result = text;
        int start = result.indexOf(THINK_OPEN);
        while (start >= 0) {
            int end = result.indexOf(THINK_CLOSE, start);
            if (end < 0) {
                return result.substring(0, start);
            }
            result = result.substring(0, start) + result.substring(end + THINK_CLOSE.length());
            start = result.indexOf(THINK_OPEN);
        }
        return result;
    }

    private static String stripCodeFence(String text) {
        String result = text.trim();
        if (!result.startsWith(CODE_FENCE)) return result;

        int firstNewline = result.indexOf('\n');
        result = firstNewline >= 0 ? result.substring(firstNewline + 1) : "";
        int closing = result.lastIndexOf(CODE_FENCE);
        if (closing >= 0) result = result.substring(0, closing);
        return result.trim();
    }

    /**
     * Devuelve el primer objeto JSON balanceado del texto, respetando llaves
     * dentro de cadenas.
     */
    static String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        while (start >= 0) {
            int end = findMatchingBrace(text, start);
            if (end > start) return text.substring(start, end + 1);
            start = text.indexOf('{', start + 1);
        }
        return null;
    }

    private static int findMatchingBrace(String text, int start) {
        int depth = 0;
        boolean isInString = false;
        boolean isEscaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isEscaped) {
                isEscaped = false;
            } else if (c == '\\') {
                isEscaped = isInString;
            } else if (c == '"') {
                isInString = !isInString;
            } else if (!isInString && c == '{') {
                depth++;
            } else if (!isInString && c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Una sola línea, sin comillas envolventes, con largo acotado. */
    static String cleanSpeech(String raw, int maxLength) {
        if (raw == null) return "";
        String text = raw.replaceAll("\\s+", " ").trim();
        while (text.length() >= 2 && isQuote(text.charAt(0))
                && isQuote(text.charAt(text.length() - 1))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        if (text.length() <= maxLength) return text;

        String cut = text.substring(0, maxLength - 1);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > maxLength / 2) cut = cut.substring(0, lastSpace);
        return cut.trim() + ELLIPSIS;
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\'' || c == '“' || c == '”' || c == '`';
    }
}
