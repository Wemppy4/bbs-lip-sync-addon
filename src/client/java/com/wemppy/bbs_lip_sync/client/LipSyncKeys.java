package com.wemppy.bbs_lip_sync.client;

import com.wemppy.bbs_lip_sync.client.phonemes.Viseme;
import com.wemppy.bbs_lip_sync.client.settings.ValueTimingPreset;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

/** Language keys used by the addon's UI; the strings live in {@code assets/bbs_lip_sync/assets/strings}. */
public class LipSyncKeys
{
    public static final IKey GENERATE = L10n.lang("bbs_lip_sync.ui.generate");
    public static final IKey CANCEL = L10n.lang("bbs_lip_sync.ui.cancel");
    public static final IKey LANGUAGE = L10n.lang("bbs_lip_sync.ui.language");
    public static final IKey PICK_LANGUAGE_TITLE = L10n.lang("bbs_lip_sync.ui.pick_language.title");

    public static final IKey TEXT_GENERATE = L10n.lang("bbs_lip_sync.ui.text.generate");
    public static final IKey TEXT_TITLE = L10n.lang("bbs_lip_sync.ui.text.title");
    public static final IKey TEXT_PLACEHOLDER = L10n.lang("bbs_lip_sync.ui.text.placeholder");
    public static final IKey TEXT_SPEED = L10n.lang("bbs_lip_sync.ui.text.speed");
    public static final IKey TEXT_SPEED_TOOLTIP = L10n.lang("bbs_lip_sync.ui.text.speed_tooltip");
    public static final IKey TEXT_DURATION = L10n.lang("bbs_lip_sync.ui.text.duration");
    public static final IKey TEXT_NO_LETTERS = L10n.lang("bbs_lip_sync.ui.text.no_letters");
    public static final IKey TEXT_BUTTON = L10n.lang("bbs_lip_sync.ui.text.button");

    public static final IKey PICK_AUDIO_TITLE = L10n.lang("bbs_lip_sync.ui.pick_audio.title");
    public static final IKey NO_AUDIO_TITLE = L10n.lang("bbs_lip_sync.ui.no_audio.title");
    public static final IKey NO_AUDIO_MESSAGE = L10n.lang("bbs_lip_sync.ui.no_audio.message");
    public static final IKey NO_SHAPES_TITLE = L10n.lang("bbs_lip_sync.ui.no_shapes.title");
    public static final IKey NO_SHAPES_MESSAGE = L10n.lang("bbs_lip_sync.ui.no_shapes.message");
    public static final IKey NO_TEXTURE_MESSAGE = L10n.lang("bbs_lip_sync.ui.no_shapes.no_texture");

    public static final IKey FORM_TITLE = L10n.lang("bbs_lip_sync.ui.form.title");
    public static final IKey FORM_POSES = L10n.lang("bbs_lip_sync.ui.form.poses");
    public static final IKey FORM_TEXTURES = L10n.lang("bbs_lip_sync.ui.form.textures");
    public static final IKey FORM_NOT_ENOUGH = L10n.lang("bbs_lip_sync.ui.form.not_enough");
    public static final IKey FORM_NO_POSES = L10n.lang("bbs_lip_sync.ui.form.no_poses");
    public static final IKey FORM_NO_TEXTURE = L10n.lang("bbs_lip_sync.ui.form.no_texture");
    public static final IKey FORM_PICK_POSE = L10n.lang("bbs_lip_sync.ui.form.pick_pose");
    public static final IKey FORM_RESET = L10n.lang("bbs_lip_sync.ui.form.reset");
    public static final IKey FORM_SAMPLE = L10n.lang("bbs_lip_sync.ui.form.sample");
    public static final IKey FORM_PLAY_TOOLTIP = L10n.lang("bbs_lip_sync.ui.form.play_tooltip");
    public static final IKey FORM_STOP_TOOLTIP = L10n.lang("bbs_lip_sync.ui.form.stop_tooltip");

    public static final IKey ERROR_TITLE = L10n.lang("bbs_lip_sync.ui.error.title");
    public static final IKey ERROR_AUDIO_UNREADABLE = L10n.lang("bbs_lip_sync.ui.error.audio_unreadable");
    public static final IKey ERROR_NATIVE = L10n.lang("bbs_lip_sync.ui.error.native");
    public static final IKey ERROR_MODEL_CORRUPT = L10n.lang("bbs_lip_sync.ui.error.model_corrupt");
    public static final IKey ERROR_LETTERS = L10n.lang("bbs_lip_sync.ui.error.letters");
    public static final IKey ERROR_GENERIC = L10n.lang("bbs_lip_sync.ui.error.generic");

    public static final IKey PROGRESS_LOADING = L10n.lang("bbs_lip_sync.ui.progress.loading");
    public static final IKey PROGRESS_RECOGNIZING = L10n.lang("bbs_lip_sync.ui.progress.recognizing");
    public static final IKey PROGRESS_BAKING = L10n.lang("bbs_lip_sync.ui.progress.baking");
    public static final IKey PROGRESS_DONE = L10n.lang("bbs_lip_sync.ui.progress.done");
    public static final IKey PROGRESS_NO_SPEECH = L10n.lang("bbs_lip_sync.ui.progress.no_speech");
    public static final IKey PROGRESS_CANCELLED = L10n.lang("bbs_lip_sync.ui.progress.cancelled");

    public static final IKey WORDS_SHAPE = L10n.lang("bbs_lip_sync.ui.words.shape");

    public static final IKey MODEL_TITLE = L10n.lang("bbs_lip_sync.ui.model.title");
    public static final IKey MODEL_MESSAGE = L10n.lang("bbs_lip_sync.ui.model.message");
    public static final IKey MODEL_DOWNLOAD = L10n.lang("bbs_lip_sync.ui.model.download");
    public static final IKey MODEL_DOWNLOAD_SIZE = L10n.lang("bbs_lip_sync.ui.model.download_size");
    public static final IKey MODEL_LATER = L10n.lang("bbs_lip_sync.ui.model.later");
    public static final IKey MODEL_LANGUAGE = L10n.lang("bbs_lip_sync.ui.model.language");
    public static final IKey MODEL_PROGRESS = L10n.lang("bbs_lip_sync.ui.model.progress");
    public static final IKey MODEL_PROGRESS_UNKNOWN = L10n.lang("bbs_lip_sync.ui.model.progress_unknown");
    public static final IKey MODEL_SPEED = L10n.lang("bbs_lip_sync.ui.model.speed");
    public static final IKey SIZE_MB = L10n.lang("bbs_lip_sync.ui.size.mb");
    public static final IKey SIZE_GB = L10n.lang("bbs_lip_sync.ui.size.gb");
    public static final IKey MODEL_DOWNLOADING_HINT = L10n.lang("bbs_lip_sync.ui.model.downloading_hint");
    public static final IKey MODEL_EXTRACTING = L10n.lang("bbs_lip_sync.ui.model.extracting");
    public static final IKey MODEL_HIDE = L10n.lang("bbs_lip_sync.ui.model.hide");
    public static final IKey MODEL_CANCEL = L10n.lang("bbs_lip_sync.ui.model.cancel");
    public static final IKey MODEL_RETRY = L10n.lang("bbs_lip_sync.ui.model.retry");
    public static final IKey MODEL_COPY_LINK = L10n.lang("bbs_lip_sync.ui.model.copy_link");
    public static final IKey MODEL_OPEN_FOLDER = L10n.lang("bbs_lip_sync.ui.model.open_folder");
    public static final IKey MODEL_ERROR_NETWORK = L10n.lang("bbs_lip_sync.ui.model.error_network");
    public static final IKey MODEL_ERROR_CORRUPT = L10n.lang("bbs_lip_sync.ui.model.error_corrupt");
    public static final IKey MODEL_ERROR_DISK = L10n.lang("bbs_lip_sync.ui.model.error_disk");
    public static final IKey MODEL_ERROR_HINT = L10n.lang("bbs_lip_sync.ui.model.error_hint");

    public static final IKey CONFIG_MODEL_SMALL = L10n.lang("bbs_lip_sync.config.speech.model_size.small");
    public static final IKey CONFIG_MODEL_LARGE = L10n.lang("bbs_lip_sync.config.speech.model_size.large");
    public static final IKey CONFIG_LANGUAGE_GAME = L10n.lang("bbs_lip_sync.config.speech.language.game");
    public static final IKey TIMING_CUSTOM = L10n.lang("bbs_lip_sync.config.timing.preset.custom");

    public static IKey timingPreset(ValueTimingPreset.Preset preset)
    {
        return L10n.lang("bbs_lip_sync.config.timing.preset." + preset.name().toLowerCase());
    }

    /** A shape as people read it: its main letter, or «closed» for the idle mouth. */
    public static IKey shape(Viseme viseme)
    {
        return L10n.lang("bbs_lip_sync.ui.shape." + viseme.id);
    }

    /** The other letters that make the shape; may be empty. */
    public static IKey shapeMore(Viseme viseme)
    {
        return L10n.lang("bbs_lip_sync.ui.shape." + viseme.id + ".more");
    }

    /** A download size for people: {@code 45 МБ}, {@code 1.8 ГБ}. */
    public static String size(int megabytes)
    {
        return megabytes >= 1024
            ? SIZE_GB.format(String.format("%.1f", megabytes / 1024F)).get()
            : SIZE_MB.format(String.valueOf(megabytes)).get();
    }
}
