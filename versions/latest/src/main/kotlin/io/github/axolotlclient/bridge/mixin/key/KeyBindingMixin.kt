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

package io.github.axolotlclient.bridge.mixin.key

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.key.AxoKey
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.util.keybinds.KeyBinds
import net.minecraft.client.KeyMapping
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * An abstract representation of a keybind
 */
@Mixin(KeyMapping::class)
abstract class KeyBindingMixin : AxoKeybinding {
	@Shadow
	abstract fun isDown(): Boolean

	@Shadow
	protected var key: InputConstants.Key? = null

	@Shadow
	abstract fun consumeClick(): Boolean

	@Shadow
	private var clickCount = 0

	// @Unique field names may not contain `$` in Kotlin; mixin only requires uniqueness.
	@Unique
	private var axolotlclientOnClicked: MutableList<Runnable>? = null

	@Unique
	private var axolotlclientOnConsumeClick: MutableList<Runnable>? = null

	@Unique
	private var axolotlclientOnReleased: MutableList<Runnable>? = null

	@Inject(method = ["setDown"], at = [At("HEAD")])
	private fun dispatchHandlers(pressed: Boolean, ci: CallbackInfo) {
		val handlers = if (pressed) axolotlclientOnClicked else axolotlclientOnReleased
		handlers?.forEach { it.run() }
	}

	@Inject(method = ["<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants\$Type;ILnet/minecraft/client/KeyMapping\$Category;)V"], at = [At("TAIL")])
	private fun registerClickHandler(string: String, type: InputConstants.Type, i: Int, category: KeyMapping.Category, ci: CallbackInfo) {
		Events.TICK.register(Runnable {
			while (axolotlclientOnConsumeClick != null && consumeClick()) {
				axolotlclientOnConsumeClick!!.forEach { it.run() }
			}
		})
	}

	override fun `br$registerOnClicked`(runnable: Runnable) {
		if (axolotlclientOnClicked == null) {
			axolotlclientOnClicked = ArrayList()
		}
		axolotlclientOnClicked!!.add(runnable)
	}

	override fun `br$registerOnReleased`(runnable: Runnable) {
		if (axolotlclientOnReleased == null) {
			axolotlclientOnReleased = ArrayList()
		}
		axolotlclientOnReleased!!.add(runnable)
	}

	override fun `br$registerOnConsumeClick`(runnable: Runnable) {
		if (axolotlclientOnConsumeClick == null) {
			axolotlclientOnConsumeClick = ArrayList()
		}
		axolotlclientOnConsumeClick!!.add(runnable)
	}

	override fun `br$isPressed`(): Boolean = isDown()

	override fun `br$getBoundKey`(): AxoKey = key!!

	override fun `br$consumeClick`(): Boolean = consumeClick()

	override fun `br$click`() {
		clickCount++
	}
}
