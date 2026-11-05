package com.wemppy.bbs_lip_sync.client.ui;

import com.wemppy.bbs_lip_sync.client.LipSyncKeys;
import com.wemppy.bbs_lip_sync.client.LipSyncSettings;
import com.wemppy.bbs_lip_sync.client.model.Language;
import com.wemppy.bbs_lip_sync.client.model.Languages;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** A searchable list of the languages lip sync knows; picking one stores it in the settings. */
public class LanguagePicker
{
    public static void open(UIContext context, Consumer<Language> callback)
    {
        Map<String, Language> labels = new LinkedHashMap<>();

        for (Language language : Languages.all())
        {
            labels.put(language.label(), language);
        }

        UIStringOverlayPanel[] panel = new UIStringOverlayPanel[1];

        panel[0] = new UIStringOverlayPanel(LipSyncKeys.PICK_LANGUAGE_TITLE, false, labels.keySet(), (label) ->
        {
            Language language = labels.get(label);

            if (language != null)
            {
                if (LipSyncSettings.language != null)
                {
                    LipSyncSettings.language.set(language.code);
                }

                panel[0].close();
                callback.accept(language);
            }
        });

        Language current = Languages.current();

        if (current != null)
        {
            panel[0].set(current.label());
        }

        panel[0].w(240).h(0.6F);
        UIOverlay.addOverlay(context, panel[0]);
    }
}
