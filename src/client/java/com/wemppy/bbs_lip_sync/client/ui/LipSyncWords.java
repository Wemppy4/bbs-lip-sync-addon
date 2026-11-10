package com.wemppy.bbs_lip_sync.client.ui;

import com.wemppy.bbs_lip_sync.client.LipSyncKeys;
import com.wemppy.bbs_lip_sync.client.TranscriptStore;
import com.wemppy.bbs_lip_sync.client.phonemes.Transcript;
import com.wemppy.bbs_lip_sync.client.phonemes.Viseme;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.tooltips.ITooltip;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows what was said over a lip synced track: a strip of words along the bottom of the row,
 * split into syllables, and the mouth shape's letter next to each keyframe that has room for it.
 * Hovering a word or a keyframe outlines the word's keyframes and shows the word with the
 * syllable under the mouse; a click on the strip selects the word's keyframes.
 *
 * <p>Rows tall enough (Alt + wheel) also carry the words written along the top.</p>
 *
 * <p>Over one of the lip sync's keyframes BBS's own preview of the form gives way to the word.
 * The letters need nothing but the keyframes' colours, so they show on any lip synced track,
 * words or not.</p>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class LipSyncWords implements IUIElement
{
    /** Height of the word strip at the bottom of the row. */
    private static final int STRIP = 3;

    /** Rows at least this tall have room for the words above the keyframes. */
    private static final int TALL = 28;

    /** How close (px) the mouse has to be to a keyframe, the same as BBS's own picking. */
    private static final int NEAR = 5;

    private final UIKeyframeEditor editor;

    public LipSyncWords(UIKeyframeEditor editor)
    {
        this.editor = editor;
    }

    @Override
    public boolean isEnabled()
    {
        return true;
    }

    @Override
    public boolean canBeRendered(Area viewport)
    {
        return true;
    }

    /** The timeline's keyframe preview, held back while a lip sync keyframe is hovered. */
    public ITooltip wrapPreview(ITooltip preview)
    {
        return new ITooltip()
        {
            @Override
            public IKey getLabel()
            {
                return preview.getLabel();
            }

            @Override
            public void renderTooltip(UIContext context)
            {
                Row row = LipSyncWords.this.hoveredRow(context);

                if (row == null || row.hoveredKey == null)
                {
                    preview.renderTooltip(context);
                }
            }
        };
    }

    /* Input */

    @Override
    public IUIElement mouseClicked(UIContext context)
    {
        if (context.mouseButton != 0)
        {
            return null;
        }

        Row row = this.hoveredRow(context);

        if (row == null || row.hoveredKey != null || row.hoveredWord == null || !row.onStrip(context.mouseY, context.batcher.getFont()))
        {
            return null;
        }

        UIKeyframes view = this.editor.view;
        UIKeyframeDopeSheet dope = view.getDopeSheet();
        List<Keyframe> keyframes = row.sheet.channel.getKeyframes();
        Keyframe first = null;

        dope.clearSelection();

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe keyframe = keyframes.get(i);

            if (row.isOwn(keyframe) && row.hoveredWord.contains(keyframe.getTick()))
            {
                row.sheet.selection.add(i);

                if (first == null)
                {
                    first = keyframe;
                }
            }
        }

        view.pickKeyframe(first);

        return this;
    }

    /* Drawing */

    @Override
    public void render(UIContext context)
    {
        UIKeyframes view = this.editor.view;
        UIKeyframeDopeSheet dope = view.getDopeSheet();

        if (view.getGraph() != dope)
        {
            return;
        }

        Area area = view.graphArea;
        int top = TimelineRulerRenderer.getRulerBottom(area);
        Row hovered = this.hoveredRow(context);
        Row pill = null;

        context.batcher.clipBox(area.x, top, area.ex(), area.ey(), context);

        for (UIKeyframeSheet sheet : dope.getSheets())
        {
            Row row = hovered != null && hovered.sheet == sheet ? hovered : this.row(sheet, null);

            if (row == null || row.y + row.h <= top || row.y >= area.ey())
            {
                continue;
            }

            this.renderRow(context, row);

            if (row == hovered && (row.hoveredWord != null || row.hoveredKey != null))
            {
                pill = row;
            }
        }

        context.batcher.unclip(context);

        if (pill != null)
        {
            this.renderPill(context, pill, area);
        }
    }

    private void renderRow(UIContext context, Row row)
    {
        UIKeyframes view = this.editor.view;
        FontRenderer font = context.batcher.getFont();
        boolean tall = row.h >= TALL;
        int stripY = row.y + row.h - STRIP;

        for (Transcript.Span word : row.words())
        {
            int x1 = view.toGraphX(word.start());
            int x2 = view.toGraphX(word.end());

            if (x2 < view.graphArea.x || x1 > view.graphArea.ex())
            {
                continue;
            }

            boolean active = word == row.hoveredWord;

            if (active)
            {
                context.batcher.box(x1, row.y, x2, row.y + row.h, BBSSettings.primaryColor(Colors.A12));
            }

            for (int i = 0; i < word.syllables().size(); i++)
            {
                Transcript.Syllable syllable = word.syllables().get(i);
                int sx1 = view.toGraphX(syllable.start());
                int sx2 = Math.max(sx1 + 1, view.toGraphX(syllable.end()) - (i + 1 < word.syllables().size() ? 1 : 0));
                int color = active
                    ? (i % 2 == 0 ? Colors.WHITE : Colors.LIGHTER_GRAY)
                    : BBSSettings.primaryColor(i % 2 == 0 ? Colors.A100 : Colors.A50);

                context.batcher.box(sx1, stripY, sx2, row.y + row.h, color);
            }

            if (tall && x2 - x1 > 6)
            {
                String label = font.limitToWidth(word.text(), x2 - x1 - 4);

                context.batcher.textShadow(label, x1 + 2, row.y + 3, active ? Colors.WHITE : Colors.LIGHTEST_GRAY);
            }
        }

        this.renderKeys(context, row);
    }

    /** The shape's letter right of each keyframe with room for it; rings around the hovered word's ones. */
    private void renderKeys(UIContext context, Row row)
    {
        UIKeyframes view = this.editor.view;
        FontRenderer font = context.batcher.getFont();
        int my = row.y + row.h / 2;
        List<Keyframe> own = new ArrayList<>();

        for (Object object : row.sheet.channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) object;

            if (row.isOwn(keyframe))
            {
                own.add(keyframe);
            }
        }

        for (int i = 0; i < own.size(); i++)
        {
            Keyframe keyframe = own.get(i);
            int x = view.toGraphX(keyframe.getTick());

            if (x < view.graphArea.x - 20 || x > view.graphArea.ex())
            {
                continue;
            }

            if (row.hoveredWord != null && row.hoveredWord.contains(keyframe.getTick()))
            {
                context.batcher.outlineCenter(x, my, 5, Colors.WHITE);
            }

            Viseme viseme = Viseme.byColor(keyframe.getStyle().getColor());
            Keyframe previous = i > 0 ? own.get(i - 1) : null;

            /* A hold keyframe repeats the shape before it, so its letter would only be noise */
            if (viseme == null || (previous != null && Viseme.byColor(previous.getStyle().getColor()) == viseme))
            {
                continue;
            }

            String letter = letter(viseme);
            int next = i + 1 < own.size() ? view.toGraphX(own.get(i + 1).getTick()) : Integer.MAX_VALUE;

            if (next - x >= font.getWidth(letter) + 9)
            {
                context.batcher.textShadow(letter, x + 5, my - font.getHeight() / 2, viseme.rgb | Colors.A100);
            }
        }
    }

    /** The hovered word over its row, syllables apart, the one under the mouse lit; and the shape if a keyframe is hovered. */
    private void renderPill(UIContext context, Row row, Area area)
    {
        FontRenderer font = context.batcher.getFont();
        Transcript.Span word = row.hoveredWord;
        List<Transcript.Syllable> syllables = word == null ? List.of() : word.syllables();
        Transcript.Syllable current = word == null ? null : word.syllableAt(row.tick);
        String dot = " · ";
        String shape = null;

        if (row.hoveredKey != null)
        {
            Viseme viseme = Viseme.byColor(row.hoveredKey.getStyle().getColor());

            if (viseme != null)
            {
                shape = LipSyncKeys.WORDS_SHAPE.format(LipSyncKeys.shape(viseme).get()).get();
            }
        }

        int width = 0;

        for (int i = 0; i < syllables.size(); i++)
        {
            width += font.getWidth(syllables.get(i).text()) + (i > 0 ? font.getWidth(dot) : 0);
        }

        String gap = syllables.isEmpty() ? "" : "   ";

        if (shape == null && syllables.isEmpty())
        {
            return;
        }

        if (shape != null)
        {
            width += font.getWidth(gap + shape);
        }

        int h = font.getHeight() + 6;
        int x = this.editor.view.toGraphX(word != null ? word.start() : row.hoveredKey.getTick());
        int y = row.y - h - 1;

        x = Math.max(area.x + 2, Math.min(area.ex() - width - 8, x));

        if (y < TimelineRulerRenderer.getRulerBottom(area))
        {
            y = row.y + row.h + 1;
        }

        context.batcher.box(x, y, x + width + 6, y + h, Colors.A90);
        context.batcher.box(x, y + h - 1, x + width + 6, y + h, BBSSettings.primaryColor(Colors.A100));

        int cursor = x + 3;
        int textY = y + 3;

        for (int i = 0; i < syllables.size(); i++)
        {
            Transcript.Syllable syllable = syllables.get(i);

            if (i > 0)
            {
                context.batcher.text(dot, cursor, textY, Colors.GRAY);
                cursor += font.getWidth(dot);
            }

            context.batcher.textShadow(syllable.text(), cursor, textY, syllable == current ? BBSSettings.primaryColor(Colors.A100) : Colors.WHITE);
            cursor += font.getWidth(syllable.text());
        }

        if (shape != null)
        {
            context.batcher.textShadow(gap + shape, cursor, textY, Colors.LIGHTER_GRAY);
        }
    }

    /** Big letter of the shape; the closed mouth is a dash. */
    private static String letter(Viseme viseme)
    {
        if (viseme == Viseme.IDLE)
        {
            return "-";
        }

        String name = LipSyncKeys.shape(viseme).get();

        return name.isEmpty() ? viseme.id.toUpperCase() : name.substring(0, name.offsetByCodePoints(0, 1));
    }

    /* Rows */

    /** The lip synced row under the mouse, with what in it is hovered. */
    private Row hoveredRow(UIContext context)
    {
        UIKeyframes view = this.editor.view;

        if (view.getGraph() != view.getDopeSheet() || !view.graphArea.isInside(context) || context.mouseY < TimelineRulerRenderer.getRulerBottom(view.graphArea))
        {
            return null;
        }

        UIKeyframeSheet sheet = view.getDopeSheet().getSheet(context.mouseY);

        return sheet == null ? null : this.row(sheet, context);
    }

    /** The film being edited, whose saved transcripts the tracks look in. */
    private String film()
    {
        UIFilmPanel panel = this.editor.getParent(UIFilmPanel.class);
        Film film = panel == null ? null : panel.getData();

        return film == null ? null : film.getId();
    }

    /** A sheet's row if it has lip sync keyframes on it, else null. */
    private Row row(UIKeyframeSheet sheet, UIContext context)
    {
        UIKeyframeDopeSheet dope = this.editor.view.getDopeSheet();
        int y = dope.getDopeSheetY(sheet);

        /* A folded away row has no place of its own */
        if (dope.getSheet(y) != sheet)
        {
            return null;
        }

        Row row = new Row(sheet, TranscriptStore.of(this.film(), sheet.channel), y, dope.getTrackHeight(sheet));
        boolean any = false;

        for (Object object : sheet.channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) object;

            if (row.isOwn(keyframe))
            {
                any = true;

                if (context != null && Math.abs(this.editor.view.toGraphX(keyframe.getTick()) - context.mouseX) < NEAR && Math.abs(y + row.h / 2 - context.mouseY) < NEAR)
                {
                    row.hoveredKey = keyframe;
                }
            }
        }

        /* Undone or deleted by hand: nothing left to explain */
        if (!any)
        {
            return null;
        }

        if (context != null)
        {
            row.tick = row.hoveredKey != null ? row.hoveredKey.getTick() : (float) this.editor.view.fromGraphX(context.mouseX);
            row.hoveredWord = row.wordAt(row.tick);
        }

        return row;
    }

    private static class Row
    {
        final UIKeyframeSheet sheet;
        final List<Transcript> transcripts;
        final int y;
        final int h;

        Keyframe hoveredKey;
        Transcript.Span hoveredWord;
        float tick;

        Row(UIKeyframeSheet sheet, List<Transcript> transcripts, int y, int h)
        {
            this.sheet = sheet;
            this.transcripts = transcripts;
            this.y = y;
            this.h = h;
        }

        /** One of the lip sync's keyframes: in its colours, and inside a known lip sync if there is one. */
        boolean isOwn(Keyframe keyframe)
        {
            if (!Viseme.isOwnColor(keyframe.getStyle().getColor()))
            {
                return false;
            }

            if (this.transcripts.isEmpty())
            {
                return true;
            }

            for (Transcript transcript : this.transcripts)
            {
                if (transcript.covers(keyframe.getTick()))
                {
                    return true;
                }
            }

            return false;
        }

        List<Transcript.Span> words()
        {
            List<Transcript.Span> words = new ArrayList<>();

            for (Transcript transcript : this.transcripts)
            {
                words.addAll(transcript.words);
            }

            return words;
        }

        Transcript.Span wordAt(float tick)
        {
            for (Transcript transcript : this.transcripts)
            {
                Transcript.Span word = transcript.covers(tick) ? transcript.wordAt(tick) : null;

                if (word != null)
                {
                    return word;
                }
            }

            return null;
        }

        /** The strip at the bottom, and the written words at the top of a tall row. */
        boolean onStrip(int mouseY, FontRenderer font)
        {
            return mouseY >= this.y + this.h - STRIP - 2 || (this.h >= TALL && mouseY < this.y + font.getHeight() + 4);
        }
    }
}
