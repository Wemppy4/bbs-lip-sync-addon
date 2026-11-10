package com.wemppy.bbs_lip_sync.client.phonemes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wemppy.bbs_lip_sync.client.Bundled;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Letter → {@link Viseme} for one language, from {@code letters/<id>.json}: a spelled word is
 * read letter by letter and each letter names the mouth shape. A table may {@code extend} another
 * (the Latin and Cyrillic bases) and the user's {@code config/bbs/lipsync/letters/<id>.json}
 * replaces the bundled one.
 *
 * <p>A letter that isn't listed is looked up without its diacritics (é → e, ř → r), so a base
 * table covers most of an alphabet's accented variants. Letters still unknown after that keep the
 * mouth in the previous shape.</p>
 *
 * <p>Letters also carry a rough duration weight — vowels long, sonorants a bit shorter, stops
 * short — which is how a word's time is shared between its letters.</p>
 */
public class LetterMap
{
    public static final float WEIGHT_VOWEL = 1.5F;
    public static final float WEIGHT_SONORANT = 1.2F;
    public static final float WEIGHT_STOP = 0.5F;
    public static final float WEIGHT_DEFAULT = 1F;

    private static final String VOWELS = "aeiouyáàâãäåæéèêëíìîïóòôõöøœúùûüýÿıąęėįūōəаеёиоуыэюяєіїўәөұүӣӯაეიოუ";
    private static final String SONORANTS = "lrmnwłñйлрнмლრმნ";
    private static final String STOPS = "pbtdkgqпбтдкгқғბპდთტგკქ";

    /** How many {@code extends} hops are followed before giving up (a cycle, most likely). */
    private static final int MAX_DEPTH = 8;

    private final Map<Integer, Viseme> letters = new HashMap<>();
    private final Map<Integer, String> digits = new HashMap<>();
    private final Set<Integer> vowels = new HashSet<>();
    private final Set<Integer> sonorants = new HashSet<>();
    private final Set<Integer> stops = new HashSet<>();

    private LetterMap()
    {
        VOWELS.codePoints().forEach(this.vowels::add);
        SONORANTS.codePoints().forEach(this.sonorants::add);
        STOPS.codePoints().forEach(this.stops::add);
    }

    /** The table for a letters id, bundled or overridden by the user. */
    public static LetterMap load(String id) throws IOException
    {
        return fromJson(id, LetterMap::source);
    }

    /**
     * @param source resolves a table id to its JSON text (used again for whatever it extends)
     */
    public static LetterMap fromJson(String id, Function<String, String> source) throws IOException
    {
        LetterMap map = new LetterMap();

        map.read(id, source, 0);

        return map;
    }

    private static String source(String id)
    {
        File override = new File(Bundled.folder(), "letters/" + id + ".json");

        if (override.isFile())
        {
            try
            {
                return IOUtils.readText(override);
            }
            catch (Exception e)
            {
                System.err.println("[bbs_lip_sync] Failed to read " + override + ", using the bundled table");
                e.printStackTrace();
            }
        }

        try
        {
            return Bundled.read("letters/" + id + ".json");
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private void read(String id, Function<String, String> source, int depth) throws IOException
    {
        String json = source.apply(id);

        if (json == null)
        {
            throw new IOException("No letter table \"" + id + "\"");
        }

        JsonObject object = JsonParser.parseString(json).getAsJsonObject();

        if (object.has("extends") && depth < MAX_DEPTH)
        {
            this.read(object.get("extends").getAsString(), source, depth + 1);
        }

        if (object.has("letters"))
        {
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("letters").entrySet())
            {
                Viseme viseme = Viseme.byId(entry.getValue().getAsString());
                String key = entry.getKey().toLowerCase();

                if (viseme != null && !key.isEmpty())
                {
                    this.letters.put(key.codePointAt(0), viseme);
                }
            }
        }

        if (object.has("digits"))
        {
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("digits").entrySet())
            {
                if (!entry.getKey().isEmpty())
                {
                    this.digits.put(entry.getKey().codePointAt(0), entry.getValue().getAsString().toLowerCase());
                }
            }
        }

        this.addAll(object, "vowels", this.vowels);
        this.addAll(object, "sonorants", this.sonorants);
        this.addAll(object, "stops", this.stops);
    }

    private void addAll(JsonObject object, String key, Set<Integer> set)
    {
        if (object.has(key))
        {
            object.get(key).getAsString().toLowerCase().codePoints().forEach(set::add);
        }
    }

    /** The mouth shape of a letter, or null if the table doesn't know it. */
    public Viseme viseme(int letter)
    {
        Viseme viseme = this.letters.get(letter);

        if (viseme == null)
        {
            int base = strip(letter);

            if (base != letter)
            {
                viseme = this.letters.get(base);
            }
        }

        return viseme;
    }

    /** Whether the letter is a vowel, the core of a syllable. */
    public boolean isVowel(int letter)
    {
        return this.vowels.contains(letter) || this.vowels.contains(strip(letter));
    }

    /** How long the letter tends to sound, relative to other letters. */
    public float weight(int letter)
    {
        int base = strip(letter);

        if (this.vowels.contains(letter) || this.vowels.contains(base))
        {
            return WEIGHT_VOWEL;
        }

        if (this.sonorants.contains(letter) || this.sonorants.contains(base))
        {
            return WEIGHT_SONORANT;
        }

        if (this.stops.contains(letter) || this.stops.contains(base))
        {
            return WEIGHT_STOP;
        }

        return WEIGHT_DEFAULT;
    }

    /**
     * A recognized word as the letters to animate: lower case, digits spelled out, everything that
     * isn't a letter dropped. A word Vosk couldn't make out ({@code [unk]}) becomes one open vowel.
     */
    public int[] letters(String word)
    {
        if (word.startsWith("["))
        {
            return new int[] {'a'};
        }

        StringBuilder builder = new StringBuilder();

        word.toLowerCase().codePoints().forEach((point) ->
        {
            String spelled = this.digits.get(point);

            if (spelled != null)
            {
                spelled.codePoints().filter(Character::isLetter).forEach(builder::appendCodePoint);
            }
            else if (Character.isLetter(point))
            {
                builder.appendCodePoint(point);
            }
        });

        return builder.codePoints().toArray();
    }

    /** The letter without its diacritics: é → e, ř → r; the letter itself when it has none. */
    static int strip(int letter)
    {
        String text = new String(Character.toChars(letter));
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);

        for (int i = 0; i < decomposed.length(); )
        {
            int point = decomposed.codePointAt(i);

            if (Character.getType(point) != Character.NON_SPACING_MARK && Character.getType(point) != Character.COMBINING_SPACING_MARK && Character.getType(point) != Character.ENCLOSING_MARK)
            {
                return point;
            }

            i += Character.charCount(point);
        }

        return letter;
    }
}
