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

package io.github.axolotlclient.bridge.mixin.internal;

import java.util.Objects;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption;
import io.github.axolotlclient.bridge.AxoMinecraftClient;
import io.github.axolotlclient.bridge.AxoPlayerListEntry;
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffect;
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffectInstance;
import io.github.axolotlclient.bridge.impl.AxoSpriteImpl;
import io.github.axolotlclient.bridge.internal.PlatformImplInternal;
import io.github.axolotlclient.bridge.item.AxoItem;
import io.github.axolotlclient.bridge.item.AxoItemStack;
import io.github.axolotlclient.bridge.key.AxoKey;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.bridge.render.AxoSprite;
import io.github.axolotlclient.bridge.render.AxoWindow;
import io.github.axolotlclient.bridge.scores.AxoTeam;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.bridge.util.AxoProfiler;
import io.github.axolotlclient.bridge.util.AxoText;
import io.github.axolotlclient.mixin.MinecraftClientAccessor;
import io.github.axolotlclient.util.keybinds.KeyBinds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = PlatformImplInternal.class, remap = false)
public abstract class PlatformImplInternalMixin {
	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoWindow getWindow() {
		return Minecraft.getInstance().getWindow();
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoMinecraftClient getMinecraftClientInstance() {
		return Minecraft.getInstance();
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoProfiler getProfiler() {
		return Profiler.get();
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static String getTranslatedString(String nameKey, Object[] args) {
		return I18n.get(nameKey, args);
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoItemStack createItemStack(AxoItem item, int count) {
		return new ItemStack((Item) item, count);
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static long getMeasuringTimeMs() {
		return Util.getMillis();
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static int getCurrentFps() {
		return MinecraftClientAccessor.axolotlclient$getCurrentFps();
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoKeybinding createKeyBinding(AxoKey defaultKey, String name) {
		int code = ((InputConstants.Key) Objects.requireNonNullElse(defaultKey, AxoKeys.KEY_UNKNOWN)).getValue();
		final var binding = new KeyMapping(name, code, KeyBinds.CATEGORY_AXOLOTLCLIENT);
		KeyBinds.getInstance().register(binding);
		return binding;
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoIdentifier createIdentifier(String ns, String path) {
		return Identifier.fromNamespaceAndPath(ns, path);
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoIdentifier parseIdentifier(String id) {
		return Identifier.parse(id);
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoText.Mutable createEmpty() {
		return Component.empty();
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoText.Mutable createLiteral(String text) {
		return Component.literal(text);
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoText.Mutable createTranslatable(String key, Object... args) {
		return Component.translatable(key, args);
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static int tickCount() {
		return Minecraft.getInstance().gui.hud.getGuiTicks();
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static String stripText(String text) {
		return ChatFormatting.stripFormatting(text);
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static String getTabNameFor(AxoPlayerListEntry player) {
		// Inlined PlayerListHud#getDisplayName to avoid StackOverflowError due to mixin
		PlayerInfo p = (PlayerInfo) player;
		if (p.getTabListDisplayName() != null) {
			MutableComponent name = p.getTabListDisplayName().copy();
			return ChatFormatting.stripFormatting((p.getGameMode() == GameType.SPECTATOR ? name.withStyle(ChatFormatting.ITALIC) : name).getString());
		}
		MutableComponent name = PlayerTeam.formatNameForTeam(p.getTeam(), Component.literal(p.getProfile().name()));
		return ChatFormatting.stripFormatting((p.getGameMode() == GameType.SPECTATOR ? name.withStyle(ChatFormatting.ITALIC) : name).getString());
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static void setTabListHeader(AxoText text) {
		Minecraft.getInstance().gui.hud.getTabList().setHeader((Component) text);
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoStatusEffectInstance createStatusEffectInstance(AxoStatusEffect effect, int duration) {
		return new MobEffectInstance(Holder.direct((MobEffect) effect), duration);
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static AxoSprite createTexture(GraphicsOption option) {
		return (AxoSpriteImpl) (client, stack, sX, sY, sW, sH, color) ->
			stack.blit(
				RenderPipelines.GUI_TEXTURED,
				io.github.axolotlclient.util.Util.getTexture(option), sX, sY, 0, 0,
				sW, sH, option.get().getWidth(), option.get().getHeight()
			);
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	public static String getTeamMemberDisplayName(AxoTeam team, String s) {
		return PlayerTeam.formatNameForTeam((Team) team, Component.literal(s)).getString();
	}
}
