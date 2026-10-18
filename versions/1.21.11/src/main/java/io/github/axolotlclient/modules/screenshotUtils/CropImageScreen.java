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

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.rounded.util.StyleColors;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.DrawUtil;
import io.github.axolotlclient.mixin.EditBoxAccessor;
import io.github.axolotlclient.util.ExtraCursorTypes;
import io.github.axolotlclient.util.MathUtil;
import io.github.axolotlclient.util.dialogs.DialogProvider;
import lombok.AllArgsConstructor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class CropImageScreen extends Screen {
	@Nullable
	private final Screen parent;
	private final ImageInstance image;
	private final boolean freeOnClose;
	private final HeaderAndFooterLayout hfl = new HeaderAndFooterLayout(this, 33, 80);
	@Nullable
	private ImageWidget imageWidget;
	@Nullable
	private EditBox posX, posY, posW, posH;

	public CropImageScreen(@Nullable Screen parent, ImageInstance image) {
		this(parent, image, false);
	}

	public CropImageScreen(@Nullable Screen parent, ImageInstance image, boolean freeOnClose) {
		super(Component.translatable("gallery.image.crop.title"));
		this.parent = parent;
		this.image = image;
		this.freeOnClose = freeOnClose;
	}

	@Override
	protected void init() {
		hfl.addTitleHeader(getTitle(), getFont());
		var contents = hfl.addToContents(LinearLayout.vertical()).spacing(4);
		imageWidget = contents.addChild(new ImageWidget(hfl.getX(), hfl.getHeaderHeight(), hfl.getWidth(), hfl.getContentHeight()));
		var footer = hfl.addToFooter(LinearLayout.vertical()).spacing(4);
		var inputs = footer.addChild(LinearLayout.horizontal(), LayoutSettings::alignHorizontallyCenter).spacing(4);
		inputs.addChild(new StringWidget(Component.translatable("gallery.image.crop.position"), font)).setHeight(20);
		posX = inputs.addChild(new EditBox(font, 50, 20, Component.translatable("gallery.image.crop.inputs.x")) {
			@Override
			public void insertText(String input) {
				if (!input.matches("\\d*")) return;
				super.insertText(input);
			}
		});
		posY = inputs.addChild(new EditBox(font, 50, 20, Component.translatable("gallery.image.crop.inputs.y")) {
			@Override
			public void insertText(String input) {
				if (!input.matches("\\d*")) return;
				super.insertText(input);
			}
		});
		inputs.addChild(new StringWidget(Component.translatable("gallery.image.crop.size"), font)).setHeight(20);
		posW = inputs.addChild(new EditBox(font, 50, 20, Component.translatable("gallery.image.crop.inputs.width")) {
			@Override
			public void insertText(String input) {
				if (!input.matches("\\d*")) return;
				super.insertText(input);
			}
		});
		posH = inputs.addChild(new EditBox(font, 50, 20, Component.translatable("gallery.image.crop.inputs.height")) {
			@Override
			public void insertText(String input) {
				if (!input.matches("\\d*")) return;
				super.insertText(input);
			}
		});
		posX.setResponder(s -> imageWidget.updateFromEditBoxes());
		posY.setResponder(s -> imageWidget.updateFromEditBoxes());
		posW.setResponder(s -> imageWidget.updateFromEditBoxes());
		posH.setResponder(s -> imageWidget.updateFromEditBoxes());
		imageWidget.updateEditBoxContents();
		var footerLine2 = footer.addChild(LinearLayout.horizontal(), LayoutSettings::alignHorizontallyCenter).spacing(4);
		var footerLine3 = footer.addChild(LinearLayout.horizontal(), LayoutSettings::alignHorizontallyCenter).spacing(4);
		footerLine2.addChild(Button.builder(Component.translatable("gallery.image.crop.save_as"), btn -> {
			try (var crop = imageWidget.getCopyOfSelection()) {
				var defaultPath = image instanceof ImageInstance.Local loc ? loc.location().getParent() : FabricLoader.getInstance().getGameDir();
				DialogProvider.IMPL.save("Choose destination", defaultPath.resolve(image.filename().replace(".png", "_cropped-" + Util.getFilenameFormattedDateTime() + ".png"))
					.toAbsolutePath(), ".png").thenAccept(opt -> {
					if (opt.isPresent()) {
						var dest = opt.get();
						try {
							crop.writeToFile(dest);
							AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.save_as.success", "gallery.image.crop.save_as.success.description", dest.getFileName());
						} catch (IOException e) {
							AxolotlClientCommon.getInstance().getLogger().error("Failed to save cropped image!", e);
							AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.save_as.failure", "gallery.image.crop.save_as.failure.description", dest);
						}
					}
				});
			}
		}).width(100).build());
		footerLine2.addChild(Button.builder(Component.translatable("gallery.image.crop.save"), btn -> {
			var p = image instanceof ImageInstance.Local loc ? loc.location().getParent() : FabricLoader.getInstance().getGameDir().resolve("screenshots");
			var dest = p.resolve(image.filename().replace(".png", "_cropped-" + Util.getFilenameFormattedDateTime() + ".png"));
			try (var crop = imageWidget.getCopyOfSelection()) {
				crop.writeToFile(dest);
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.save.success", "gallery.image.crop.save.success.description", dest.getFileName());
			} catch (IOException e) {
				AxolotlClientCommon.getInstance().getLogger().error("Failed to save cropped image!", e);
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.save.failure", "gallery.image.crop.save.failure.description", dest.getFileName());
			}
		}).width(100).build());
		footerLine2.addChild(Button.builder(Component.translatable("gallery.image.crop.copy"), btn -> {
			try (var crop = imageWidget.getCopyOfSelection()) {
				ScreenshotCopying.copy(DrawUtil.writeToByteArray(crop));
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.copy.success", "gallery.image.crop.copy.success.description");
			} catch (IOException e) {
				AxolotlClientCommon.getInstance().getLogger().error("Failed to copy cropped image!", e);
				AxolotlClientCommon.getInstance().getNotificationProvider().addStatus("gallery.image.crop.copy.failure", "gallery.image.crop.save.copy.description");
			}
		}).width(100).build());
		footerLine3.addChild(Button.builder(CommonComponents.GUI_BACK, btn -> onClose()).build());

		hfl.arrangeElements();
		hfl.visitWidgets(this::addRenderableWidget);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, 0, hfl.getHeaderHeight() - 2, 0.0F, 0.0F, this.width, 2, 32, 2);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, this.height - this.hfl.getFooterHeight(), 0.0F, 0.0F, this.width, 2, 32, 2);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		if (freeOnClose) {
			minecraft.getTextureManager().release(image.id());
		}
	}

	@Override
	protected void repositionElements() {
		if (imageWidget != null) {
			imageWidget.setHeight(hfl.getContentHeight());
			imageWidget.setWidth(hfl.getWidth());
		}
		hfl.arrangeElements();
	}

	private class ImageWidget extends AbstractWidget {
		private static final int DRAG_HANDLE_RADIUS = 7;
		private float dragX, dragY, dragX1, dragY1;
		@Nullable
		private DragHandle currentHandle = null;
		private final float imgAspectRatio;
		private int imgX, imgY, imgWidth, imgHeight;
		private float scale = 1f, translateX = 0f, translateY = 0f;

		@SuppressWarnings("resource")
		public ImageWidget(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty());
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
			updateEditBoxContents();
		}

		@Override
		public void setY(int y) {
			var diff0 = this.dragY - imgY;
			var diff1 = this.dragY1 - imgY;
			imgY = imgY - getY() + y;
			this.dragY = imgY + diff0;
			this.dragY1 = imgY + diff1;
			super.setY(y);
			updateEditBoxContents();
		}

		@Override
		public void setWidth(int width) {
			super.setWidth(width);
			setImgDimensions();
		}

		@Override
		public void setHeight(int height) {
			super.setHeight(height);
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
			updateEditBoxContents();
		}

		@Override
		protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
			guiGraphics.br$pushMatrix();
			guiGraphics.br$pushScissor(getX(), getY(), getWidth(), getHeight());
			guiGraphics.br$translateMatrix(CropImageScreen.this.width / 2f, CropImageScreen.this.height / 2f);
			guiGraphics.br$translateMatrix(translateX, translateY);
			guiGraphics.br$scaleMatrix(scale, scale);
			guiGraphics.br$translateMatrix(-CropImageScreen.this.width / 2f, -CropImageScreen.this.height / 2f);
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, image.id(), imgX, imgY, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
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
				if (minecraft.hasControlDown()) {
					guiGraphics.requestCursor(CursorTypes.RESIZE_ALL);
				} else if (currentHandle != null) {
					hoveredHandle = currentHandle;
					guiGraphics.requestCursor(currentHandle.cursor);
				} else {
					hoveredHandle = getHandle(getTransformedX(mouseX), getTransformedY(mouseY));
					if (hoveredHandle != null) {
						guiGraphics.requestCursor(hoveredHandle.cursor);
					}
				}
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
		public void playDownSound(SoundManager handler) {
		}

		@Override
		public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
			return null;
		}

		@Override
		public NarrationPriority narrationPriority() {
			return NarrationPriority.NONE;
		}

		@SuppressWarnings("resource")
		public NativeImage getCopyOfSelection() {
			var trueDragX = Math.round((dragX - imgX) / imgWidth * image.image().getWidth());
			var trueDragX1 = Math.round((dragX1 - imgX) / imgWidth * image.image().getWidth());
			var trueDragY = Math.round((dragY - imgY) / imgHeight * image.image().getHeight());
			var trueDragY1 = Math.round((dragY1 - imgY) / imgHeight * image.image().getHeight());
			var img = new NativeImage(trueDragX1 - trueDragX, trueDragY1 - trueDragY, false);
			image.image().copyRect(img, trueDragX, trueDragY, 0, 0, trueDragX1 - trueDragX, trueDragY1 - trueDragY, false, false);
			return img;
		}

		@Override
		protected void onDrag(MouseButtonEvent event, double mouseX, double mouseY) {
			if (event.hasControlDownWithQuirk()) {
				translateX += (float) mouseX;
				translateY += (float) mouseY;
				return;
			}
			final float transformedX = (float) getTransformedX(event.x());
			final float transformedY = (float) getTransformedY(event.y());
			if (currentHandle == null) {
				super.onDrag(event, mouseX, mouseY);
				return;
			}
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
					updateEditBoxContents();
					return;
				}
				default -> {
					super.onDrag(event, mouseX, mouseY);
					return;
				}
			}
			clampCrop();
			updateEditBoxContents();
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
			if (minecraft.hasControlDown()) {
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
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			if (currentHandle != null) {
				super.playDownSound(minecraft.getSoundManager());
			}
			currentHandle = null;
			super.onRelease(event);
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
		public void onClick(MouseButtonEvent event, boolean isDoubleClick) {
			var mouseX = getTransformedX(event.x());
			var mouseY = getTransformedY(event.y());
			currentHandle = getHandle(mouseX, mouseY);
			if (currentHandle != null) {
				clickDragOffsetX = currentHandle.getXOffset((float) mouseX, this);
				clickDragOffsetY = currentHandle.getYOffset((float) mouseY, this);
			}
			super.onClick(event, isDoubleClick);
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
		protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		}

		@SuppressWarnings({"DataFlowIssue", "resource"})
		private void updateEditBoxContents() {
			var trueDragX = Math.round((dragX - imgX) / imgWidth * image.image().getWidth());
			var trueDragX1 = Math.round((dragX1 - imgX) / imgWidth * image.image().getWidth());
			var trueDragY = Math.round((dragY - imgY) / imgHeight * image.image().getHeight());
			var trueDragY1 = Math.round((dragY1 - imgY) / imgHeight * image.image().getHeight());
			if (posX != null && !posX.isFocused()) {
				var res = ((EditBoxAccessor) posX).getResponder();
				posX.setResponder(null);
				posX.setValue(String.valueOf(trueDragX));
				posX.setResponder(res);
			}
			if (posY != null && !posY.isFocused()) {
				var res = ((EditBoxAccessor) posY).getResponder();
				posY.setResponder(null);
				posY.setValue(String.valueOf(trueDragY));
				posY.setResponder(res);
			}
			if (posW != null && !posW.isFocused()) {
				var res = ((EditBoxAccessor) posW).getResponder();
				posW.setResponder(null);
				posW.setValue(String.valueOf(trueDragX1 - trueDragX));
				posW.setResponder(res);
			}
			if (posH != null && !posH.isFocused()) {
				var res = ((EditBoxAccessor) posH).getResponder();
				posH.setResponder(null);
				posH.setValue(String.valueOf(trueDragY1 - trueDragY));
				posH.setResponder(res);
			}
		}

		@SuppressWarnings("resource")
		private void updateFromEditBoxes() {
			if (posX == null || posY == null || posW == null || posH == null) return;
			var xValue = posX.getValue();
			var yValue = posY.getValue();
			var wValue = posW.getValue();
			var hValue = posH.getValue();
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
			updateEditBoxContents();
		}

		@AllArgsConstructor
		private enum DragHandle {
			TOP_LEFT(ExtraCursorTypes.RESIZE_NWSE),
			TOP_CENTER(CursorTypes.RESIZE_NS),
			TOP_RIGHT(ExtraCursorTypes.RESIZE_NESW),
			LEFT_CENTER(CursorTypes.RESIZE_EW),
			RIGHT_CENTER(CursorTypes.RESIZE_EW),
			BOTTOM_LEFT(ExtraCursorTypes.RESIZE_NESW),
			BOTTOM_CENTER(CursorTypes.RESIZE_NS),
			BOTTOM_RIGHT(ExtraCursorTypes.RESIZE_NWSE),
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
