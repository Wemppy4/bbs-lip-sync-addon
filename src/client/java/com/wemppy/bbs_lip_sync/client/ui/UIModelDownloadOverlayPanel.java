package com.wemppy.bbs_lip_sync.client.ui;

import com.wemppy.bbs_lip_sync.client.LipSyncKeys;
import com.wemppy.bbs_lip_sync.client.LipSyncSettings;
import com.wemppy.bbs_lip_sync.client.model.Language;
import com.wemppy.bbs_lip_sync.client.model.ModelStore;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;

/**
 * The "download the speech model" dialog for one language. It mirrors {@link ModelStore}'s state
 * each frame — offer, downloading, unpacking, error — and closes itself once the language's model
 * is on disk; the download keeps going if the dialog is hidden.
 *
 * <p>Layout: a card with the language and the model it needs (the language can be changed right
 * there, in case the game's language isn't the one spoken), one muted line of explanation, and a
 * button bar where only the main action — download, hide, retry — wears the accent colour. The
 * rest are flat buttons on the right or plain icons on the left. Enter runs the main action.</p>
 *
 * <p>It opens as tall as its content; once the user drags its corner, the size is theirs.</p>
 */
public class UIModelDownloadOverlayPanel extends UIOverlayPanel
{
    private static final int WIDTH = 300;

    /** Distance from the panel's edges to its content, as in BBS's own message dialogs. */
    private static final int PADDING = 10;

    /** Gap between the card, the progress bar and the text. */
    private static final int GAP = UIConstants.SCROLL_PADDING;

    private static final int CARD_HEIGHT = 32;
    private static final int PROGRESS_HEIGHT = 20;
    private static final int ERROR_COLOR = 0xffff8a80;

    private final UIElement card;
    private final UIElement progress;
    private final UIText message;
    private final UIText hint;

    private final UIButton download;
    private final UIButton later;
    private final UIButton hide;
    private final UIButton cancel;
    private final UIButton retry;
    private final UIButton changeLanguage;
    private final UIIcon folder;
    private final UIIcon copyLink;

    private Language current;
    private ModelStore.State shown;
    private ModelStore.Error shownError;
    private Language shownLanguage;

    public UIModelDownloadOverlayPanel(Language language)
    {
        super(LipSyncKeys.MODEL_TITLE);

        this.w(WIDTH);
        this.current = language;

        this.card = new Card();
        this.card.h(CARD_HEIGHT);
        this.progress = new Progress();
        this.progress.h(PROGRESS_HEIGHT);
        this.message = new UIText();
        this.hint = new UIText();
        this.hint.color(Colors.GRAY, false);

        UIElement column = UI.column(GAP, 0, this.card, this.progress, this.message, this.hint);

        column.relative(this.content).x(PADDING).y(PADDING).w(1F, -PADDING * 2);

        this.download = new UIButton(LipSyncKeys.MODEL_DOWNLOAD, (b) -> this.startDownload());
        this.retry = new UIButton(LipSyncKeys.MODEL_RETRY, (b) -> this.startDownload());
        this.hide = new UIButton(LipSyncKeys.MODEL_HIDE, (b) -> this.close());
        this.later = new UIFlatButton(LipSyncKeys.MODEL_LATER, (b) -> this.close());
        this.cancel = new UIFlatButton(LipSyncKeys.MODEL_CANCEL, (b) -> ModelStore.cancelDownload());

        this.changeLanguage = new UIFlatButton(LipSyncKeys.MODEL_LANGUAGE, (b) -> this.pickLanguage());
        this.changeLanguage.relative(this.card).x(1F, -UIConstants.SCROLL_PADDING).y(0.5F).w(width(this.changeLanguage.label, false)).anchor(1F, 0.5F);
        this.card.add(this.changeLanguage);

        this.folder = new UIIcon(Icons.FOLDER, (b) -> this.openFolder());
        this.folder.tooltip(LipSyncKeys.MODEL_OPEN_FOLDER);
        this.folder.relative(this.content).x(PADDING).y(1F, -PADDING).wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT).anchorY(1F);

        this.copyLink = new UIIcon(Icons.LINK, (b) -> this.copyLink());
        this.copyLink.tooltip(LipSyncKeys.MODEL_COPY_LINK);
        this.copyLink.relative(this.folder).x(1F, UIConstants.MARGIN).y(0).wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);

        this.content.add(column, this.download, this.retry, this.hide, this.later, this.cancel, this.folder, this.copyLink);

        /* A new request starts from a clean offer, not from how an earlier download ended */
        ModelStore.clearError();
        this.apply(ModelStore.state(), ModelStore.error());
    }

    private Language.Model model()
    {
        return this.current == null ? null : this.current.model(LipSyncSettings.largeModel());
    }

    private void startDownload()
    {
        Language.Model model = this.model();

        if (model != null)
        {
            ModelStore.download(model);
        }
    }

    private void pickLanguage()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        LanguagePicker.open(context, (picked) ->
        {
            this.current = picked;

            ModelStore.clearError();

            if (ModelStore.isReady(this.model()))
            {
                /* Nothing to download after all: carry on with the generation that was waiting */
                ModelStore.fireReady();
            }
        });
    }

    private void openFolder()
    {
        ModelStore.folder().mkdirs();
        UIUtils.openFolder(ModelStore.folder());
    }

    private void copyLink()
    {
        Language.Model model = this.model();

        if (model != null)
        {
            MinecraftClient.getInstance().keyboard.setClipboard(model.url());
        }
    }

    @Override
    public int getContentHeight()
    {
        /* The user sized it: keep their height */
        if (this.wasResized() || this.message.area.h <= 0)
        {
            return -1;
        }

        int bottom = this.message.area.ey();

        if (this.hint.isVisible() && this.hint.area.h > 0)
        {
            bottom = Math.max(bottom, this.hint.area.ey());
        }

        return bottom - this.area.y + PADDING + UIConstants.CONTROL_HEIGHT + PADDING;
    }

    /** Enter does what the accented button does. */
    @Override
    public void confirm()
    {
        if (this.download.isVisible() || this.retry.isVisible())
        {
            this.startDownload();
        }
        else if (this.hide.isVisible())
        {
            this.close();
        }
    }

    private void apply(ModelStore.State state, ModelStore.Error error)
    {
        this.shown = state;
        this.shownError = error;
        this.shownLanguage = this.current;

        boolean asking = state == ModelStore.State.IDLE;
        boolean downloading = state == ModelStore.State.DOWNLOADING;
        boolean extracting = state == ModelStore.State.EXTRACTING;
        boolean failed = state == ModelStore.State.ERROR;
        Language.Model model = this.model();

        this.changeLanguage.setVisible(asking || failed);
        this.progress.setVisible(downloading || extracting);
        this.hint.setVisible(failed);

        this.download.setVisible(asking && model != null);
        this.later.setVisible(asking || failed);
        this.hide.setVisible(downloading || extracting);
        this.cancel.setVisible(downloading);
        this.retry.setVisible(failed);
        this.folder.setVisible(asking || failed);
        this.copyLink.setVisible(failed);

        this.message.color(Colors.LIGHTEST_GRAY, false);

        if (asking)
        {
            this.download.label = model != null && model.megabytes() > 0
                ? LipSyncKeys.MODEL_DOWNLOAD_SIZE.format(LipSyncKeys.size(model.megabytes()))
                : LipSyncKeys.MODEL_DOWNLOAD;

            this.message.text(LipSyncKeys.MODEL_MESSAGE);
            this.placeRight(this.download, this.later);
        }
        else if (downloading)
        {
            this.message.text(LipSyncKeys.MODEL_DOWNLOADING_HINT);
            this.placeRight(this.hide, this.cancel);
        }
        else if (extracting)
        {
            this.message.text(LipSyncKeys.MODEL_EXTRACTING);
            this.placeRight(this.hide);
        }
        else if (failed)
        {
            IKey reason = error == ModelStore.Error.CORRUPT ? LipSyncKeys.MODEL_ERROR_CORRUPT
                : error == ModelStore.Error.DISK ? LipSyncKeys.MODEL_ERROR_DISK
                : LipSyncKeys.MODEL_ERROR_NETWORK;

            this.message.text(reason);
            this.message.color(ERROR_COLOR, false);
            this.hint.text(LipSyncKeys.MODEL_ERROR_HINT);
            this.placeRight(this.retry, this.later);
        }

        UIElement parent = this.getParent();

        if (parent != null)
        {
            parent.resize();
        }
        else
        {
            this.resize();
        }
    }

    /** Lines buttons up along the bottom right, the first one rightmost, each as wide as its label needs. */
    private void placeRight(UIButton... buttons)
    {
        int offset = PADDING;

        for (UIButton button : buttons)
        {
            int width = width(button.label, !(button instanceof UIFlatButton));

            button.relative(this.content).x(1F, -offset).y(1F, -PADDING).w(width).h(UIConstants.CONTROL_HEIGHT).anchor(1F, 1F);
            offset += width + UIConstants.MARGIN;
        }
    }

    /** A label's width plus room on both sides; the main button is never narrower than a settings control. */
    private static int width(IKey label, boolean main)
    {
        int text = Batcher2D.getDefaultTextRenderer().getWidth(label.get()) + 16;

        return main ? Math.max(UIConstants.VALUE_WIDTH, text) : Math.max(50, text);
    }

    @Override
    public void render(UIContext context)
    {
        ModelStore.State state = ModelStore.state();

        if (state == ModelStore.State.IDLE && ModelStore.isReady(this.model()))
        {
            /* Not mid-render: the overlay is being drawn right now */
            MinecraftClient.getInstance().send(this::close);

            return;
        }

        if (state != this.shown || ModelStore.error() != this.shownError || this.current != this.shownLanguage)
        {
            this.apply(state, ModelStore.error());
        }

        super.render(context);
    }

    private static String megabytes(long bytes)
    {
        return String.format("%.1f", bytes / 1048576D);
    }

    /** The language and the model it needs, on a sunken strip with a globe. */
    private class Card extends UIElement
    {
        @Override
        public void render(UIContext context)
        {
            Language language = UIModelDownloadOverlayPanel.this.current;
            Language.Model model = UIModelDownloadOverlayPanel.this.model();
            FontRenderer font = context.batcher.getFont();

            this.area.render(context.batcher, BBSSettings.deepSurface() | Colors.A100);
            context.batcher.icon(Icons.GLOBE, Colors.LIGHTEST_GRAY, this.area.x + UIConstants.SCROLL_PADDING, this.area.my(), 0F, 0.5F);

            int x = this.area.x + UIConstants.SCROLL_PADDING + 16 + UIConstants.SCROLL_PADDING;
            int right = changeLanguage.isVisible() ? changeLanguage.area.x - UIConstants.SCROLL_PADDING : this.area.ex() - UIConstants.SCROLL_PADDING;
            String name = language == null ? "?" : language.name;
            String details = model == null ? "" : (model.megabytes() > 0 ? model.name() + " · " + LipSyncKeys.size(model.megabytes()) : model.name());
            int top = this.area.my() - font.getHeight() - 1;

            context.batcher.text(font.limitToWidth(name, right - x), x, top, Colors.WHITE);
            context.batcher.text(font.limitToWidth(details, right - x), x, this.area.my() + 1, Colors.GRAY);

            super.render(context);
        }
    }

    /** The download bar: how much is in, out of how much, and how fast; a full bar while unpacking. */
    private class Progress extends UIElement
    {
        @Override
        public void render(UIContext context)
        {
            int x = this.area.x;
            int w = this.area.w;
            int y = this.area.y;
            boolean extracting = ModelStore.state() == ModelStore.State.EXTRACTING;
            long received = ModelStore.received();
            long total = ModelStore.total();
            float fraction = extracting ? 1F : (total > 0 ? Math.min(1F, received / (float) total) : 0F);

            context.batcher.box(x, y, x + w, y + 4, BBSSettings.deepSurface() | Colors.A100);
            context.batcher.box(x, y, x + w * fraction, y + 4, BBSSettings.primaryColor(Colors.A100));

            if (!extracting)
            {
                FontRenderer font = context.batcher.getFont();
                String left = total > 0
                    ? LipSyncKeys.MODEL_PROGRESS.format(megabytes(received), megabytes(total)).get()
                    : LipSyncKeys.MODEL_PROGRESS_UNKNOWN.format(megabytes(received)).get();
                String right = ModelStore.speed() > 0 ? LipSyncKeys.MODEL_SPEED.format(megabytes(ModelStore.speed())).get() : "";

                context.batcher.text(left, x, y + 9, Colors.GRAY);
                context.batcher.text(right, x + w - font.getWidth(right), y + 9, Colors.GRAY);
            }

            super.render(context);
        }
    }
}
