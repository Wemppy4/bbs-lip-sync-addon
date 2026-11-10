package com.wemppy.bbs_lip_sync.client.ui;

import com.wemppy.bbs_lip_sync.client.LipSyncController;
import com.wemppy.bbs_lip_sync.client.LipSyncJob;
import com.wemppy.bbs_lip_sync.client.LipSyncKeys;
import com.wemppy.bbs_lip_sync.client.LipSyncTarget;
import com.wemppy.bbs_lip_sync.client.TextLipSync;
import com.wemppy.bbs_lip_sync.client.model.Language;
import com.wemppy.bbs_lip_sync.client.model.Languages;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * Hooks the addon into the replay keyframe timeline: context menu entries on pose and texture tracks, the
 * words of a finished lip sync over its row, and a painter that draws the running job over its row. Called after every rebuild of the editor.
 */
public class LipSyncTimeline
{
    public static void attach(UIReplaysEditor editor)
    {
        UIKeyframeEditor keyframeEditor = editor.keyframeEditor;

        if (keyframeEditor == null)
        {
            return;
        }

        keyframeEditor.view.context((menu) ->
        {
            UIContext context = editor.getContext();
            UIKeyframeSheet sheet = context == null ? null : keyframeEditor.view.getGraph().getSheet(context.mouseX, context.mouseY);

            if (LipSyncTarget.of(sheet) == null)
            {
                return;
            }

            LipSyncJob job = LipSyncJob.current();

            if (job != null && !job.isFinished())
            {
                if (job.channel == sheet.channel)
                {
                    menu.action(Icons.STOP, LipSyncKeys.CANCEL, job::cancel);
                }

                return;
            }

            Language language = Languages.current();
            String name = language == null ? "?" : language.name;

            menu.action(Icons.VOICE, LipSyncKeys.GENERATE, () -> LipSyncController.generate(editor, sheet));
            menu.action(Icons.FONT, LipSyncKeys.TEXT_GENERATE, () -> TextLipSync.open(editor, sheet));
            menu.action(Icons.GLOBE, LipSyncKeys.LANGUAGE.format(name), () -> LanguagePicker.open(context, (picked) -> {}));
        });

        LipSyncWords words = new LipSyncWords(keyframeEditor);

        if (keyframeEditor.view.tooltip != null)
        {
            keyframeEditor.view.tooltip = words.wrapPreview(keyframeEditor.view.tooltip);
        }

        keyframeEditor.add(words);
        keyframeEditor.add(new UIRenderable((context) -> LipSyncProgressPainter.render(context, keyframeEditor)));
    }
}
