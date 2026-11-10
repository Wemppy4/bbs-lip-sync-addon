package com.wemppy.bbs_lip_sync.client;

import com.wemppy.bbs_lip_sync.client.bake.KeyframeBaker;
import com.wemppy.bbs_lip_sync.client.bake.MouthRig;
import com.wemppy.bbs_lip_sync.client.model.Language;
import com.wemppy.bbs_lip_sync.client.model.Languages;
import com.wemppy.bbs_lip_sync.client.model.Word;
import com.wemppy.bbs_lip_sync.client.phonemes.LetterMap;
import com.wemppy.bbs_lip_sync.client.phonemes.TextSpeech;
import com.wemppy.bbs_lip_sync.client.phonemes.Transcript;
import com.wemppy.bbs_lip_sync.client.phonemes.VisemeTimeline;
import com.wemppy.bbs_lip_sync.client.ui.UITextLipSyncOverlayPanel;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lip sync from typed text. The text becomes words with made-up times ({@link TextSpeech}) and
 * goes through the same timeline and baker as recognized speech, starting at the film's cursor.
 * There is no audio and no model, so nothing runs in the background: the keyframes are written the
 * moment the dialog's button is pressed.
 */
public class TextLipSync
{
    /** The dialog's text, kept for the next time it opens: the usual next step is another speed. */
    public static String lastText = "";

    /**
     * Where the last text went. Redone on the same spot at a faster speed, it is shorter than
     * before, and the old keyframes past its new end have to go too.
     */
    private static WeakReference<KeyframeChannel> lastChannel = new WeakReference<>(null);
    private static int lastTick;
    private static int lastTicks;

    /** What a text turns into: the words, their mouth shapes and how many ticks they take, closed mouth included. */
    public record Plan(List<Word> words, LetterMap map, List<VisemeTimeline.Segment> segments, int ticks)
    {
        public boolean hasSpeech()
        {
            return VisemeTimeline.hasSpeech(this.segments);
        }
    }

    public static void open(UIReplaysEditor editor, UIKeyframeSheet sheet)
    {
        UIFilmPanel panel = editor.getParent(UIFilmPanel.class);
        Replay replay = editor.getReplay();
        LipSyncTarget target = LipSyncTarget.of(sheet);
        UIContext context = editor.getContext();

        if (panel == null || replay == null || target == null || context == null || LipSyncJob.isBusy())
        {
            return;
        }

        MouthRig<?> rig = target.usableRig(context);

        if (rig == null)
        {
            return;
        }

        UIOverlay.addOverlay(context, new UITextLipSyncOverlayPanel((plan) ->
        {
            if (!LipSyncJob.isBusy() && plan.hasSpeech())
            {
                bake(editor, panel, replay, target.channel(), rig, panel.getCursor(), plan);
            }
        }));
    }

    public static Plan plan(String text, float speed, Tables tables)
    {
        LetterMap map = tables.forText(text);

        if (map == null)
        {
            return new Plan(List.of(), null, List.of(), 0);
        }

        List<Word> words = TextSpeech.words(text, map, speed);
        List<VisemeTimeline.Segment> segments = VisemeTimeline.segments(words, map, LipSyncSettings.minHold(), LipSyncSettings.minPause(), LipSyncSettings.closeDelay());
        int ticks = segments.isEmpty() ? 0 : (int) Math.ceil(segments.get(segments.size() - 1).end() * VisemeTimeline.TPS) + 1;

        return new Plan(words, map, segments, ticks);
    }

    /** No anticipation: with no sound to get ahead of, the text's timing is the mouth's. */
    @SuppressWarnings("unchecked")
    private static void bake(UIReplaysEditor editor, UIFilmPanel panel, Replay replay, KeyframeChannel channel, MouthRig rig, int tick, Plan plan)
    {
        List<VisemeTimeline.Key> keys = rig.isSmooth()
            ? VisemeTimeline.keys(plan.segments(), tick, plan.ticks(), 0F)
            : VisemeTimeline.steps(plan.segments(), tick, plan.ticks(), 0F);
        int range = lastChannel.get() == channel && lastTick == tick ? Math.max(plan.ticks(), lastTicks) : plan.ticks();

        KeyframeBaker.bake(replay, channel, rig, keys, tick, range);
        TranscriptStore.remember(panel.getData() == null ? null : panel.getData().getId(), channel, Transcript.build(plan.words(), plan.map(), tick, range, 0F));
        panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        editor.updateChannelsList();

        lastChannel = new WeakReference<>(channel);
        lastTick = tick;
        lastTicks = plan.ticks();
    }

    /** Letter tables by id, each read once; one set lives as long as a dialog. */
    public static class Tables
    {
        /** Below this share of known letters the speech language is taken for the wrong one. */
        private static final float ENOUGH = 0.8F;

        private final Map<String, LetterMap> loaded = new HashMap<>();

        /**
         * The speech language's table, unless it can't read the text — Russian typed while the game
         * runs in English — and another language's table reads more of it.
         */
        public LetterMap forText(String text)
        {
            Language current = Languages.current();
            LetterMap best = current == null ? null : this.get(current.letters);
            float bestCoverage = best == null ? 0F : TextSpeech.coverage(text, best);

            if (bestCoverage >= ENOUGH)
            {
                return best;
            }

            for (Language language : Languages.all())
            {
                LetterMap map = this.get(language.letters);
                float coverage = map == null ? 0F : TextSpeech.coverage(text, map);

                if (coverage > bestCoverage)
                {
                    best = map;
                    bestCoverage = coverage;
                }
            }

            return best;
        }

        private LetterMap get(String id)
        {
            if (!this.loaded.containsKey(id))
            {
                LetterMap map = null;

                try
                {
                    map = LetterMap.load(id);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                this.loaded.put(id, map);
            }

            return this.loaded.get(id);
        }
    }
}
