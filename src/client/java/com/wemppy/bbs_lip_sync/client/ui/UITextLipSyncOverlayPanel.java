package com.wemppy.bbs_lip_sync.client.ui;

import com.wemppy.bbs_lip_sync.client.LipSyncKeys;
import com.wemppy.bbs_lip_sync.client.LipSyncSettings;
import com.wemppy.bbs_lip_sync.client.TextLipSync;
import com.wemppy.bbs_lip_sync.client.phonemes.TextSpeech;
import com.wemppy.bbs_lip_sync.client.phonemes.VisemeTimeline;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

/**
 * The "lip sync from text" dialog: a text area that grows with the panel, the speech speed under
 * it, and a bar with how long the result will take (muted) and the one accented button. Enter
 * outside the text area generates; inside it, Enter starts a new line.
 *
 * <p>The text and the speed outlive the dialog, so trying another speed is reopen and press.</p>
 */
public class UITextLipSyncOverlayPanel extends UIOverlayPanel
{
    private static final int WIDTH = 300;

    /** Distance from the panel's edges to its content, as in the model download dialog. */
    private static final int PADDING = 10;

    private static final int GAP = UIConstants.SCROLL_PADDING;
    private static final int TEXT_HEIGHT = 80;
    private static final int TEXT_PADDING = 6;

    /** Everything under the text area: the speed row and the button bar with their gaps. */
    private static final int BELOW_TEXT = GAP + UIConstants.CONTROL_HEIGHT + GAP + UIConstants.CONTROL_HEIGHT + PADDING;

    private final UITextarea<TextLine> text;
    private final UISliderTrackpad speed;
    private final UIButton generate;

    private final Consumer<TextLipSync.Plan> callback;
    private final TextLipSync.Tables tables = new TextLipSync.Tables();

    private String plannedText;
    private float plannedSpeed = -1F;
    private TextLipSync.Plan plan;

    public UITextLipSyncOverlayPanel(Consumer<TextLipSync.Plan> callback)
    {
        super(LipSyncKeys.TEXT_TITLE);

        this.w(WIDTH);
        this.callback = callback;

        this.text = new UITextarea<>(null);
        this.text.background().wrap(true).padding(TEXT_PADDING);
        this.text.setText(TextLipSync.lastText);
        this.text.relative(this.content).x(PADDING).y(PADDING).w(1F, -PADDING * 2).hTo(this.content.area, 1F, -BELOW_TEXT);

        this.speed = new UISliderTrackpad((value) -> LipSyncSettings.textSpeed.set(value.floatValue()));
        this.speed.limit(TextSpeech.MIN_SPEED, TextSpeech.MAX_SPEED).values(0.05D, 0.01D, 0.25D);
        this.speed.snap(0.05D);
        this.speed.setValue(LipSyncSettings.textSpeed());
        this.speed.tooltip(LipSyncKeys.TEXT_SPEED_TOOLTIP);
        this.speed.relative(this.content).x(1F, -PADDING).y(1F, -(PADDING + UIConstants.CONTROL_HEIGHT + GAP)).w(UIConstants.VALUE_WIDTH).h(UIConstants.CONTROL_HEIGHT).anchor(1F, 1F);

        UILabel speedLabel = UI.label(LipSyncKeys.TEXT_SPEED, 0).labelAnchor(0F, 0.5F);

        speedLabel.relative(this.content).x(PADDING).y(1F, -(PADDING + UIConstants.CONTROL_HEIGHT + GAP)).w(1F, -(PADDING * 2 + UIConstants.VALUE_WIDTH + GAP)).h(UIConstants.CONTROL_HEIGHT).anchorY(1F);

        this.generate = new UIButton(LipSyncKeys.TEXT_BUTTON, (b) -> this.generate());

        int width = Math.max(UIConstants.VALUE_WIDTH, Batcher2D.getDefaultTextRenderer().getWidth(LipSyncKeys.TEXT_BUTTON.get()) + 16);

        this.generate.relative(this.content).x(1F, -PADDING).y(1F, -PADDING).w(width).h(UIConstants.CONTROL_HEIGHT).anchor(1F, 1F);

        this.content.add(this.text, speedLabel, this.speed, this.generate);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        List<TextLine> lines = this.text.getLines();

        this.text.cursor.set(lines.size() - 1, lines.get(lines.size() - 1).text.length());
        parent.getContext().focus(this.text);
    }

    @Override
    public int getContentHeight()
    {
        /* The title bar, the text area at its default height and what's under it; once the user
         * drags the corner, the size is theirs */
        return this.wasResized() ? -1 : 20 + PADDING + TEXT_HEIGHT + BELOW_TEXT;
    }

    /** Enter does what the accented button does (inside the text area it makes a new line instead). */
    @Override
    public void confirm()
    {
        this.generate();
    }

    @Override
    public void onClose()
    {
        TextLipSync.lastText = this.text.getText();

        super.onClose();
    }

    private void generate()
    {
        TextLipSync.lastText = this.text.getText();

        /* Fresh, in case the timing settings changed while the dialog was open */
        TextLipSync.Plan plan = TextLipSync.plan(TextLipSync.lastText, LipSyncSettings.textSpeed(), this.tables);

        if (!plan.hasSpeech())
        {
            return;
        }

        this.close();
        this.callback.accept(plan);
    }

    /** The plan for what's typed now; worked out again only when the text or the speed changed. */
    private TextLipSync.Plan plan()
    {
        String current = this.text.getText();
        float speed = LipSyncSettings.textSpeed();

        if (this.plan == null || !current.equals(this.plannedText) || speed != this.plannedSpeed)
        {
            this.plan = TextLipSync.plan(current, speed, this.tables);
            this.plannedText = current;
            this.plannedSpeed = speed;
        }

        return this.plan;
    }

    @Override
    public void render(UIContext context)
    {
        TextLipSync.Plan plan = this.plan();
        boolean blank = this.plannedText.isBlank();

        this.generate.setEnabled(plan.hasSpeech());

        super.render(context);

        FontRenderer font = context.batcher.getFont();

        if (blank)
        {
            int y = this.text.area.y + TEXT_PADDING;

            for (String line : font.wrap(LipSyncKeys.TEXT_PLACEHOLDER.get(), this.text.area.w - TEXT_PADDING * 2))
            {
                if (y + font.getHeight() > this.text.area.ey())
                {
                    break;
                }

                context.batcher.text(line, this.text.area.x + TEXT_PADDING, y, Colors.GRAY);
                y += 12;
            }
        }

        String summary = blank ? ""
            : plan.hasSpeech() ? LipSyncKeys.TEXT_DURATION.format(String.format("%.1f", plan.ticks() / VisemeTimeline.TPS), String.valueOf(plan.ticks())).get()
            : LipSyncKeys.TEXT_NO_LETTERS.get();
        int left = this.content.area.x + PADDING;

        summary = font.limitToWidth(summary, this.generate.area.x - GAP - left);
        context.batcher.text(summary, left, this.generate.area.my(font.getHeight()), Colors.GRAY);
    }
}
