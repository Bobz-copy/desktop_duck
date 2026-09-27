package com.cfks.goosedroid.brain;

import org.junit.Test;

import static org.junit.Assert.*;

public class IntentParserTest {

    @Test
    public void parse_cleanJson_readsAllFields() {
        BrainIntent intent = IntentParser.parse(
                "{\"say\": \"¡Quiero pan!\", \"mood\": \"HUNGRY\", \"action\": \"HONK\", "
                        + "\"remember\": \"A mi humano le gusta el mate\"}");

        assertNotNull(intent);
        assertEquals("¡Quiero pan!", intent.say);
        assertEquals(BrainMood.HUNGRY, intent.mood);
        assertEquals(BrainAction.HONK, intent.action);
        assertEquals("A mi humano le gusta el mate", intent.remember);
    }

    @Test
    public void parse_jsonSurroundedByProse_isExtracted() {
        BrainIntent intent = IntentParser.parse(
                "Claro, acá va:\n{\"say\": \"honk\", \"mood\": \"happy\", \"action\": \"dance\"}\n¡Listo!");

        assertNotNull(intent);
        assertEquals("honk", intent.say);
        assertEquals(BrainMood.HAPPY, intent.mood);
        assertEquals(BrainAction.DANCE, intent.action);
    }

    @Test
    public void parse_jsonInsideCodeFence_isExtracted() {
        BrainIntent intent = IntentParser.parse("```json\n{\"say\": \"hola\"}\n```");

        assertNotNull(intent);
        assertEquals("hola", intent.say);
    }

    @Test
    public void parse_reasoningBlock_isIgnored() {
        BrainIntent intent = IntentParser.parse(
                "<think>el usuario {quiere} algo</think>{\"say\": \"cuac\"}");

        assertNotNull(intent);
        assertEquals("cuac", intent.say);
    }

    @Test
    public void parse_unclosedReasoningBlock_dropsTheRest() {
        assertNull(IntentParser.parse("<think>sigo pensando y nunca termino"));
    }

    @Test
    public void parse_bracesInsideStrings_doNotBreakExtraction() {
        BrainIntent intent = IntentParser.parse("{\"say\": \"mirá esto: } raro {\", \"mood\": \"CURIOUS\"}");

        assertNotNull(intent);
        assertEquals("mirá esto: } raro {", intent.say);
        assertEquals(BrainMood.CURIOUS, intent.mood);
    }

    @Test
    public void parse_escapedQuotesInsideStrings_areHandled() {
        BrainIntent intent = IntentParser.parse("{\"say\": \"dijo \\\"honk\\\" y se fue\"}");

        assertNotNull(intent);
        assertEquals("dijo \"honk\" y se fue", intent.say);
    }

    @Test
    public void parse_unknownActionAndMood_fallBackToDefaults() {
        BrainIntent intent = IntentParser.parse(
                "{\"say\": \"hola\", \"mood\": \"furibundo\", \"action\": \"DELETE_ALL_FILES\"}");

        assertNotNull(intent);
        assertEquals(BrainMood.NEUTRAL, intent.mood);
        assertEquals(BrainAction.NONE, intent.action);
    }

    @Test
    public void parse_actionWithSpacesOrLowercase_isNormalized() {
        assertEquals(BrainAction.PLAY_DEAD,
                IntentParser.parse("{\"action\": \"play dead\"}").action);
        assertEquals(BrainAction.LOOK_AROUND,
                IntentParser.parse("{\"action\": \"look-around\"}").action);
    }

    @Test
    public void parse_nullRemember_countsAsEmpty() {
        BrainIntent intent = IntentParser.parse("{\"say\": \"hola\", \"remember\": null}");

        assertNotNull(intent);
        assertFalse(intent.hasMemory());
    }

    @Test
    public void parse_nonStringValues_countAsEmpty() {
        BrainIntent intent = IntentParser.parse("{\"say\": 42, \"action\": \"SPIN\"}");

        assertNotNull(intent);
        assertEquals("", intent.say);
        assertEquals(BrainAction.SPIN, intent.action);
    }

    @Test
    public void parse_plainSentence_becomesSpeech() {
        BrainIntent intent = IntentParser.parse("  \"Tengo sueño\"  ");

        assertNotNull(intent);
        assertEquals("Tengo sueño", intent.say);
        assertEquals(BrainAction.NONE, intent.action);
    }

    @Test
    public void parse_brokenJson_isRejected() {
        assertNull(IntentParser.parse("{\"say\": \"hola"));
    }

    @Test
    public void parse_emptyObject_isRejected() {
        assertNull(IntentParser.parse("{}"));
    }

    @Test
    public void parse_nullOrBlank_isRejected() {
        assertNull(IntentParser.parse(null));
        assertNull(IntentParser.parse("   \n "));
    }

    @Test
    public void parse_longSpeech_isTruncatedAtWordBoundary() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 40; i++) longText.append("palabra ");
        BrainIntent intent = IntentParser.parse("{\"say\": \"" + longText + "\"}");

        assertNotNull(intent);
        assertTrue(intent.say.length() <= BrainIntent.MAX_SAY_LENGTH);
        assertTrue(intent.say.endsWith("…"));
        assertFalse(intent.say.contains("  "));
    }

    @Test
    public void parse_customMaxLength_allowsLongerDiaryText() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 25; i++) longText.append("palabra ");
        String text = longText.toString().trim();

        BrainIntent intent = IntentParser.parse("{\"say\": \"" + text + "\"}", 300);

        assertNotNull(intent);
        assertEquals(text, intent.say);
    }

    @Test
    public void parse_multilineSpeech_isCollapsedToOneLine() {
        BrainIntent intent = IntentParser.parse("{\"say\": \"hola\\n\\n  mundo\"}");

        assertNotNull(intent);
        assertEquals("hola mundo", intent.say);
    }
}
