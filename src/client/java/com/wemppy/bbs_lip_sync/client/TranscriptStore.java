package com.wemppy.bbs_lip_sync.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.wemppy.bbs_lip_sync.client.phonemes.Transcript;
import com.wemppy.bbs_lip_sync.client.phonemes.Viseme;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where the words of every lip sync live: in memory per channel, and on disk per film in
 * {@code config/bbs/lipsync/transcripts/<film>.json}, so they are still there after a restart.
 *
 * <p>A film's channels are new objects every time it loads, so a saved transcript finds its track
 * by the keyframes it wrote: the track with the same name whose keyframes still stand where the
 * transcript says. A transcript whose keyframes are all gone (undone, regenerated, deleted) finds
 * no track and is simply not shown.</p>
 */
public class TranscriptStore
{
    /** Share of a transcript's keyframes still in place for it to belong to a track. */
    private static final float MATCH = 0.5F;

    /** The same, when the track also sits at the same place in the film (same replay). */
    private static final float MATCH_SAME_PATH = 0.2F;

    private static final Map<KeyframeChannel, List<Transcript>> LOADED = new WeakHashMap<>();
    private static final Map<String, List<Transcript>> FILMS = new HashMap<>();

    /** The transcripts of a track (one per lip synced clip), from memory or from the film's file. */
    public static List<Transcript> of(String film, KeyframeChannel channel)
    {
        if (channel == null)
        {
            return List.of();
        }

        List<Transcript> transcripts = LOADED.get(channel);

        if (transcripts == null)
        {
            transcripts = film == null ? new ArrayList<>() : find(film(film), channel);
            LOADED.put(channel, transcripts);
        }

        return transcripts;
    }

    /**
     * Keeps the words of a lip sync just written into a track. Its keyframes replaced whatever the
     * track had in that range, so the transcripts there, and any whose keyframes are all gone, go.
     */
    public static void remember(String film, KeyframeChannel channel, Transcript transcript)
    {
        List<Transcript> transcripts = of(film, channel);

        transcripts.removeIf((entry) -> entry.from < transcript.to && transcript.from < entry.to);

        if (!transcript.words.isEmpty())
        {
            transcript.channel = String.valueOf(channel.getId());
            transcript.path = path(channel);
            transcript.keys = ownTicks(channel, transcript);
            transcripts.add(transcript);
        }

        if (film == null)
        {
            return;
        }

        List<Transcript> saved = film(film);
        String path = path(channel);

        saved.removeIf((entry) -> transcripts.contains(entry) || (entry.path.equals(path) && score(channel, entry) < MATCH_SAME_PATH));
        saved.addAll(transcripts);
        save(film, saved);
    }

    /** The saved transcripts whose keyframes are still on this track. */
    private static List<Transcript> find(List<Transcript> saved, KeyframeChannel channel)
    {
        String id = String.valueOf(channel.getId());
        String path = path(channel);
        List<Transcript> found = new ArrayList<>();

        for (Transcript transcript : saved)
        {
            if (!transcript.channel.equals(id))
            {
                continue;
            }

            float needed = transcript.path.equals(path) ? MATCH_SAME_PATH : MATCH;

            if (score(channel, transcript) >= needed)
            {
                found.add(transcript);
            }
        }

        return found;
    }

    /** Share of the transcript's keyframes that still stand on the channel. */
    private static float score(KeyframeChannel<?> channel, Transcript transcript)
    {
        if (transcript.keys.length == 0)
        {
            return 0F;
        }

        int found = 0;

        for (float tick : transcript.keys)
        {
            for (Keyframe<?> keyframe : channel.getKeyframes())
            {
                if (Math.abs(keyframe.getTick() - tick) < 0.01F && Viseme.isOwnColor(keyframe.getStyle().getColor()))
                {
                    found++;

                    break;
                }
            }
        }

        return found / (float) transcript.keys.length;
    }

    private static float[] ownTicks(KeyframeChannel<?> channel, Transcript transcript)
    {
        List<Float> ticks = new ArrayList<>();

        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            if (transcript.covers(keyframe.getTick()) && Viseme.isOwnColor(keyframe.getStyle().getColor()))
            {
                ticks.add(keyframe.getTick());
            }
        }

        float[] array = new float[ticks.size()];

        for (int i = 0; i < array.length; i++)
        {
            array[i] = ticks.get(i);
        }

        return array;
    }

    private static String path(KeyframeChannel<?> channel)
    {
        try
        {
            return String.valueOf(channel.getPath());
        }
        catch (Exception e)
        {
            return "";
        }
    }

    /* Files */

    private static List<Transcript> film(String film)
    {
        return FILMS.computeIfAbsent(film, TranscriptStore::load);
    }

    private static File file(String film)
    {
        return new File(Bundled.folder(), "transcripts/" + URLEncoder.encode(film, StandardCharsets.UTF_8) + ".json");
    }

    private static List<Transcript> load(String film)
    {
        List<Transcript> transcripts = new ArrayList<>();
        File file = file(film);

        if (!file.isFile())
        {
            return transcripts;
        }

        try
        {
            for (JsonElement element : JsonParser.parseString(Files.readString(file.toPath())).getAsJsonArray())
            {
                transcripts.add(Transcript.fromJson(element.getAsJsonObject()));
            }
        }
        catch (Exception e)
        {
            System.err.println("[bbs_lip_sync] Failed to read " + file);
            e.printStackTrace();
        }

        return transcripts;
    }

    private static void save(String film, List<Transcript> transcripts)
    {
        File file = file(film);
        JsonArray array = new JsonArray();

        for (Transcript transcript : transcripts)
        {
            array.add(transcript.toJson());
        }

        try
        {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), new GsonBuilder().create().toJson(array));
        }
        catch (Exception e)
        {
            System.err.println("[bbs_lip_sync] Failed to write " + file);
            e.printStackTrace();
        }
    }
}
