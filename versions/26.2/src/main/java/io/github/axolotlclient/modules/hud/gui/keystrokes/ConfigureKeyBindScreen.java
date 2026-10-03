/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
 *
 * This file is part of AxolotlClient.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * For more information, see the LICENSE file.
 */

package io.github.axolotlclient.modules.hud.gui.keystrokes;

import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.vanilla.widgets.IntegerWidget;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.ConfigStyles;
import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud;
import io.github.axolotlclient.modules.hud.gui.layout.Justification;
import io.github.axolotlclient.util.DrawUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ConfigureKeyBindScreen extends Screen {

	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final Screen parent;
	private final KeystrokeHud hud;
	public final KeystrokeHud.Keystroke stroke;
	private final IntegerOption width;
	private final IntegerOption height;
	private final boolean isAddScreen;
	private Button addButton, synchronizeButton;
	private StringWidget currentKey;

	public ConfigureKeyBindScreen(Screen parent, KeystrokeHud hud, KeystrokeHud.Keystroke stroke, boolean isAddScreen) {
		super(Component.translatable("keystrokes.stroke.configure_stroke"));
		this.parent = parent;
		this.hud = hud;
		this.stroke = stroke;

		width = new IntegerOption("", stroke.getBounds().width(), v -> stroke.getBounds().width(v), 7, 100);
		height = new IntegerOption("", stroke.getBounds().height(), v -> stroke.getBounds().height(v), 7, 100);
		this.isAddScreen = isAddScreen;
	}

	@Override
	protected void init() {
		layout.addTitleHeader(getTitle(), font);

		var body = LinearLayout.vertical().spacing(8);
		body.defaultCellSetting().alignVerticallyTop();
		var labelFrame = new FrameLayout();
		labelFrame.setMinWidth(super.width);
		labelFrame.addChild(new AbstractWidget(0, 0, 200, 40, Component.empty()) {
			@Override
			protected void extractWidgetRenderState(@NotNull GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
				var rect = stroke.getRenderPosition();
				guiGraphicsExtractor.pose().pushMatrix();
				guiGraphicsExtractor.pose().translate(getX(), getY());
				float scale = Math.min((float) getHeight() / rect.height(), (float) getWidth() / rect.width());
				guiGraphicsExtractor.pose().translate(getWidth() / 2f - (rect.width() * scale) / 2f, 0);
				guiGraphicsExtractor.pose().scale(scale, scale);
				guiGraphicsExtractor.pose().translate(-rect.x(), -rect.y());
				DrawUtil.fillRect(guiGraphicsExtractor, rect, Colors.WHITE.withAlpha(128));
				stroke.render(guiGraphicsExtractor);
				guiGraphicsExtractor.pose().popMatrix();
			}

			@Override
			protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {

			}
		}).active = false;
		body.addChild(labelFrame);
		currentKey = body.addChild(new StringWidget(Component.empty(), font), LayoutSettings::alignHorizontallyCenter);

		var optionsFrame = new FrameLayout();
		optionsFrame.setMinWidth(super.width);
		var contents = LinearLayout.horizontal().spacing(4);
		contents.defaultCellSetting().alignHorizontallyCenter();
		var names = contents.addChild(LinearLayout.vertical().spacing(8));
		var options = contents.addChild(LinearLayout.vertical().spacing(8));
		if (stroke.isLabelEditable()) {
			names.addChild(new StringWidget(150, 20, Component.translatable("keystrokes.stroke.label"), font));
			boolean supportsSynchronization = stroke instanceof KeystrokeHud.LabelKeystroke;

			LinearLayout labelLayout = options.addChild(LinearLayout.horizontal()).spacing(4);
			var label = labelLayout.addChild(new EditBox(font, supportsSynchronization ? 30 : 150, 20, Component.empty()));
			label.setValue(stroke.getLabel());
			label.setResponder(stroke::setLabel);
			if (supportsSynchronization) {
				var s = (KeystrokeHud.LabelKeystroke) stroke;
				synchronizeButton = labelLayout.addChild(Button.builder(Component.translatable("keystrokes.stroke.label.synchronize_with_key", s.isSynchronizeLabel() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), b -> {
					s.setSynchronizeLabel(!s.isSynchronizeLabel());
					b.setMessage(Component.translatable("keystrokes.stroke.label.synchronize_with_key", s.isSynchronizeLabel() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
					label.setEditable(!s.isSynchronizeLabel());
					if (s.isSynchronizeLabel()) {
						label.setValue(stroke.getLabel());
					}
				}).width(58).build());
				synchronizeButton.active = s.getKey() != null;
				label.setEditable(!s.isSynchronizeLabel());
				labelLayout.addChild(CycleButton.<Justification>builder(j -> Component.translatable(j.toString()), s::getJustification).withValues(Justification.values())
					.create(0, 0, 58, 20, Component.translatable("justification"),
						(btn, val) -> s.setJustification(val)));
			}
		}
		if (stroke instanceof KeystrokeHud.CustomRenderKeystroke customRender) {
			names.addChild(new StringWidget(150, 20, Component.translatable("keystrokes.stroke.graphics"), font));
			var graphicsLayout = options.addChild(LinearLayout.horizontal()).spacing(4);
			var sliderMin = 2;
			var sliderMax = 25;
			var sizeSlider = new AbstractSliderButton(0, 0, 98, 20, Component.translatable("keystrokes.stroke.configure_graphics_size", customRender.getSize()), (customRender.getSize() - sliderMin) / ((float) sliderMax - sliderMin)) {
				@Override
				protected void updateMessage() {
					setMessage(Component.translatable("keystrokes.stroke.configure_graphics_size", (int) (value * (sliderMax - sliderMin) + sliderMin)));
				}

				@Override
				protected void applyValue() {
					int size = (int) (value * (sliderMax - sliderMin) + sliderMin);
					this.value = (size - sliderMin) / ((float) sliderMax - sliderMin);
					customRender.setSize(size);
				}
			};
			graphicsLayout.addChild(sizeSlider);
			var widget = (AbstractButton) ConfigStyles.createWidget(0, 0, 48, 20, customRender.getGraphics());
			graphicsLayout.addChild(Button.builder(Component.translatable("keystrokes.stroke.configure_graphics"), btn ->
				widget.onPress(new KeyEvent(0, 0, 0))).width(48).build());
		}
		names.addChild(new StringWidget(150, 20, Component.translatable("keystrokes.stroke.width"), font));
		options.addChild(new IntegerWidget(0, 0, 150, 20, width));
		names.addChild(new StringWidget(150, 20, Component.translatable("keystrokes.stroke.height"), font));
		options.addChild(new IntegerWidget(0, 0, 150, 20, height));
		optionsFrame.addChild(contents);
		body.addChild(optionsFrame);

		var buttonsFrame = new FrameLayout();
		buttonsFrame.setMinWidth(super.width);
		var row4 = LinearLayout.horizontal().spacing(8);
		row4.defaultCellSetting().alignHorizontallyCenter();
		row4.addChild(Button.builder(Component.translatable("keystrokes.stroke.configure_key"), b ->
			minecraft.gui.setScreen(new KeyBindSelectionScreen(this, stroke))).width(150).build());
		row4.addChild(Button.builder(Component.translatable("keystrokes.stroke.configure_position"), b ->
			minecraft.gui.setScreen(new KeystrokePositioningScreen(this, hud, stroke))).width(150).build());
		buttonsFrame.addChild(row4);
		body.addChild(buttonsFrame);

		layout.addToContents(body);

		var footer = LinearLayout.horizontal().spacing(8);
		if (isAddScreen) {
			addButton = footer.addChild(Button.builder(Component.translatable("keystrokes.stroke.add"), b -> {
				hud.keystrokes.add(stroke);
				onClose();
			}).build());
			addButton.active = false;
		}
		footer.addChild(Button.builder(isAddScreen ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_BACK, b -> onClose()).build());

		layout.addToFooter(footer);

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (isAddScreen && stroke.getKey() != null) {
			addButton.active = true;
		}
		currentKey.setWidth(super.width);
		if (stroke.getKey() != null) {
			currentKey.setMessage(Component.translatable("keystrokes.stroke.key", stroke.getKey().getTranslatedKeyMessage(), Component.translatable(stroke.getKey().getName())));
		} else {
			currentKey.setMessage(Component.empty());
		}
		if (synchronizeButton != null) {
			synchronizeButton.active = stroke.getKey() != null;
		}
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
		hud.saveKeystrokes();
	}
}
