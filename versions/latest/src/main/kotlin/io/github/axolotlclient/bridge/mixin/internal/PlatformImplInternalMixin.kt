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

package io.github.axolotlclient.bridge.mixin.internal

import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.AxoPlayerListEntry
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffect
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffectInstance
import io.github.axolotlclient.bridge.impl.AxoSpriteImpl
import io.github.axolotlclient.bridge.internal.PlatformImplInternal
import io.github.axolotlclient.bridge.item.AxoItem
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.key.AxoKey
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.render.AxoSprite
import io.github.axolotlclient.bridge.render.AxoWindow
import io.github.axolotlclient.bridge.scores.AxoTeam
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.bridge.util.AxoProfiler
import io.github.axolotlclient.bridge.util.AxoText
import io.github.axolotlclient.mixin.MinecraftClientAccessor
import io.github.axolotlclient.util.Util
import io.github.axolotlclient.util.keybinds.KeyBinds
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Util.getMillis
import net.minecraft.util.profiling.Profiler
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Overwrite
import java.util.Objects

@Mixin(PlatformImplInternal.Companion::class, remap = false)
abstract class PlatformImplInternalMixin {
	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun getWindow(): AxoWindow? = Minecraft.getInstance().window

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun getMinecraftClientInstance(): AxoMinecraftClient = Minecraft.getInstance()

	/**
	 * @author moehreag
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun getProfiler(): AxoProfiler = Profiler.get()

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun getTranslatedString(nameKey: String, args: Array<out Any?>): String {
		// NullMarked vararg: the bridge passes whatever AxoI18n received (Java Object[]),
		// so spread through the documented unchecked array cast.
		@Suppress("UNCHECKED_CAST")
		val cast = args as Array<Any>
		return I18n.get(nameKey, *cast)
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun createItemStack(item: AxoItem, count: Int): AxoItemStack = ItemStack(item as Item, count)

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun getMeasuringTimeMs(): Long = getMillis()

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun getCurrentFps(): Int = MinecraftClientAccessor.`axolotlclient$getCurrentFps`()

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun createKeyBinding(defaultKey: AxoKey?, name: String): io.github.axolotlclient.bridge.key.AxoKeybinding {
		val code = (Objects.requireNonNullElse(defaultKey, AxoKeys.KEY_UNKNOWN)
			as com.mojang.blaze3d.platform.InputConstants.Key).value
		val binding = KeyMapping(name, code, KeyBinds.CATEGORY_AXOLOTLCLIENT)
		KeyBinds.getInstance().register(binding)
		return binding
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun createIdentifier(ns: String, path: String): AxoIdentifier = Identifier.fromNamespaceAndPath(ns, path)

	/**
	 * @author moehreag
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun parseIdentifier(id: String): AxoIdentifier = Identifier.parse(id)

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun createLiteral(text: String): AxoText.Mutable = Component.literal(text)

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun createTranslatable(key: String, vararg args: Any?): AxoText.Mutable {
		@Suppress("UNCHECKED_CAST")
		val cast = args as Array<Any>
		return Component.translatable(key, *cast)
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun tickCount(): Int = Minecraft.getInstance().gui.hud.getGuiTicks()

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun stripText(text: String): String = ChatFormatting.stripFormatting(text)!!

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun getTabNameFor(player: AxoPlayerListEntry): String {
		// Inlined PlayerListHud#getDisplayName to avoid StackOverflowError due to mixin
		val p = player as PlayerInfo
		if (p.tabListDisplayName != null) {
			val name = p.tabListDisplayName!!.copy()
			return ChatFormatting.stripFormatting(
				(if (p.gameMode == GameType.SPECTATOR) name.withStyle(ChatFormatting.ITALIC) else name).string
			)!!
		}
		val name = PlayerTeam.formatNameForTeam(p.team, Component.literal(p.profile.name))
		return ChatFormatting.stripFormatting(
			(if (p.gameMode == GameType.SPECTATOR) name.withStyle(ChatFormatting.ITALIC) else name).string
		)!!
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun setTabListHeader(text: AxoText) {
		Minecraft.getInstance().gui.hud.tabList.setHeader(text as Component)
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun createStatusEffectInstance(effect: AxoStatusEffect, duration: Int): AxoStatusEffectInstance =
		MobEffectInstance(Holder.direct(effect as MobEffect), duration)

	/**
	 * @author Flowey
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun createTexture(option: GraphicsOption): AxoSprite = AxoSpriteImpl { _, stack, sX, sY, sW, sH, _ ->
		stack.blit(
			RenderPipelines.GUI_TEXTURED,
			Util.getTexture(option), sX, sY, 0f, 0f,
			sW, sH, option.get().width, option.get().height
		)
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge platform.
	 */
	@Overwrite
	fun getTeamMemberDisplayName(team: AxoTeam, s: String): String =
		PlayerTeam.formatNameForTeam(team as Team, Component.literal(s)).string
}
