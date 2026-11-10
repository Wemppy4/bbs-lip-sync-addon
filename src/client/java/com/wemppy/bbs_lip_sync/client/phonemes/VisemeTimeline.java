package com.wemppy.bbs_lip_sync.client.phonemes;

import com.wemppy.bbs_lip_sync.client.model.Word;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns recognized words into mouth-shape segments and keyframes.
 *
 * <p>Speech recognition gives each word's start and end; the word's spelling says which shapes
 * are in it, and the word's time is shared between its letters by how long each kind of letter
 * tends to sound. Short pauses between words keep the last shape; only a real pause closes the
 * mouth. Lip closures (b, p, m, f, v) are always shown, quick tongue-and-teeth consonants
 * dissolve into the vowel next to them so the mouth doesn't flicker.</p>
 */
public class VisemeTimeline
{
    /** Ticks per second in a BBS film. */
    public static final float TPS = 20F;

    /** The mouth never closes for less than this (ticks): a one-tick snap shut reads as a glitch. */
    private static final float IDLE_MIN_TICKS = 3F;

    /** Ticks a shape takes to ease into the next one. */
    private static final float TRANSITION = 1.5F;

    /** Segments at least this long (ticks) get a second, hold keyframe so the shape stays put. */
    private static final float HOLD_MIN = 3.5F;

    /**
     * A closed mouth holds as soon as there's room for a hold keyframe at all: without one, BBS
     * would ease from the start of the silence into the next word, and the mouth would creep open
     * over the whole pause instead of opening just before the sound.
     */
    private static final float IDLE_HOLD_MIN = TRANSITION + 0.5F;

    /** A lip closure is shown for at least this long (ticks). */
    private static final float CLOSURE_MIN = 1F;

    /**
     * Pictures swap instantly, so a shape shorter than this (ticks) flashes rather than moves and
     * joins its neighbour; lip closures still keep their {@link #CLOSURE_MIN}.
     */
    private static final float STEP_MIN = 1.5F;

    /** Nothing shorter than this (ticks) becomes a keyframe. */
    private static final float MIN_GAP = 0.5F;

    /** A word is never shorter than this (seconds), whatever the recognizer says. */
    static final float MIN_WORD = 0.05F;

    /** One mouth shape held from {@code start} to {@code end} (seconds from the clip start). */
    public record Segment(Viseme viseme, float start, float end)
    {
        public float length()
        {
            return this.end - this.start;
        }

        Segment from(float start)
        {
            return new Segment(this.viseme, start, this.end);
        }

        Segment to(float end)
        {
            return new Segment(this.viseme, this.start, end);
        }
    }

    /** A keyframe to write: shape, its tick on the film timeline, and whether it only holds the shape. */
    public record Key(Viseme viseme, float tick, boolean hold)
    {}

    /**
     * @param weakMin    shortest weak consonant shape (ticks) kept as its own segment
     * @param minPause   shortest silence (ticks) that closes the mouth; shorter gaps keep the last shape
     * @param closeDelay how long (ticks) the last shape of a phrase stays after the word ends before the mouth closes
     */
    public static List<Segment> segments(List<Word> words, LetterMap map, float weakMin, float minPause, float closeDelay)
    {
        float delay = Math.max(0F, closeDelay) / TPS;
        List<Segment> segments = spell(words, map, Math.max(IDLE_MIN_TICKS, minPause) / TPS, delay);

        if (segments.isEmpty())
        {
            return segments;
        }

        absorbWeak(segments, weakMin / TPS);
        ensureClosures(segments, CLOSURE_MIN / TPS);
        absorbShort(segments, MIN_GAP / TPS);
        mergeSame(segments);
        delayClosing(segments, delay);

        return segments;
    }

    /**
     * Words → one segment per letter. A letter's share of the word is its weight over the word's
     * total; a letter the table doesn't know keeps the previous shape. Between words, a gap of at
     * least {@code minPause} seconds closes the mouth — provided it is still a real pause once the
     * closing delay has eaten into it — and a shorter one holds the last shape.
     */
    private static List<Segment> spell(List<Word> words, LetterMap map, float minPause, float closeDelay)
    {
        List<Word> sorted = new ArrayList<>(words);

        sorted.sort(Comparator.comparingDouble(Word::start));

        List<Segment> segments = new ArrayList<>();
        Viseme last = Viseme.IDLE;
        float lastEnd = 0F;

        for (Word word : sorted)
        {
            int[] letters = map.letters(word.text());

            if (letters.length == 0)
            {
                continue;
            }

            float start = Math.max(word.start(), lastEnd);
            float end = Math.max(word.end(), start + MIN_WORD);

            if (!segments.isEmpty())
            {
                float gap = start - lastEnd;

                if (gap >= minPause && gap - closeDelay >= IDLE_MIN_TICKS / TPS)
                {
                    segments.add(new Segment(Viseme.IDLE, lastEnd, start));
                }
                else if (gap > 0)
                {
                    int index = segments.size() - 1;

                    segments.set(index, segments.get(index).to(start));
                }
            }

            float total = 0F;

            for (int letter : letters)
            {
                total += map.weight(letter);
            }

            float cursor = start;

            for (int letter : letters)
            {
                float length = (end - start) * map.weight(letter) / total;
                Viseme viseme = map.viseme(letter);

                if (viseme == null)
                {
                    viseme = last;
                }

                last = viseme;
                segments.add(new Segment(viseme, cursor, cursor + length));
                cursor += length;
            }

            lastEnd = end;
        }

        return segments;
    }

    /**
     * A quick weak consonant (teeth, tongue) hands its time to the vowel after it; at the end of
     * a word, to the shape before it.
     */
    private static void absorbWeak(List<Segment> segments, float weakMin)
    {
        for (int i = 0; i < segments.size(); )
        {
            Segment segment = segments.get(i);

            if (!segment.viseme().isWeak() || segment.length() >= weakMin)
            {
                i++;

                continue;
            }

            Segment next = i + 1 < segments.size() ? segments.get(i + 1) : null;
            Segment prev = i > 0 ? segments.get(i - 1) : null;

            if (next != null && next.viseme() != Viseme.IDLE)
            {
                segments.set(i + 1, next.from(segment.start()));
            }
            else if (prev != null)
            {
                segments.set(i - 1, prev.to(segment.end()));
            }
            else if (next != null)
            {
                segments.set(i + 1, next.from(segment.start()));
            }
            else
            {
                i++;

                continue;
            }

            segments.remove(i);
        }
    }

    /** Lip closures are short by nature but must be seen: stretch them to a tick, taking from the neighbour. */
    private static void ensureClosures(List<Segment> segments, float closureMin)
    {
        for (int i = 0; i < segments.size(); i++)
        {
            Segment segment = segments.get(i);

            if (!segment.viseme().isClosure() || segment.length() >= closureMin)
            {
                continue;
            }

            float needed = closureMin - segment.length();
            Segment next = i + 1 < segments.size() ? segments.get(i + 1) : null;
            Segment prev = i > 0 ? segments.get(i - 1) : null;

            if (next != null && next.length() - needed >= closureMin)
            {
                segments.set(i + 1, next.from(next.start() + needed));
                segments.set(i, segment.to(segment.end() + needed));
            }
            else if (prev != null && prev.length() - needed >= closureMin)
            {
                segments.set(i - 1, prev.to(prev.end() - needed));
                segments.set(i, segment.from(segment.start() - needed));
            }
        }
    }

    /** Whatever is still too short to make a keyframe joins the longer neighbour. */
    private static void absorbShort(List<Segment> segments, float min)
    {
        for (int i = 0; i < segments.size() && segments.size() > 1; )
        {
            Segment segment = segments.get(i);

            if (segment.length() >= min)
            {
                i++;

                continue;
            }

            Segment next = i + 1 < segments.size() ? segments.get(i + 1) : null;
            Segment prev = i > 0 ? segments.get(i - 1) : null;

            if (next != null && (prev == null || next.length() >= prev.length()))
            {
                segments.set(i + 1, next.from(segment.start()));
            }
            else
            {
                segments.set(i - 1, prev.to(segment.end()));
            }

            segments.remove(i);
        }
    }

    /**
     * Speech doesn't stop dead when the recognizer says a word ended: the last shape lingers for
     * {@code delay} seconds into every pause and after the last word, then the mouth closes. The
     * pause keeps at least {@link #IDLE_MIN_TICKS}, so a delay never turns it into a flash of idle.
     * Done last, so the lingering goes to the shape that survived the clean-up.
     */
    private static void delayClosing(List<Segment> segments, float delay)
    {
        if (delay <= 0F)
        {
            return;
        }

        for (int i = 1; i < segments.size(); i++)
        {
            Segment idle = segments.get(i);

            if (idle.viseme() != Viseme.IDLE)
            {
                continue;
            }

            float shift = Math.min(delay, idle.length() - IDLE_MIN_TICKS / TPS);

            if (shift > 0F)
            {
                segments.set(i - 1, segments.get(i - 1).to(idle.start() + shift));
                segments.set(i, idle.from(idle.start() + shift));
            }
        }

        int last = segments.size() - 1;
        Segment tail = segments.get(last);

        if (tail.viseme() != Viseme.IDLE)
        {
            segments.set(last, tail.to(tail.end() + delay));
        }
    }

    private static void mergeSame(List<Segment> segments)
    {
        for (int i = segments.size() - 1; i > 0; i--)
        {
            Segment a = segments.get(i - 1);
            Segment b = segments.get(i);

            if (a.viseme() == b.viseme())
            {
                segments.set(i - 1, a.to(b.end()));
                segments.remove(i);
            }
        }
    }

    /**
     * Keyframes for the segments. Each shape gets a keyframe at its start; a long one also gets a
     * hold keyframe near its end, so the mouth stays in the shape and only then eases into the
     * next one (BBS interpolates between consecutive keyframes). Everything is shifted ahead by
     * the anticipation and kept inside the clip.
     *
     * <p>Silence — before the first word and between words — is a closed mouth held right up to
     * {@link #TRANSITION} ticks before the next sound, the way the old lip sync tool did it.</p>
     */
    public static List<Key> keys(List<Segment> segments, int clipTick, int clipDuration, float anticipation)
    {
        List<Key> keys = new ArrayList<>();

        if (segments.isEmpty())
        {
            return keys;
        }

        float first = clipTick;
        float last = clipTick + clipDuration - MIN_GAP;
        Segment head = segments.get(0);

        if (head.viseme() != Viseme.IDLE)
        {
            float speech = clipTick + head.start() * TPS - anticipation;

            if (speech - clipTick > TRANSITION)
            {
                add(keys, Viseme.IDLE, clipTick, false, first, last);

                if (speech - clipTick >= IDLE_HOLD_MIN)
                {
                    add(keys, Viseme.IDLE, speech - TRANSITION, true, first, last);
                }
            }
        }

        for (Segment segment : segments)
        {
            float start = clipTick + segment.start() * TPS - anticipation;
            float end = clipTick + segment.end() * TPS - anticipation;
            float holdMin = segment.viseme() == Viseme.IDLE ? IDLE_HOLD_MIN : HOLD_MIN;

            add(keys, segment.viseme(), start, false, first, last);

            if (end - start >= holdMin)
            {
                add(keys, segment.viseme(), end - TRANSITION, true, first, last);
            }
        }

        Segment tail = segments.get(segments.size() - 1);

        if (tail.viseme() != Viseme.IDLE)
        {
            add(keys, Viseme.IDLE, clipTick + tail.end() * TPS - anticipation, false, first, last);
        }

        return keys;
    }

    /**
     * Keyframes for pictures, which switch instead of easing: one at the start of every shape, the
     * too short ones merged away first, and the closed mouth at the clip start and after the last
     * word. No hold keyframes, since a constant keyframe holds by itself.
     */
    public static List<Key> steps(List<Segment> segments, int clipTick, int clipDuration, float anticipation)
    {
        List<Key> keys = new ArrayList<>();

        if (segments.isEmpty())
        {
            return keys;
        }

        List<Segment> merged = new ArrayList<>(segments);

        absorbFlashes(merged, STEP_MIN / TPS);
        mergeSame(merged);

        float first = clipTick;
        float last = clipTick + clipDuration - MIN_GAP;
        Segment head = merged.get(0);

        if (head.viseme() != Viseme.IDLE && clipTick + head.start() * TPS - anticipation >= clipTick + MIN_GAP)
        {
            add(keys, Viseme.IDLE, clipTick, false, first, last);
        }

        for (Segment segment : merged)
        {
            add(keys, segment.viseme(), clipTick + segment.start() * TPS - anticipation, false, first, last);
        }

        Segment tail = merged.get(merged.size() - 1);

        if (tail.viseme() != Viseme.IDLE)
        {
            add(keys, Viseme.IDLE, clipTick + tail.end() * TPS - anticipation, false, first, last);
        }

        return keys;
    }

    /**
     * For pictures: a shape too short to see joins the longer of its open neighbours. It never
     * joins a lip closure or a pause, which would keep the mouth shut through a vowel; with no open
     * neighbour it stays, since it's the only opening there is.
     */
    private static void absorbFlashes(List<Segment> segments, float min)
    {
        for (int i = 0; i < segments.size(); )
        {
            Segment segment = segments.get(i);
            boolean closure = segment.viseme().isClosure() && segment.length() >= CLOSURE_MIN / TPS - 0.0001F;

            if (segment.viseme() == Viseme.IDLE || closure || segment.length() >= min - 0.0001F)
            {
                i++;

                continue;
            }

            Segment prev = i > 0 && isOpen(segments.get(i - 1)) ? segments.get(i - 1) : null;
            Segment next = i + 1 < segments.size() && isOpen(segments.get(i + 1)) ? segments.get(i + 1) : null;

            if (next != null && (prev == null || next.length() >= prev.length()))
            {
                segments.set(i + 1, next.from(segment.start()));
            }
            else if (prev != null)
            {
                segments.set(i - 1, prev.to(segment.end()));
            }
            else
            {
                i++;

                continue;
            }

            segments.remove(i);
        }
    }

    private static boolean isOpen(Segment segment)
    {
        return segment.viseme() != Viseme.IDLE && !segment.viseme().isClosure();
    }

    private static void add(List<Key> keys, Viseme viseme, float tick, boolean hold, float first, float last)
    {
        tick = Math.round(Math.max(first, Math.min(last, tick)) * 4F) / 4F;

        if (!keys.isEmpty())
        {
            Key previous = keys.get(keys.size() - 1);

            if (previous.viseme() == viseme && !hold)
            {
                return;
            }

            if (tick - previous.tick() < MIN_GAP)
            {
                if (hold)
                {
                    return;
                }

                /* The clamp squeezed two shapes onto one spot: the later one wins */
                keys.remove(keys.size() - 1);
                tick = previous.tick();
            }
        }

        keys.add(new Key(viseme, tick, hold));
    }

    /** Whether anything but silence was heard. */
    public static boolean hasSpeech(List<Segment> segments)
    {
        for (Segment segment : segments)
        {
            if (segment.viseme() != Viseme.IDLE)
            {
                return true;
            }
        }

        return false;
    }
}
