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

package io.github.axolotlclient.modules.chat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.axolotlclient.AxolotlClientCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The capture half of the chat screenshot button ({@code chat_screenshot_button}): reads the
 * main render target back, crops it to the chat rectangle, and saves it under a
 * {@code chat-} namespace in the screenshots directory (so galleries group the two kinds).
 * If the render target is not reachable or the crop would be empty, the fallback saves the
 * full frame under the same {@code chat-} prefix rather than failing silently.
 *
 * <p>The rectangle math is a pure function ({@link #cropRect}) so the GUI-to-pixel mapping
 * is unit-testable; everything else is the vanilla readback path
 * ({@link Screenshot#takeScreenshot}), which also means the resulting image is already
 * top-down oriented.</p>
 */
public final class ChatScreenshot {

	private ChatScreenshot() {
	}

	/** A pixel rectangle inside a screenshot image. */
	@VisibleForTesting
	public record Rect(int x, int y, int width, int height) {
	}

	/**
	 * Maps the chat rectangle from GUI units to screenshot pixels.
	 *
	 * <p>The chat occupies, in GUI units, {@code x in [0, chatWidth]}, {@code y in
	 * [guiHeight - 40 - chatHeight, guiHeight - 40]} (vanilla's 40 px bottom margin), where
	 * {@code chatWidth}/{@code chatHeight} are the chat-scale-inclusive sizes
	 * {@code ChatComponent.getWidth() / getHeight()} report. {@code pad} GUI pixels of
	 * context are kept around it so anti-aliased edges survive the crop.</p>
	 *
	 * @param imageWidth  screenshot width in pixels
	 * @param imageHeight screenshot height in pixels
	 * @param guiScale    the window's GUI scale (screenshot pixel per GUI unit)
	 */
	@VisibleForTesting
	public static Rect cropRect(int imageWidth, int imageHeight, double guiScale, int chatWidth, int chatHeight, int guiHeight, int pad) {
		int left = clampPixel(-pad, guiScale, imageWidth);
		int right = clampPixel(chatWidth + pad, guiScale, imageWidth);
		int bottom = clampPixel(guiHeight - 40 + pad, guiScale, imageHeight);
		int top = clampPixel(guiHeight - 40 - chatHeight - pad, guiScale, imageHeight);
		// Keep the origin one pixel inside the image even in the degenerate case (a chat
		// larger than the image clamps top and bottom to the same edge), so the guaranteed
		// non-empty 1px crop still stays within the image.
		int x = Math.min(Math.max(0, left), Math.max(0, imageWidth - 1));
		int y = Math.min(Math.max(0, top), Math.max(0, imageHeight - 1));
		int w = Math.max(1, Math.min(right, imageWidth) - x);
		int h = Math.max(1, Math.min(bottom, imageHeight) - y);
		return new Rect(x, y, w, h);
	}

	private static int clampPixel(int guiUnits, double guiScale, int imageBound) {
		return Math.max(0, Math.min(imageBound, (int) Math.round(guiUnits * guiScale)));
	}

	/**
	 * Captures the chat region of the current frame. Must run on the render/client thread
	 * (the chat screen button's click handler).
	 */
	public static void capture(Minecraft client) {
		RenderTarget target;
		try {
			target = client.gameRenderer.mainRenderTarget();
		} catch (Exception e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Chat screenshot: render target unreachable", e);
			return;
		}
		// The focused sizes are what the open chat screen displays. The width helper is
		// already GUI units; the height helper is chat-local units and the chat-scale option
		// multiplies it on screen, so scale it here to keep the crop honest at scale != 1.
		int chatWidth = ChatComponent.getWidth(client.options.chatWidth().get());
		double chatScale = Math.max(0.0, client.options.chatScale().get());
		int chatHeight = (int) Math.ceil(ChatComponent.getHeight(client.options.chatHeightFocused().get()) * chatScale);
		Rect rect = cropRect(target.width, target.height, client.getWindow().getGuiScale(),
			chatWidth, chatHeight, client.getWindow().getGuiScaledHeight(), 2);
		boolean fullFallback = rect.width <= 1 || rect.height <= 1 || rect.width >= target.width - 1 && rect.height >= target.height - 1;
		Screenshot.takeScreenshot(target, image -> cropAndSave(image, rect, fullFallback));
	}

	private static void cropAndSave(NativeImage image, Rect rect, boolean fullFallback) {
		try (image) {
			NativeImage result;
			if (fullFallback) {
				result = image;
			} else {
				NativeImage cropped = new NativeImage(rect.width(), rect.height(), false);
				cropped.copyRect(image, rect.x(), rect.y(), rect.width(), rect.height(), 0, 0, false, false);
				result = cropped;
			}
			Path dest = destination();
			CompletableFuture.runAsync(() -> {
				try {
					result.writeToFile(dest.toFile());
					io.github.axolotlclient.util.Util.addMessageToChatHud(Component.translatable("chat_screenshot.saved"));
				} catch (IOException e) {
					AxolotlClientCommon.getInstance().getLogger().warn("Chat screenshot: write failed", e);
					io.github.axolotlclient.util.Util.addMessageToChatHud(Component.translatable("chat_screenshot.failed"));
				} finally {
					if (result != image) {
						result.close();
					}
				}
			}, Util.ioPool());
		}
	}

	/** {@code screenshots/chat-<datetime>[_n].png}, collision-suffixed like vanilla's getFile. */
	private static Path destination() {
		Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(Screenshot.SCREENSHOT_DIR);
		String stamp = Util.getFilenameFormattedDateTime();
		for (int i = 1; i < 1000; i++) {
			Path candidate = dir.resolve("chat-" + stamp + (i == 1 ? "" : "_" + i) + ".png");
			if (!java.nio.file.Files.exists(candidate)) {
				return candidate;
			}
		}
		return dir.resolve("chat-" + stamp + "-" + System.nanoTime() + ".png");
	}
}
