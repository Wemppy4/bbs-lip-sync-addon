package com.wemppy.bbs_lip_sync.client.ui;

import com.wemppy.bbs_lip_sync.client.LipSyncController;
import com.wemppy.bbs_lip_sync.client.LipSyncKeys;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Chooses the audio clips a lip sync is generated from, asking only when it is ambiguous: audio
 * clips selected in the camera editor are all taken, in film order; otherwise the film's only
 * clip, or the only clip under the playback cursor, is taken as is.
 */
public class AudioClipPicker
{
    public static void pick(Film film, List<Clip> selected, int cursor, UIContext context, Consumer<List<AudioClip>> callback)
    {
        List<AudioClip> clips = new ArrayList<>();

        for (AudioClip clip : film.camera.getClips(AudioClip.class))
        {
            if (clip.enabled.get() && clip.audio.get() != null)
            {
                clips.add(clip);
            }
        }

        if (clips.isEmpty())
        {
            LipSyncController.message(context, LipSyncKeys.NO_AUDIO_TITLE, LipSyncKeys.NO_AUDIO_MESSAGE);

            return;
        }

        List<AudioClip> picked = new ArrayList<>();

        for (Clip clip : selected)
        {
            if (clip instanceof AudioClip audio && clips.contains(audio))
            {
                picked.add(audio);
            }
        }

        if (!picked.isEmpty())
        {
            picked.sort(Comparator.comparingInt((AudioClip clip) -> clip.tick.get()));
            callback.accept(picked);

            return;
        }

        if (clips.size() == 1)
        {
            callback.accept(clips);

            return;
        }

        List<AudioClip> underCursor = new ArrayList<>();

        for (AudioClip clip : clips)
        {
            if (clip.isInside(cursor))
            {
                underCursor.add(clip);
            }
        }

        if (underCursor.size() == 1)
        {
            callback.accept(underCursor);

            return;
        }

        Map<String, AudioClip> labels = new LinkedHashMap<>();

        for (int i = 0; i < clips.size(); i++)
        {
            labels.put(label(i, clips.get(i)), clips.get(i));
        }

        UIListOverlayPanel[] panel = new UIListOverlayPanel[1];

        panel[0] = new UIListOverlayPanel(LipSyncKeys.PICK_AUDIO_TITLE, (label) ->
        {
            AudioClip clip = labels.get(label);

            if (clip != null)
            {
                panel[0].close();
                callback.accept(List.of(clip));
            }
        });

        panel[0].addValues(labels.keySet());

        if (!underCursor.isEmpty())
        {
            panel[0].setValue(label(clips.indexOf(underCursor.get(0)), underCursor.get(0)));
        }

        UIOverlay.addOverlay(context, panel[0], 320, 0.6F);
    }

    private static String label(int index, AudioClip clip)
    {
        String name = clip.title.get();

        if (name == null || name.isEmpty())
        {
            Link link = clip.audio.get();
            String path = link == null ? "" : link.path;
            int slash = path.lastIndexOf('/');

            name = slash >= 0 ? path.substring(slash + 1) : path;
        }

        int tick = clip.tick.get();
        int end = tick + clip.duration.get();

        return (index + 1) + ". " + name + "  " + TimeUtils.formatTime(tick) + " - " + TimeUtils.formatTime(end) + "  L" + clip.layer.get();
    }
}
