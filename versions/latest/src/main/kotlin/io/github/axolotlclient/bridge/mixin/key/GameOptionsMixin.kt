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

import io.github.axolotlclient.bridge.AxoGameOptions
import io.github.axolotlclient.bridge.AxoPerspective
import io.github.axolotlclient.bridge.key.AxoKeybinding
import net.minecraft.client.CameraType
import net.minecraft.client.KeyMapping
import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@Mixin(Options::class)
abstract class GameOptionsMixin : AxoGameOptions {
	@Shadow
	@field:Final
	private val keySprint: KeyMapping? = null

	@Shadow
	@field:Final
	private val keyShift: KeyMapping? = null

	@Shadow
	@field:Final
	private val keyAttack: KeyMapping? = null

	@Shadow
	@field:Final
	private val keyUse: KeyMapping? = null

	@Shadow
	abstract fun getCameraType(): CameraType

	@Shadow
	abstract fun setCameraType(pointOfView: CameraType)

	@Shadow
	private var smoothCamera: Boolean = false

	@Shadow
	@field:Final
	private val sensitivity: OptionInstance<Double>? = null

	override fun `br$getSprintKeybind`(): AxoKeybinding = keySprint!!

	override fun `br$getSneakKeybind`(): AxoKeybinding = keyShift!! // great naming, mojang!

	override fun `br$getAttackKey`(): AxoKeybinding = keyAttack!!

	override fun `br$getUseKey`(): AxoKeybinding = keyUse!!

	override fun `br$getCameraType`(): AxoPerspective = when (getCameraType()) {
		CameraType.FIRST_PERSON -> AxoPerspective.FIRST_PERSON
		CameraType.THIRD_PERSON_BACK -> AxoPerspective.THIRD_PERSON_BACK
		CameraType.THIRD_PERSON_FRONT -> AxoPerspective.THIRD_PERSON_FRONT
	}

	override fun `br$setCameraType`(perspective: AxoPerspective) {
		setCameraType(
			when (perspective) {
				AxoPerspective.FIRST_PERSON -> CameraType.FIRST_PERSON
				AxoPerspective.THIRD_PERSON_BACK -> CameraType.THIRD_PERSON_BACK
				AxoPerspective.THIRD_PERSON_FRONT -> CameraType.THIRD_PERSON_FRONT
			}
		)
	}

	override fun `br$getSmoothCamera`(): Boolean = smoothCamera

	override fun `br$setSmoothCamera`(c: Boolean) {
		smoothCamera = c
	}

	override fun `br$getSensitivity`(): Double = sensitivity!!.get()

	override fun `br$setSensitivity`(s: Double) {
		sensitivity!!.set(s)
	}
}
