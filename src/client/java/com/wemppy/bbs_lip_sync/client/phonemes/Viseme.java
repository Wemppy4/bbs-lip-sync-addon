package com.wemppy.bbs_lip_sync.client.phonemes;

import mchorse.bbs_mod.utils.colors.Color;

/**
 * The mouth shapes the addon animates. The ids double as the pose names looked up in a model's
 * {@code poses.json} (the set the bundled {@code player/mouth} rig ships), and each shape owns a
 * colour: generated keyframes are painted with it, which is also how a later run tells its own
 * keyframes from the user's.
 */
public enum Viseme
{
    IDLE("idle", 0x8A94A6),
    B("b", 0xFF5C7A),
    D("d", 0xFFB547),
    EE("ee", 0xF2DA4B),
    A("a", 0x4CD07D),
    O("o", 0x3FC1E0),
    U("u", 0x4F8DFF),
    F("f", 0xB77CFF),
    L("l", 0xFF7AD9);

    /** What a rig without the shape falls back to, nearest first; ends at IDLE which is always available. */
    private static final Viseme[][] FALLBACKS = new Viseme[values().length][];

    static
    {
        FALLBACKS[IDLE.ordinal()] = new Viseme[] {};
        FALLBACKS[B.ordinal()] = new Viseme[] {IDLE};
        FALLBACKS[D.ordinal()] = new Viseme[] {EE, A};
        FALLBACKS[EE.ordinal()] = new Viseme[] {D, A};
        FALLBACKS[A.ordinal()] = new Viseme[] {O, EE};
        FALLBACKS[O.ordinal()] = new Viseme[] {U, A};
        FALLBACKS[U.ordinal()] = new Viseme[] {O, A};
        FALLBACKS[F.ordinal()] = new Viseme[] {D, B};
        FALLBACKS[L.ordinal()] = new Viseme[] {A, D};
    }

    public final String id;
    public final int rgb;

    Viseme(String id, int rgb)
    {
        this.id = id;
        this.rgb = rgb;
    }

    public Viseme[] fallbacks()
    {
        return FALLBACKS[this.ordinal()];
    }

    /** A bilabial/labiodental closure: brief by nature, yet the most visible cue of all. */
    public boolean isClosure()
    {
        return this == B || this == F;
    }

    /** Teeth and tongue consonants: barely visible, they yield to the vowel next to them when short. */
    public boolean isWeak()
    {
        return this == D || this == L;
    }

    public static Viseme byId(String id)
    {
        for (Viseme viseme : values())
        {
            if (viseme.id.equalsIgnoreCase(id))
            {
                return viseme;
            }
        }

        return null;
    }

    /**
     * Whether a keyframe colour is one of ours. BBS stores colours as floats, so an int survives a
     * save/load round trip only to within a unit per channel; compare with a tolerance.
     */
    public static boolean isOwnColor(Color color)
    {
        return byColor(color) != null;
    }

    /** The shape a keyframe was painted for, or null if the colour isn't one of ours. */
    public static Viseme byColor(Color color)
    {
        if (color == null)
        {
            return null;
        }

        int rgb = color.getRGBColor();

        for (Viseme viseme : values())
        {
            if (close(rgb, viseme.rgb))
            {
                return viseme;
            }
        }

        return null;
    }

    private static boolean close(int a, int b)
    {
        return Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff)) <= 3
            && Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff)) <= 3
            && Math.abs((a & 0xff) - (b & 0xff)) <= 3;
    }
}
