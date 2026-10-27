package de.ancientreminder;

/** Bewertungsstufen eines Ancient-Spots. */
public enum RatingLevel {
    VERY_GOOD("Sehr gut", 0x55FF55),
    GOOD("Gut", 0xAAFF55),
    OK("Ok", 0xFFFF55),
    BAD("Schlecht", 0xFFAA00),
    GONE("Weg", 0xFF5555);

    public final String label;
    public final int color;

    RatingLevel(String label, int color) {
        this.label = label;
        this.color = color;
    }
}
