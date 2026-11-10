package com.wemppy.bbs_lip_sync.client.phonemes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wemppy.bbs_lip_sync.client.model.Word;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * What was said where, in film ticks: the words of the last lip sync on a channel and their
 * syllables, so the timeline can show which keyframes belong to which word. A keyframe is matched
 * by its tick, so one the user moved follows into its new word.
 *
 * <p>{@link com.wemppy.bbs_lip_sync.client.TranscriptStore} keeps them, in memory and on disk.</p>
 */
public class Transcript
{
    /** A key this close before a word (ticks) still belongs to it: keyframe ticks are rounded. */
    private static final float SLACK = 0.25F;

    public record Syllable(String text, float start, float end)
    {}

    public record Span(String text, float start, float end, List<Syllable> syllables)
    {
        public boolean contains(float tick)
        {
            return tick >= this.start - SLACK && tick < this.end;
        }

        public Syllable syllableAt(float tick)
        {
            for (Syllable syllable : this.syllables)
            {
                if (tick < syllable.end())
                {
                    return syllable;
                }
            }

            return this.syllables.isEmpty() ? null : this.syllables.get(this.syllables.size() - 1);
        }
    }

    public final String id;

    /** Where the lip sync wrote its keyframes, in ticks. */
    public final float from;
    public final float to;
    public final List<Span> words;

    /**
     * The track it was made for, and the ticks of the keyframes it wrote there: after a restart
     * the channel objects are new, and these are how a transcript finds its track again.
     */
    public String channel = "";
    public String path = "";
    public float[] keys = new float[0];

    private Transcript(String id, float from, float to, List<Span> words)
    {
        this.id = id;
        this.from = from;
        this.to = to;
        this.words = words;
    }

    /**
     * Words placed the way {@link VisemeTimeline#segments} places them, moved onto the film the way
     * its keys are: from {@code clipTick}, earlier by the anticipation.
     */
    public static Transcript build(List<Word> words, LetterMap map, int clipTick, int clipDuration, float anticipation)
    {
        List<Word> sorted = new ArrayList<>(words);

        sorted.sort(Comparator.comparingDouble(Word::start));

        List<Span> spans = new ArrayList<>();
        float lastEnd = 0F;

        for (Word word : sorted)
        {
            int[] letters = map.letters(word.text());

            if (letters.length == 0)
            {
                continue;
            }

            float start = Math.max(word.start(), lastEnd);
            float end = Math.max(word.end(), start + VisemeTimeline.MIN_WORD);
            float total = 0F;

            for (int letter : letters)
            {
                total += map.weight(letter);
            }

            List<Syllable> syllables = new ArrayList<>();
            float cursor = start;
            int from = 0;

            for (int cut : cuts(letters, map))
            {
                float length = 0F;

                for (int i = from; i < cut; i++)
                {
                    length += (end - start) * map.weight(letters[i]) / total;
                }

                syllables.add(new Syllable(new String(letters, from, cut - from), tick(cursor, clipTick, anticipation), tick(cursor + length, clipTick, anticipation)));
                cursor += length;
                from = cut;
            }

            String text = word.text().startsWith("[") ? "?" : word.text();

            spans.add(new Span(text, tick(start, clipTick, anticipation), tick(end, clipTick, anticipation), syllables));
            lastEnd = end;
        }

        return new Transcript(UUID.randomUUID().toString(), clipTick, clipTick + clipDuration, spans);
    }

    /**
     * Where a word's syllables end, as letter indices, the last one being the word's length. One
     * vowel per syllable; a single consonant between vowels opens the next syllable (ma-ma), of
     * two or more the first closes the previous one (kar-ta).
     */
    private static List<Integer> cuts(int[] letters, LetterMap map)
    {
        List<Integer> vowels = new ArrayList<>();

        for (int i = 0; i < letters.length; i++)
        {
            if (map.isVowel(letters[i]))
            {
                vowels.add(i);
            }
        }

        List<Integer> cuts = new ArrayList<>();

        for (int i = 0; i + 1 < vowels.size(); i++)
        {
            int vowel = vowels.get(i);
            int consonants = vowels.get(i + 1) - vowel - 1;

            cuts.add(consonants >= 2 ? vowel + 2 : vowel + 1);
        }

        cuts.add(letters.length);

        return cuts;
    }

    private static float tick(float seconds, int clipTick, float anticipation)
    {
        return clipTick + seconds * VisemeTimeline.TPS - anticipation;
    }

    /** Whether the tick is where this lip sync wrote keyframes. */
    public boolean covers(float tick)
    {
        return tick >= this.from && tick < this.to;
    }

    public Span wordAt(float tick)
    {
        for (Span span : this.words)
        {
            if (span.contains(tick))
            {
                return span;
            }
        }

        return null;
    }

    /* Saving */

    public JsonObject toJson()
    {
        JsonObject object = new JsonObject();
        JsonArray keys = new JsonArray();
        JsonArray words = new JsonArray();

        for (float key : this.keys)
        {
            keys.add(key);
        }

        for (Span span : this.words)
        {
            JsonObject word = new JsonObject();
            JsonArray syllables = new JsonArray();

            for (Syllable syllable : span.syllables())
            {
                JsonObject entry = new JsonObject();

                entry.addProperty("text", syllable.text());
                entry.addProperty("start", syllable.start());
                entry.addProperty("end", syllable.end());
                syllables.add(entry);
            }

            word.addProperty("text", span.text());
            word.addProperty("start", span.start());
            word.addProperty("end", span.end());
            word.add("syllables", syllables);
            words.add(word);
        }

        object.addProperty("id", this.id);
        object.addProperty("channel", this.channel);
        object.addProperty("path", this.path);
        object.addProperty("from", this.from);
        object.addProperty("to", this.to);
        object.add("keys", keys);
        object.add("words", words);

        return object;
    }

    public static Transcript fromJson(JsonObject object)
    {
        List<Span> words = new ArrayList<>();

        for (JsonElement element : object.getAsJsonArray("words"))
        {
            JsonObject word = element.getAsJsonObject();
            List<Syllable> syllables = new ArrayList<>();

            for (JsonElement entry : word.getAsJsonArray("syllables"))
            {
                JsonObject syllable = entry.getAsJsonObject();

                syllables.add(new Syllable(syllable.get("text").getAsString(), syllable.get("start").getAsFloat(), syllable.get("end").getAsFloat()));
            }

            words.add(new Span(word.get("text").getAsString(), word.get("start").getAsFloat(), word.get("end").getAsFloat(), syllables));
        }

        Transcript transcript = new Transcript(object.get("id").getAsString(), object.get("from").getAsFloat(), object.get("to").getAsFloat(), words);
        JsonArray keys = object.getAsJsonArray("keys");

        transcript.channel = object.get("channel").getAsString();
        transcript.path = object.get("path").getAsString();
        transcript.keys = new float[keys.size()];

        for (int i = 0; i < keys.size(); i++)
        {
            transcript.keys[i] = keys.get(i).getAsFloat();
        }

        return transcript;
    }
}
