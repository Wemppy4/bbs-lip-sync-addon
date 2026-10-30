package com.wemppy.bbs_lip_sync.client;

import com.wemppy.bbs_lip_sync.client.bake.MouthRig;
import com.wemppy.bbs_lip_sync.client.model.Language;
import com.wemppy.bbs_lip_sync.client.model.Languages;
import com.wemppy.bbs_lip_sync.client.model.ModelStore;
import com.wemppy.bbs_lip_sync.client.ui.AudioClipPicker;
import com.wemppy.bbs_lip_sync.client.ui.UIModelDownloadOverlayPanel;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;

/**
 * What happens after "Generate lip sync" is clicked on a pose or texture track: check the form has
 * mouth shapes, pick the audio clip, make sure the language's model is on disk, then run a
 * {@link LipSyncJob}.
 */
public class LipSyncController
{
    public static void generate(UIReplaysEditor editor, UIKeyframeSheet sheet)
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

        Film film = panel.getData();

        AudioClipPicker.pick(film, panel.cameraEditor.clips.getClipsFromSelection(), panel.getCursor(), context, (clips) ->
        {
            Runnable start = () -> start(editor, panel, replay, target.channel(), rig, clips);
            Language language = Languages.current();

            if (language != null && ModelStore.isReady(language.model(LipSyncSettings.largeModel())))
            {
                start.run();
            }
            else
            {
                ModelStore.onceReady(start);
                UIOverlay.addOverlay(context, new UIModelDownloadOverlayPanel(language));
            }
        });
    }

    /**
     * Runs the jobs one clip after another for whatever language is current now — it may have been
     * changed in the model dialog. A cancelled or failed clip stops the rest.
     */
    private static void start(UIReplaysEditor editor, UIFilmPanel panel, Replay replay, KeyframeChannel channel, MouthRig<?> rig, List<AudioClip> clips)
    {
        if (clips.isEmpty())
        {
            return;
        }

        AudioClip clip = clips.get(0);
        Language language = Languages.current();

        if (LipSyncJob.isBusy() || clip.audio.get() == null || language == null)
        {
            return;
        }

        Language.Model model = language.model(LipSyncSettings.largeModel());

        if (!ModelStore.isReady(model))
        {
            return;
        }

        Runnable onBaked = () ->
        {
            panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
            editor.updateChannelsList();
        };

        new LipSyncJob(
            replay, channel, rig, language, model,
            clip.audio.get(), clip.offset.get(), clip.tick.get(), clip.duration.get(),
            onBaked, (error) -> message(editor.getContext(), LipSyncKeys.ERROR_TITLE, error)
        ).then(() -> start(editor, panel, replay, channel, rig, clips.subList(1, clips.size()))).start();
    }

    public static void message(UIContext context, IKey title, IKey message)
    {
        if (context != null)
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(title, message));
        }
    }
}
