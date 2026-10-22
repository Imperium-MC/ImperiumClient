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

package io.github.axolotlclient.bridge.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode

// fun interface keeps the Java lambda usage in the version-side Bridge
// (`COMMAND_REGISTER.invoker().accept(() -> dispatcher)`) convertible.
fun interface Commands {
	fun getDispatcher(): CommandDispatcher<AxoClientCmdSrcStack>

	fun register(node: LiteralArgumentBuilder<AxoClientCmdSrcStack>): LiteralCommandNode<AxoClientCmdSrcStack> =
		getDispatcher().register(node)

	companion object {
		@JvmStatic
		fun literal(literal: String): LiteralArgumentBuilder<AxoClientCmdSrcStack> =
			LiteralArgumentBuilder.literal(literal)

		@JvmStatic
		fun <T> argument(name: String, arg: ArgumentType<T>): RequiredArgumentBuilder<AxoClientCmdSrcStack, T> =
			RequiredArgumentBuilder.argument(name, arg)
	}
}
