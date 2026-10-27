package de.chunkclaim;

/**
 * Was Fremde (weder Besitzer noch Vertraute) in einem Chunk dürfen.
 * Neue Einstellung hinzufügen: hier eintragen, in config.yml unter defaults + flag-names ergänzen.
 */
public enum Flag {
    BREAK("break"),
    PLACE("place"),
    INTERACT("interact");

    private final String id;

    Flag(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Flag parse(String text) {
        for (Flag flag : values()) {
            if (flag.id.equalsIgnoreCase(text)) return flag;
        }
        return null;
    }
}
