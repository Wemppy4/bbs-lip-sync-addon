package com.wemppy.bbs_lip_sync.client;

import com.wemppy.bbs_lip_sync.client.audio.AudioExtractor;
import com.wemppy.bbs_lip_sync.client.bake.KeyframeBaker;
import com.wemppy.bbs_lip_sync.client.bake.MouthRig;
import com.wemppy.bbs_lip_sync.client.model.Language;
import com.wemppy.bbs_lip_sync.client.model.ModelStore;
import com.wemppy.bbs_lip_sync.client.model.SpeechRecognizer;
import com.wemppy.bbs_lip_sync.client.model.Word;
import com.wemppy.bbs_lip_sync.client.phonemes.LetterMap;
import com.wemppy.bbs_lip_sync.client.phonemes.Transcript;
import com.wemppy.bbs_lip_sync.client.phonemes.VisemeTimeline;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * One lip sync generation: audio → words → letters → mouth shapes → keyframes. The heavy part
 * runs on a worker thread; the keyframes are written on the client thread. Only one job runs at
 * a time, and the finished one lingers for a few seconds so the timeline can show its outcome.
 */
public class LipSyncJob
{
    public enum Stage
    {
        LOADING, RECOGNIZING, BAKING, DONE, NO_SPEECH, CANCELLED, FAILED
    }

    private static final long LINGER = 4000;

    private static volatile LipSyncJob current;

    public final KeyframeChannel channel;
    public final int clipTick;
    public final int clipDuration;

    private final String film;
    private final Replay replay;
    private final Link audio;
    private final int offset;
    private final MouthRig rig;
    private final Language language;
    private final Language.Model model;
    private final Runnable onBaked;
    private final Consumer<IKey> onError;
    private Runnable next;

    private volatile Stage stage = Stage.LOADING;
    private volatile int progress;
    private volatile boolean cancelled;
    private volatile long finishedAt;
    private volatile int written;

    public LipSyncJob(String film, Replay replay, KeyframeChannel channel, MouthRig rig, Language language, Language.Model model, Link audio, int offset, int clipTick, int clipDuration, Runnable onBaked, Consumer<IKey> onError)
    {
        this.film = film;
        this.replay = replay;
        this.channel = channel;
        this.rig = rig;
        this.language = language;
        this.model = model;
        this.audio = audio;
        this.offset = offset;
        this.clipTick = clipTick;
        this.clipDuration = clipDuration;
        this.onBaked = onBaked;
        this.onError = onError;
    }

    /** What runs on the client thread once this job is done, or heard no speech; not after a cancel or a failure. */
    public LipSyncJob then(Runnable next)
    {
        this.next = next;

        return this;
    }

    /** The running job, or a just-finished one for a moment; null otherwise. */
    public static LipSyncJob current()
    {
        LipSyncJob job = current;

        if (job != null && job.isFinished() && System.currentTimeMillis() - job.finishedAt > LINGER)
        {
            current = null;

            return null;
        }

        return job;
    }

    public static boolean isBusy()
    {
        LipSyncJob job = current();

        return job != null && !job.isFinished();
    }

    public Stage stage()
    {
        return this.stage;
    }

    public int progress()
    {
        return this.progress;
    }

    public int written()
    {
        return this.written;
    }

    public boolean isFinished()
    {
        return this.stage.ordinal() >= Stage.DONE.ordinal();
    }

    public void cancel()
    {
        this.cancelled = true;
    }

    public void start()
    {
        current = this;

        Thread thread = new Thread(this::run, "BBS lipsync");

        thread.setDaemon(true);
        thread.start();
    }

    @SuppressWarnings("unchecked")
    private void run()
    {
        try
        {
            float[] samples = AudioExtractor.extract(this.audio, this.offset, this.clipDuration);
            LetterMap letters;

            try
            {
                letters = LetterMap.load(this.language.letters);
            }
            catch (Exception e)
            {
                throw new LipSyncException(LipSyncKeys.ERROR_LETTERS.format(this.language.letters), e);
            }

            SpeechRecognizer recognizer;

            try
            {
                recognizer = SpeechRecognizer.get(ModelStore.dir(this.model));
            }
            catch (IOException e)
            {
                throw new LipSyncException(LipSyncKeys.ERROR_MODEL_CORRUPT.format(this.model.name()), e);
            }

            if (this.cancelled)
            {
                this.finish(Stage.CANCELLED);

                return;
            }

            this.stage = Stage.RECOGNIZING;

            List<Word> words = recognizer.recognize(samples, (p) -> this.progress = p, () -> this.cancelled);

            if (words == null)
            {
                this.finish(Stage.CANCELLED);

                return;
            }

            this.stage = Stage.BAKING;

            List<VisemeTimeline.Segment> segments = VisemeTimeline.segments(words, letters, LipSyncSettings.minHold(), LipSyncSettings.minPause(), LipSyncSettings.closeDelay());

            log(words, segments);

            if (!VisemeTimeline.hasSpeech(segments))
            {
                this.finish(Stage.NO_SPEECH);

                return;
            }

            float anticipation = LipSyncSettings.anticipation();
            List<VisemeTimeline.Key> keys = this.rig.isSmooth()
                ? VisemeTimeline.keys(segments, this.clipTick, this.clipDuration, anticipation)
                : VisemeTimeline.steps(segments, this.clipTick, this.clipDuration, anticipation);
            Transcript transcript = Transcript.build(words, letters, this.clipTick, this.clipDuration, anticipation);

            MinecraftClient.getInstance().execute(() ->
            {
                if (this.cancelled)
                {
                    this.finish(Stage.CANCELLED);

                    return;
                }

                try
                {
                    this.written = KeyframeBaker.bake(this.replay, this.channel, this.rig, keys, this.clipTick, this.clipDuration);
                    TranscriptStore.remember(this.film, this.channel, transcript);
                    this.onBaked.run();
                    this.finish(Stage.DONE);
                }
                catch (Exception e)
                {
                    this.fail(LipSyncKeys.ERROR_GENERIC.format(String.valueOf(e)), e);
                }
            });
        }
        catch (LipSyncException e)
        {
            this.fail(e.message, e.getCause());
        }
        catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e)
        {
            this.fail(LipSyncKeys.ERROR_NATIVE, e);
        }
        catch (Throwable e)
        {
            this.fail(LipSyncKeys.ERROR_GENERIC.format(String.valueOf(e)), e);
        }
        finally
        {
            if (LipSyncSettings.unloadModel())
            {
                try
                {
                    SpeechRecognizer.unload();
                }
                catch (Throwable e)
                {
                    /* The library never loaded; nothing to free */
                }
            }

            /* Queued after the baking, so the next job starts once this one wrote its keyframes
             * and let go of the model */
            MinecraftClient.getInstance().execute(() ->
            {
                if ((this.stage == Stage.DONE || this.stage == Stage.NO_SPEECH) && this.next != null)
                {
                    this.next.run();
                }
            });
        }
    }

    /** What was heard and what will be shown, for anyone checking a bad result in the log. */
    private static void log(List<Word> words, List<VisemeTimeline.Segment> segments)
    {
        try
        {
            StringBuilder heard = new StringBuilder();

            for (Word word : words)
            {
                heard.append(String.format("%.2f-%.2f:%s ", word.start(), word.end(), word.text()));
            }

            StringBuilder shapes = new StringBuilder();

            for (VisemeTimeline.Segment segment : segments)
            {
                shapes.append(String.format("%.2f-%.2f:%s ", segment.start(), segment.end(), segment.viseme().id));
            }

            System.out.println("[bbs_lip_sync] words: " + heard);
            System.out.println("[bbs_lip_sync] shapes: " + shapes);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void finish(Stage stage)
    {
        this.stage = stage;
        this.finishedAt = System.currentTimeMillis();
    }

    private void fail(IKey message, Throwable cause)
    {
        if (cause != null)
        {
            cause.printStackTrace();
        }

        this.finish(Stage.FAILED);

        MinecraftClient.getInstance().execute(() -> this.onError.accept(message));
    }
}
