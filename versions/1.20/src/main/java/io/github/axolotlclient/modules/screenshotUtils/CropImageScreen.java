/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.screenshotUtils;

import java.io.IOException;

import com.mojang.blaze3d.texture.NativeImage;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.rounded.util.StyleColors;
import io.github.axolotlclient.mixin.TextFieldWidgetAccessor;
import io.github.axolotlclient.util.CursorType;
import io.github.axolotlclient.util.CursorTypes;
import io.github.axolotlclient.util.MathUtil;
import io.github.axolotlclient.util.dialogs.DialogProvider;
import lombok.AllArgsConstructor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.ElementPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.GuiNavigationEvent;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.CommonTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

public class CropImageScreen extends Screen {
	private final Screen parent;
	private final ImageInstance image;
	private final boolean freeOnClose;
	private ImageWidget imageWidget;
	private TextFieldWidget posX, posY, posW, posH;

	public CropImageScreen(Screen parent, ImageInstance image) {
		this(parent, image, false);
	}

	public CropImageScreen(@Nullable Screen parent, ImageInstance image, boolean freeOnClose) {
		super(Text.translatable("gallery.image.crop.title"));
		this.parent = parent;
		this.image = image;
		this.freeOnClose = freeOnClose;
	}

	@Override
	protected void init() {
		var title = addDrawableChild(new TextWidget(getTitle(), textRenderer));
		title.setPosition(width / 2 - title.getWidth() / 2, 33 / 2 - title.getHeight() / 2);
		imageWidget = addDrawableChild(new ImageWidget(0, 33, width, height - 33 - 80));
		var inputsY = height - 80 / 2 - (20 * 3 + 4 * 3) / 2;
		var posWidget = addDrawableChild(new TextWidget(Text.translatable("gallery.image.crop.position"), textRenderer));
		posWidget.axolotlclientconfig$setHeight(20);
		posX = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 50, 20, Text.translatable("gallery.image.crop.inputs.x")));
		posY = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 50, 20, Text.translatable("gallery.image.crop.inputs.y")));
		var sizeWidget = addDrawableChild(new TextWidget(Text.translatable("gallery.image.crop.size"), textRenderer));
		sizeWidget.axolotlclientconfig$setHeight(20);
		posW = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 50, 20, Text.translatable("gallery.image.crop.inputs.width")));
		posH = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 50, 20, Text.translatable("gallery.image.crop.inputs.height")));
		var inputsWidth = posWidget.getWidth() + 4 + posX.getWidth() + 4 + posY.getWidth() + 4 + sizeWidget.getWidth() + 4 + posW.getWidth() + 4 + posH.getWidth();
		posWidget.setPosition(width / 2 - inputsWidth / 2, inputsY);
		posX.setPosition(posWidget.axolotlclientconfig$getRight() + 4, inputsY);
		posY.setPosition(posX.axolotlclientconfig$getRight() + 4, inputsY);
		sizeWidget.setPosition(posY.axolotlclientconfig$getRight() + 4, inputsY);
		posW.setPosition(sizeWidget.axolotlclientconfig$getRight() + 4, inputsY);
		posH.setPosition(posW.axolotlclientconfig$getRight() + 4, inputsY);
		posX.setTextPredicate(s -> s.matches("\\d*"));
		posY.setTextPredicate(s -> s.matches("\\d*"));
		posW.setTextPredicate(s -> s.matches("\\d*"));
		posH.setTextPredicate(s -> s.matches("\\d*"));
		posX.setChangedListener(s -> imageWidget.updateFromTextFieldWidgets());
		posY.setChangedListener(s -> imageWidget.updateFromTextFieldWidgets());
		posW.setChangedListener(s -> imageWidget.updateFromTextFieldWidgets());
		posH.setChangedListener(s -> imageWidget.updateFromTextFieldWidgets());
		imageWidget.updateTextFieldWidgetContents();
		var footerLine2Y = inputsY + 24;
		var footerLine3Y = footerLine2Y + 24;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gallery.image.crop.save_as"), btn -> {
			try (var crop = imageWidget.getCopyOfSelection()) {
				var defaultPath = image instanceof ImageInstance.Local loc ? loc.location().getParent() : FabricLoader.getInstance().getGameDir();
				DialogProvider.IMPL.save("Choose destination", defaultPath.resolve(image.filename().replace(".png", "_cropped-" + Util.getFileNameFormattedDateTime() + ".png"))
					.toAbsolutePath(), ".png").thenAccept(opt -> {
					if (opt.isPresent()) {
						var dest = opt.get();
						try {
							crop.writeFile(dest);
							AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.save_as.success", "gallery.image.crop.save_as.success.description", dest.getFileName());
						} catch (IOException e) {
							AxolotlClientCommon.getInstance().getLogger().error("Failed to save cropped image!", e);
							AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.save_as.failure", "gallery.image.crop.save_as.failure.description", dest);
						}
					}
				});
			}
		}).width(100).position(width / 2 - (100 * 3 + 4 * 2) / 2, footerLine2Y).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gallery.image.crop.save"), btn -> {
			var p = image instanceof ImageInstance.Local loc ? loc.location().getParent() : FabricLoader.getInstance().getGameDir().resolve("screenshots");
			var dest = p.resolve(image.filename().replace(".png", "_cropped-" + Util.getFileNameFormattedDateTime() + ".png"));
			try (var crop = imageWidget.getCopyOfSelection()) {
				crop.writeFile(dest);
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.save.success", "gallery.image.crop.save.success.description", dest.getFileName());
			} catch (IOException e) {
				AxolotlClientCommon.getInstance().getLogger().error("Failed to save cropped image!", e);
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.save.failure", "gallery.image.crop.save.failure.description", dest.getFileName());
			}
		}).width(100).position(width / 2 - (100 * 3 + 4 * 2) / 2 + 100 + 4, footerLine2Y).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gallery.image.crop.copy"), btn -> {
			try (var crop = imageWidget.getCopyOfSelection()) {
				ScreenshotCopying.copy(crop.getBytes());
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.copy.success", "gallery.image.crop.copy.success.description");
			} catch (IOException e) {
				AxolotlClientCommon.getInstance().getLogger().error("Failed to copy cropped image!", e);
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.copy.failure", "gallery.image.crop.save.copy.description");
			}
		}).width(100).position(width / 2 - (100 * 3 + 4 * 2) / 2 + 100 + 4 + 100 + 4, footerLine2Y).build());
		addDrawableChild(ButtonWidget.builder(CommonTexts.BACK, btn -> closeScreen()).position(width / 2 - 150 / 2, footerLine3Y).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, delta);
		graphics.fillGradient(RenderLayer.getGuiOverlay(), 0, 33 - 2, width, 33 - 2 + 4, -16777216, 0, 0);
		graphics.fillGradient(RenderLayer.getGuiOverlay(), 0, height - 80 - 4, width, height - 80, 0, -16777216, 0);
	}

	@Override
	public void closeScreen() {
		client.setScreen(parent);
		CursorTypes.ARROW.select();
	}

	@Override
	public void removed() {
		if (freeOnClose) {
			client.getTextureManager().destroyTexture(image.id());
		}
	}

	private class ImageWidget extends ClickableWidget {
		private static final int DRAG_HANDLE_RADIUS = 7;
		private float dragX, dragY, dragX1, dragY1;
		private DragHandle currentHandle = null;
		private final float imgAspectRatio;
		private int imgX, imgY, imgWidth, imgHeight;
		private float scale = 1f, translateX = 0f, translateY = 0f;

		@SuppressWarnings("resource")
		public ImageWidget(int x, int y, int width, int height) {
			super(x, y, width, height, Text.empty());
			imgAspectRatio = ((float) image.image().getWidth()) / image.image().getHeight();
			setImgDimensions();
			dragX = imgX;
			dragY = imgY;
			dragX1 = dragX + imgWidth;
			dragY1 = dragY + imgHeight;
		}

		@Override
		public void setX(int x) {
			var diff0 = this.dragX - this.imgX;
			var diff1 = this.dragX1 - this.imgX;
			this.imgX = imgX - getX() + x;
			this.dragX = imgX + diff0;
			this.dragX1 = imgX + diff1;
			super.setX(x);
			updateTextFieldWidgetContents();
		}

		@Override
		public void setY(int y) {
			var diff0 = this.dragY - imgY;
			var diff1 = this.dragY1 - imgY;
			imgY = imgY - getY() + y;
			this.dragY = imgY + diff0;
			this.dragY1 = imgY + diff1;
			super.setY(y);
			updateTextFieldWidgetContents();
		}

		@Override
		public void setWidth(int width) {
			super.setWidth(width);
			setImgDimensions();
		}

		private void setImgDimensions() {
			var dragAreaPercentageX0 = (this.dragX - imgX) / imgWidth;
			var dragAreaPercentageX1 = (this.dragX1 - imgX) / imgWidth;
			var dragAreaPercentageY0 = (this.dragY - imgY) / imgHeight;
			var dragAreaPercentageY1 = (this.dragY1 - imgY) / imgHeight;
			imgHeight = getHeight() - DRAG_HANDLE_RADIUS * 2;
			var w = (int) (imgHeight * imgAspectRatio);
			imgWidth = Math.min(w, getWidth() - DRAG_HANDLE_RADIUS * 2);
			imgHeight = (int) (imgWidth / imgAspectRatio);
			imgX = getX() + getWidth() / 2 - imgWidth / 2;
			imgY = getY() + getHeight() / 2 - imgHeight / 2;
			this.dragX = imgX + dragAreaPercentageX0 * imgWidth;
			this.dragX1 = imgX + dragAreaPercentageX1 * imgWidth;
			this.dragY = imgY + dragAreaPercentageY0 * imgHeight;
			this.dragY1 = imgY + dragAreaPercentageY1 * imgHeight;
			updateTextFieldWidgetContents();
		}

		@Override
		protected void drawWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
			guiGraphics.br$pushMatrix();
			guiGraphics.br$pushScissor(getX(), getY(), getWidth(), getHeight());
			guiGraphics.br$translateMatrix(CropImageScreen.this.width / 2f, CropImageScreen.this.height / 2f);
			guiGraphics.br$translateMatrix(translateX, translateY);
			guiGraphics.br$scaleMatrix(scale, scale);
			guiGraphics.br$translateMatrix(-CropImageScreen.this.width / 2f, -CropImageScreen.this.height / 2f);
			guiGraphics.drawTexture(image.id(), imgX, imgY, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
			// Can we have a stencil buffer please?
			guiGraphics.br$scaleMatrix(1 / scale, 1 / scale);
			int scaledDragX = Math.round(dragX * scale);
			int scaledDragX1 = Math.round(dragX * scale + ((dragX1 - dragX) * scale));
			int scaledDragY = Math.round(dragY * scale);
			int scaledDragY1 = Math.round(dragY * scale + ((dragY1 - dragY) * scale));
			guiGraphics.br$pushMatrix();
			guiGraphics.br$translateMatrix(imgX * scale, imgY * scale);
			guiGraphics.br$fillRectWithCutout(0, 0,
				Math.round(imgWidth * scale), Math.round(imgHeight * scale), Colors.DARK_GRAY.withAlpha(180).toInt(),
				Math.round(scaledDragX - imgX * scale), Math.round(scaledDragY - imgY * scale), scaledDragX1 - scaledDragX, scaledDragY1 - scaledDragY);
			guiGraphics.br$popMatrix();

			int hoveredHandleColor = StyleColors.highlight().toInt();
			DragHandle hoveredHandle = null;
			if (isMouseOver(mouseX, mouseY)) {
				if (hasControlDown()) {
					CursorTypes.RESIZE_ALL.select();
				} else if (currentHandle != null) {
					hoveredHandle = currentHandle;
					currentHandle.cursor.select();
				} else {
					hoveredHandle = getHandle(getTransformedX(mouseX), getTransformedY(mouseY));
					if (hoveredHandle != null) {
						hoveredHandle.cursor.select();
					} else {
						CursorTypes.ARROW.select();
					}
				}
			} else {
				CursorTypes.ARROW.select();
			}
			var handleSize = Math.min(Math.min(scaledDragX1 - scaledDragX, scaledDragY1 - scaledDragY) / 3, DRAG_HANDLE_RADIUS);
			int handleColor = StyleColors.highlight2().toInt();
			var xHandleW = Math.max(scaledDragX1 - scaledDragX - handleSize * 2 - 10, 0);
			float outlineWidth = .5f;
			var handleRadius = handleSize * 3 / 4f;
			var xHandleRadius = Math.min(handleRadius, xHandleW / 2f);
			if (xHandleW > 0 && xHandleRadius > 1f) {
				int handleXStart = scaledDragX + (scaledDragX1 - scaledDragX) / 2 - xHandleW / 2;
				guiGraphics.br$outlineRectRoundVarying(handleXStart, scaledDragY,
					xHandleW, handleSize, hoveredHandle == DragHandle.TOP_CENTER ? hoveredHandleColor : handleColor, 0, xHandleRadius, xHandleRadius, 0, outlineWidth);
				guiGraphics.br$outlineRectRoundVarying(handleXStart, scaledDragY1 - handleSize,
					xHandleW, handleSize, hoveredHandle == DragHandle.BOTTOM_CENTER ? hoveredHandleColor : handleColor, xHandleRadius, 0, 0, xHandleRadius, outlineWidth);
			}
			var yHandleH = Math.max(scaledDragY1 - scaledDragY - handleSize * 2 - 10, 0);
			var yHandleRadius = Math.min(handleRadius, yHandleH / 2f);
			if (yHandleH > 0 && yHandleRadius > 1f) {
				int handleYStart = scaledDragY + (scaledDragY1 - scaledDragY) / 2 - yHandleH / 2;
				guiGraphics.br$outlineRectRoundVarying(scaledDragX, handleYStart, handleSize,
					yHandleH, hoveredHandle == DragHandle.LEFT_CENTER ? hoveredHandleColor : handleColor, 0, 0, yHandleRadius, yHandleRadius, outlineWidth);
				guiGraphics.br$outlineRectRoundVarying(scaledDragX1 - handleSize, handleYStart,
					handleSize, yHandleH, hoveredHandle == DragHandle.RIGHT_CENTER ? hoveredHandleColor : handleColor, yHandleRadius, yHandleRadius, 0, 0, outlineWidth);
			}
			guiGraphics.br$outlineRect(scaledDragX, scaledDragY,
				scaledDragX1 - scaledDragX, scaledDragY1 - scaledDragY,
				hoveredHandle == DragHandle.CENTER_CENTER ? hoveredHandleColor : Colors.WHITE.toInt());
			guiGraphics.axolotlclient_rendering$outlineCircle(scaledDragX, scaledDragY,
				hoveredHandle == DragHandle.TOP_LEFT ? hoveredHandleColor : handleColor, handleSize, outlineWidth);
			guiGraphics.axolotlclient_rendering$outlineCircle(scaledDragX1, scaledDragY,
				hoveredHandle == DragHandle.TOP_RIGHT ? hoveredHandleColor : handleColor, handleSize, outlineWidth);
			guiGraphics.axolotlclient_rendering$outlineCircle(scaledDragX1, scaledDragY1,
				hoveredHandle == DragHandle.BOTTOM_RIGHT ? hoveredHandleColor : handleColor, handleSize, outlineWidth);
			guiGraphics.axolotlclient_rendering$outlineCircle(scaledDragX, scaledDragY1,
				hoveredHandle == DragHandle.BOTTOM_LEFT ? hoveredHandleColor : handleColor, handleSize, outlineWidth);
			guiGraphics.br$popScissor();
			guiGraphics.br$popMatrix();
		}

		@Override
		public void playDownSound(SoundManager soundManager) {
		}

		@Override
		public @Nullable ElementPath nextFocusPath(GuiNavigationEvent event) {
			return null;
		}

		@Override
		public SelectionType getType() {
			return SelectionType.NONE;
		}

		@SuppressWarnings("resource")
		public NativeImage getCopyOfSelection() {
			var trueDragX = Math.round((dragX - imgX) / imgWidth * image.image().getWidth());
			var trueDragX1 = Math.round((dragX1 - imgX) / imgWidth * image.image().getWidth());
			var trueDragY = Math.round((dragY - imgY) / imgHeight * image.image().getHeight());
			var trueDragY1 = Math.round((dragY1 - imgY) / imgHeight * image.image().getHeight());
			var img = new NativeImage(trueDragX1 - trueDragX, trueDragY1 - trueDragY, false);
			image.image().copyRectangle(img, trueDragX, trueDragY, 0, 0, trueDragX1 - trueDragX, trueDragY1 - trueDragY, false, false);
			return img;
		}

		@Override
		protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
			if (hasControlDown()) {
				translateX += (float) deltaX;
				translateY += (float) deltaY;
				return;
			}
			if (currentHandle == null) return;
			final float transformedX = (float) getTransformedX(mouseX);
			final float transformedY = (float) getTransformedY(mouseY);
			switch (currentHandle) {
				case TOP_LEFT -> {
					dragX = transformedX + clickDragOffsetX;
					dragY = transformedY + clickDragOffsetY;
				}
				case TOP_CENTER -> dragY = transformedY;
				case TOP_RIGHT -> {
					dragX1 = transformedX + clickDragOffsetX;
					dragY = transformedY + clickDragOffsetY;
				}
				case LEFT_CENTER -> dragX = transformedX + clickDragOffsetX;
				case RIGHT_CENTER -> dragX1 = transformedX + clickDragOffsetX;
				case BOTTOM_LEFT -> {
					dragY1 = transformedY + clickDragOffsetY;
					dragX = transformedX + clickDragOffsetX;
				}
				case BOTTOM_CENTER -> dragY1 = transformedY + clickDragOffsetY;
				case BOTTOM_RIGHT -> {
					dragX1 = transformedX + clickDragOffsetX;
					dragY1 = transformedY + clickDragOffsetY;
				}
				case CENTER_CENTER -> {
					var newMouseX = transformedX - clickDragOffsetX;
					var newMouseY = transformedY - clickDragOffsetY;
					var dragW = dragX1 - dragX;
					var dragH = dragY1 - dragY;
					dragX = MathUtil.clamp(newMouseX, imgX, imgX + imgWidth);
					dragY = MathUtil.clamp(newMouseY, imgY, imgY + imgHeight);
					dragX1 = dragX + dragW;
					dragY1 = dragY + dragH;
					if (dragX1 >= imgX + imgWidth) {
						dragX1 = imgX + imgWidth;
						dragX = dragX1 - dragW;
					}
					if (dragY1 >= imgY + imgHeight) {
						dragY1 = imgY + imgHeight;
						dragY = dragY1 - dragH;
					}
					updateTextFieldWidgetContents();
					return;
				}
				default -> {
					super.onDrag(mouseX, mouseY, deltaX, deltaY);
					return;
				}
			}
			clampCrop();
			updateTextFieldWidgetContents();
		}


		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
			if (hasControlDown()) {
				if (scrollY > 0) {
					if (scale > 40f) return false;
					scale *= 2f;
					var offsetX = CropImageScreen.this.width / 2f + translateX;
					var offsetY = CropImageScreen.this.height / 2f + translateY;
					var mirroredOnOffsetX = offsetX - (mouseX - offsetX);
					var mirroredOnOffsetY = offsetY - (mouseY - offsetY);
					translateX = (float) (mirroredOnOffsetX - CropImageScreen.this.width / 2f);
					translateY = (float) (mirroredOnOffsetY - CropImageScreen.this.height / 2f);
				} else {
					if (scale < 0.2f) return false;
					scale /= 2f;
					var offsetX = CropImageScreen.this.width / 2f + translateX;
					var offsetY = CropImageScreen.this.height / 2f + translateY;
					var mirroredOnOffsetX = offsetX + (mouseX - offsetX) / 2f;
					var mirroredOnOffsetY = offsetY + (mouseY - offsetY) / 2f;
					translateX = (float) (mirroredOnOffsetX - CropImageScreen.this.width / 2f);
					translateY = (float) (mirroredOnOffsetY - CropImageScreen.this.height / 2f);
				}
				return true;
			}
			return super.mouseScrolled(mouseX, mouseY, scrollY);
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
			if (currentHandle != null) {
				super.playDownSound(client.getSoundManager());
			}
			currentHandle = null;
			super.onRelease(mouseX, mouseY);
			CursorType.DEFAULT.select();
		}

		private void clampCrop() {
			dragX = MathUtil.clamp(dragX, imgX, imgX + imgWidth);
			dragX1 = MathUtil.clamp(dragX1, imgX, imgX + imgWidth);
			dragY = MathUtil.clamp(dragY, imgY, imgY + imgHeight);
			dragY1 = MathUtil.clamp(dragY1, imgY, imgY + imgHeight);
			if (dragX >= dragX1) {
				var x = dragX;
				dragX = dragX1;
				dragX1 = x;
				if (currentHandle != null) {
					currentHandle = currentHandle.mirrorX();
				}
			}
			if (dragY >= dragY1) {
				var y = dragY;
				dragY = dragY1;
				dragY1 = y;
				if (currentHandle != null) {
					currentHandle = currentHandle.mirrorY();
				}
			}
		}

		private float clickDragOffsetX, clickDragOffsetY;

		@Override
		public void onClick(double mouseX, double mouseY) {
			mouseX = getTransformedX(mouseX);
			mouseY = getTransformedY(mouseY);
			currentHandle = getHandle(mouseX, mouseY);
			if (currentHandle != null) {
				clickDragOffsetX = currentHandle.getXOffset((float) mouseX, this);
				clickDragOffsetY = currentHandle.getYOffset((float) mouseY, this);
			}
			super.onClick(mouseX, mouseY);
		}

		private @Nullable DragHandle getHandle(double mouseX, double mouseY) {
			mouseX *= scale;
			mouseY *= scale;
			var scaledDragX = dragX * scale;
			var scaledDragX1 = dragX * scale + (dragX1 - dragX) * scale;
			var scaledDragY = dragY * scale;
			var scaledDragY1 = dragY * scale + (dragY1 - dragY) * scale;
			var handleSize = Math.min(Math.min((scaledDragX1 - scaledDragX), (scaledDragY1 - scaledDragY)) / 3, DRAG_HANDLE_RADIUS);
			var handleSizeSq = handleSize * handleSize;
			if (MathUtil.distSq(mouseX, mouseY, scaledDragX, scaledDragY) < handleSizeSq) {
				return DragHandle.TOP_LEFT;
			} else if (MathUtil.distSq(mouseX, mouseY, scaledDragX1, scaledDragY) < handleSizeSq) {
				return DragHandle.TOP_RIGHT;
			} else if (MathUtil.distSq(mouseX, mouseY, scaledDragX1, scaledDragY1) < handleSizeSq) {
				return DragHandle.BOTTOM_RIGHT;
			} else if (MathUtil.distSq(mouseX, mouseY, scaledDragX, scaledDragY1) < handleSizeSq) {
				return DragHandle.BOTTOM_LEFT;
			}
			var xHandleW = Math.max(scaledDragX1 - scaledDragX - handleSize * 2 - 10, 0);
			var handleRadius = handleSize * 3 / 4f;
			var xHandleRadius = Math.min(handleRadius, xHandleW / 2f);
			if (xHandleW > 0 && xHandleRadius > 1f) {
				float handleXStart = scaledDragX + (scaledDragX1 - scaledDragX) / 2 - xHandleW / 2;
				if (isOverRoundedRect(handleXStart, scaledDragY, xHandleW, handleSize, 0, xHandleRadius, xHandleRadius, 0, mouseX, mouseY)) {
					return DragHandle.TOP_CENTER;
				} else if (isOverRoundedRect(handleXStart, scaledDragY1 - handleSize, xHandleW, handleSize, xHandleRadius, 0, 0, xHandleRadius, mouseX, mouseY)) {
					return DragHandle.BOTTOM_CENTER;
				}
			}
			var yHandleH = Math.max(scaledDragY1 - scaledDragY - handleSize * 2 - 10, 0);
			var yHandleRadius = Math.min(handleRadius, yHandleH / 2f);
			if (yHandleH > 0 && yHandleRadius > 1f) {
				float handleYStart = scaledDragY + (scaledDragY1 - scaledDragY) / 2 - yHandleH / 2;
				if (isOverRoundedRect(scaledDragX, handleYStart, handleSize, yHandleH, 0, 0, yHandleRadius, yHandleRadius, mouseX, mouseY)) {
					return DragHandle.LEFT_CENTER;
				} else if (isOverRoundedRect(scaledDragX1 - handleSize, handleYStart, handleSize, yHandleH, yHandleRadius, yHandleRadius, 0, 0, mouseX, mouseY)) {
					return DragHandle.RIGHT_CENTER;
				}
			}
			if (mouseX >= scaledDragX && mouseX <= scaledDragX + (scaledDragX1 - scaledDragX) && mouseY >= scaledDragY && mouseY <= scaledDragY + (scaledDragY1 - scaledDragY)) {
				return DragHandle.CENTER_CENTER;
			}
			return null;
		}

		private boolean isOverRoundedRect(float x, float y, float width, float height, float roundingTL, float roundingBL, float roundingBR, float roundingTR, double mouseX, double mouseY) {
			var x1 = x + width;
			var y1 = y + height;
			if (!(mouseX >= x && mouseX <= x1 && mouseY >= y && mouseY <= y1)) {
				return false; // short-circuit as this check is cheap
			}
			// need to check in segments
			// top-left arc
			if (roundingTL > 0 && mouseX >= x && mouseX <= x + roundingTL && mouseY >= y && mouseY <= y + roundingTL) {
				return MathUtil.distSq(x + roundingTL, y + roundingTL, mouseX, mouseY) <= roundingTL * roundingTL;
			}
			// top-right arc
			if (roundingTR > 0 && mouseX >= x1 - roundingTR && mouseX <= x1 && mouseY >= y && mouseY <= y + roundingTR) {
				return MathUtil.distSq(x1 - roundingTR, y + roundingTR, mouseX, mouseY) <= roundingTR * roundingTR;
			}
			// bottom-left arc
			if (roundingBL > 0 && mouseX >= x && mouseX <= x + roundingBL && mouseY >= y1 - roundingBL && mouseY <= y1) {
				return MathUtil.distSq(x + roundingBL, y1 - roundingBL, mouseX, mouseY) <= roundingBL * roundingBL;
			}
			// bottom-right arc
			if (roundingBR > 0 && mouseX >= x1 - roundingBR && mouseX <= x1 && mouseY >= y1 - roundingBR && mouseY <= y1) {
				return MathUtil.distSq(x1 - roundingBR, y1 - roundingBR, mouseX, mouseY) <= roundingBR * roundingBR;
			}
			return true;
		}

		private double getTransformedX(double mouseX) {
			return (mouseX - translateX - CropImageScreen.this.width / 2f) / scale + CropImageScreen.this.width / 2f;
		}

		private double getTransformedY(double mouseY) {
			return (mouseY - translateY - CropImageScreen.this.height / 2f) / scale + CropImageScreen.this.height / 2f;
		}

		@Override
		protected void updateNarration(NarrationMessageBuilder builder) {
		}

		@SuppressWarnings("resource")
		private void updateTextFieldWidgetContents() {
			var trueDragX = Math.round((dragX - imgX) / imgWidth * image.image().getWidth());
			var trueDragX1 = Math.round((dragX1 - imgX) / imgWidth * image.image().getWidth());
			var trueDragY = Math.round((dragY - imgY) / imgHeight * image.image().getHeight());
			var trueDragY1 = Math.round((dragY1 - imgY) / imgHeight * image.image().getHeight());
			if (posX != null && !posX.isFocused()) {
				var res = ((TextFieldWidgetAccessor) posX).getResponder();
				posX.setChangedListener(null);
				posX.setText(String.valueOf(trueDragX));
				posX.setChangedListener(res);
			}
			if (posY != null && !posY.isFocused()) {
				var res = ((TextFieldWidgetAccessor) posY).getResponder();
				posY.setChangedListener(null);
				posY.setText(String.valueOf(trueDragY));
				posY.setChangedListener(res);
			}
			if (posW != null && !posW.isFocused()) {
				var res = ((TextFieldWidgetAccessor) posW).getResponder();
				posW.setChangedListener(null);
				posW.setText(String.valueOf(trueDragX1 - trueDragX));
				posW.setChangedListener(res);
			}
			if (posH != null && !posH.isFocused()) {
				var res = ((TextFieldWidgetAccessor) posH).getResponder();
				posH.setChangedListener(null);
				posH.setText(String.valueOf(trueDragY1 - trueDragY));
				posH.setChangedListener(res);
			}
		}

		@SuppressWarnings("resource")
		private void updateFromTextFieldWidgets() {
			if (posX == null || posY == null || posW == null || posH == null) return;
			var xValue = posX.getText();
			var yValue = posY.getText();
			var wValue = posW.getText();
			var hValue = posH.getText();
			if (xValue.isEmpty() || yValue.isEmpty() || wValue.isEmpty() || hValue.isEmpty()) return;
			var pX = Integer.parseInt(xValue);
			var pY = Integer.parseInt(yValue);
			var pW = Integer.parseInt(wValue);
			var pH = Integer.parseInt(hValue);
			var iW = image.image().getWidth();
			var iH = image.image().getHeight();
			if (pX < 0 || pX > iW || pY < 0 || pY > iH || pW < 0 || pW > iW || pH < 0 || pH > iH) return;
			var dragAreaPercentageX0 = (float) pX / iW;
			var dragAreaPercentageY0 = (float) pY / iH;
			var dragAreaPercentageX1 = (float) (pX + pW) / iW;
			var dragAreaPercentageY1 = (float) (pY + pH) / iH;
			dragX = MathUtil.clamp(imgX + dragAreaPercentageX0 * imgWidth, imgX, imgX + imgWidth);
			dragX1 = MathUtil.clamp(imgX + dragAreaPercentageX1 * imgWidth, imgX, imgX + imgWidth);
			dragY = MathUtil.clamp(imgY + dragAreaPercentageY0 * imgHeight, imgY, imgY + imgHeight);
			dragY1 = MathUtil.clamp(imgY + dragAreaPercentageY1 * imgHeight, imgY, imgY + imgHeight);
			var wScale = imgWidth / (dragX1 - dragX);
			var hScale = imgHeight / (dragY1 - dragY);
			scale = Math.abs(wScale - 1) < Math.abs(hScale - 1) ? wScale : hScale;
			translateX = -CropImageScreen.this.width / 2f - scale * (-CropImageScreen.this.width / 2f + dragX) + getX() + getWidth() / 2f - (dragX1 - dragX) * scale / 2f;
			translateY = -CropImageScreen.this.height / 2f - scale * (-CropImageScreen.this.height / 2f + dragY) + getY() + getHeight() / 2f - (dragY1 - dragY) * scale / 2f;
			updateTextFieldWidgetContents();
		}

		@AllArgsConstructor
		private enum DragHandle {
			TOP_LEFT(CursorTypes.RESIZE_NWSE),
			TOP_CENTER(CursorTypes.RESIZE_NS),
			TOP_RIGHT(CursorTypes.RESIZE_NESW),
			LEFT_CENTER(CursorTypes.RESIZE_EW),
			RIGHT_CENTER(CursorTypes.RESIZE_EW),
			BOTTOM_LEFT(CursorTypes.RESIZE_NESW),
			BOTTOM_CENTER(CursorTypes.RESIZE_NS),
			BOTTOM_RIGHT(CursorTypes.RESIZE_NWSE),
			CENTER_CENTER(CursorTypes.RESIZE_ALL);
			private final CursorType cursor;

			public DragHandle mirrorX() {
				return switch (this) {
					case TOP_LEFT -> TOP_RIGHT;
					case TOP_RIGHT -> TOP_LEFT;
					case LEFT_CENTER -> RIGHT_CENTER;
					case RIGHT_CENTER -> LEFT_CENTER;
					case BOTTOM_LEFT -> BOTTOM_RIGHT;
					case BOTTOM_RIGHT -> BOTTOM_LEFT;
					default -> this;
				};
			}

			public DragHandle mirrorY() {
				return switch (this) {
					case TOP_LEFT -> BOTTOM_LEFT;
					case TOP_CENTER -> BOTTOM_CENTER;
					case TOP_RIGHT -> BOTTOM_RIGHT;
					case BOTTOM_LEFT -> TOP_LEFT;
					case BOTTOM_CENTER -> TOP_CENTER;
					case BOTTOM_RIGHT -> TOP_RIGHT;
					default -> this;
				};
			}

			public float getXOffset(float mouseX, ImageWidget w) {
				return switch (this) {
					case TOP_LEFT, LEFT_CENTER, BOTTOM_LEFT -> w.dragX - mouseX;
					case TOP_CENTER, BOTTOM_CENTER -> 0;
					case CENTER_CENTER -> mouseX - w.dragX;
					case TOP_RIGHT, RIGHT_CENTER, BOTTOM_RIGHT -> w.dragX1 - mouseX;
				};
			}

			public float getYOffset(float mouseY, ImageWidget w) {
				return switch (this) {
					case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> w.dragY - mouseY;
					case LEFT_CENTER, RIGHT_CENTER -> 0;
					case CENTER_CENTER -> mouseY - w.dragY;
					case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> w.dragY1 - mouseY;
				};
			}
		}
	}
}
