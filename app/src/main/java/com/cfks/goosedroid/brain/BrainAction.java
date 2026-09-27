package com.cfks.goosedroid.brain;

import java.util.Locale;

/**
 * Lo único que el modelo puede pedirle al ganso que haga. Cada valor corresponde
 * a algo que el juego ya sabe ejecutar.
 */
public enum BrainAction {
    NONE("no hacer nada especial"),
    WANDER("pasear"),
    NAP("dormir una siesta"),
    ZOOMIES("correr como loco"),
    DANCE("bailar"),
    SPIN("girar"),
    SING("cantar"),
    HONK("graznar"),
    STRETCH("estirarse"),
    LOOK_AROUND("mirar alrededor"),
    PLAY_DEAD("hacerse el muerto"),
    MOONWALK("caminar hacia atrás"),
    TRACK_MUD("ensuciar la pantalla con barro"),
    SEEK_ATTENTION("ir al centro a pedir atención"),
    SULK("ponerse triste"),
    CELEBRATE("festejar");

    private final String description;

    BrainAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Acepta mayúsculas, minúsculas, espacios y guiones. Desconocido = NONE. */
    public static BrainAction fromModelText(String text) {
        if (text == null) return NONE;
        String normalized = text.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_').replace('-', '_');
        for (BrainAction action : values()) {
            if (action.name().equals(normalized)) return action;
        }
        return NONE;
    }
}
