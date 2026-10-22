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

package io.github.axolotlclient.bridge.util

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.PlatformImplInternal
import io.github.axolotlclient.bridge.internal.RequiresImpl
import org.jetbrains.annotations.ApiStatus
import java.util.function.BiConsumer
import java.util.function.UnaryOperator

interface AxoText {
	// The Java original carried a redundant public `name` field whose value always equaled
	// the enum constant name; Kotlin enums cannot redeclare `name`, and no caller reads it.
	enum class Color(val charCode: Char, val id: Int, val rgb: Int) {
		BLACK('0', 0, 0),
		DARK_BLUE('1', 1, 170),
		DARK_GREEN('2', 2, 43520),
		DARK_AQUA('3', 3, 43690),
		DARK_RED('4', 4, 11141120),
		DARK_PURPLE('5', 5, 11141290),
		GOLD('6', 6, 16755200),
		GRAY('7', 7, 11184810),
		DARK_GRAY('8', 8, 5592405),
		BLUE('9', 9, 5592575),
		GREEN('a', 10, 5635925),
		AQUA('b', 11, 5636095),
		RED('c', 12, 16733525),
		LIGHT_PURPLE('d', 13, 16733695),
		YELLOW('e', 14, 16777045),
		WHITE('f', 15, 16777215);

		override fun toString(): String = "§" + charCode
	}

	interface Style {
		@RequiresImpl
		fun `br$color`(color: Color): Style = `br$color`(color.rgb)

		@RequiresImpl
		fun `br$color`(color: Int): Style {
			throw BridgeUtil.noImpl()
		}

		@RequiresImpl
		fun `br$tooltip`(text: AxoText): Style {
			throw BridgeUtil.noImpl()
		}
	}

	interface Mutable : AxoText {
		@ApiStatus.NonExtendable
		fun `br$color`(color: Color): Mutable = `br$setStyle`(`br$getStyle`().`br$color`(color))

		@ApiStatus.NonExtendable
		fun `br$color`(color: Int): Mutable = `br$setStyle`(`br$getStyle`().`br$color`(color))

		@ApiStatus.NonExtendable
		fun `br$color`(color: io.github.axolotlclient.AxolotlClientConfig.api.util.Color): Mutable = `br$color`(color.toInt())

		@RequiresImpl
		fun `br$append`(child: AxoText): Mutable {
			throw BridgeUtil.noImpl()
		}

		@ApiStatus.NonExtendable
		fun `br$append`(child: AxoText, color: Color): Mutable =
			`br$append`(child.`br$copy`().`br$color`(color))

		@ApiStatus.NonExtendable
		fun `br$append`(child: Any): Mutable = `br$append`(literal(child))

		@ApiStatus.NonExtendable
		fun `br$append`(child: Any, color: Color): Mutable =
			`br$append`(literal(child).`br$color`(color))

		@RequiresImpl
		fun `br$setStyle`(style: Style): Mutable {
			throw BridgeUtil.noImpl()
		}

		@ApiStatus.NonExtendable
		fun `br$withStyle`(op: UnaryOperator<Style>): Mutable = `br$setStyle`(op.apply(`br$getStyle`()))
	}

	@RequiresImpl
	fun `br$getRawString`(): String {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$copy`(): Mutable {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$visit`(handler: BiConsumer<String, Style>) {
		throw BridgeUtil.noImpl()
	}

	@RequiresImpl
	fun `br$getStyle`(): Style {
		throw BridgeUtil.noImpl()
	}

	companion object {
		@JvmStatic
		fun empty(): Mutable = PlatformImplInternal.createEmpty()

		@JvmStatic
		fun literal(value: String): Mutable = PlatformImplInternal.createLiteral(value)

		@JvmStatic
		fun literal(`object`: Any): Mutable = literal(`object`.toString())

		@JvmStatic
		fun translatable(key: String, vararg args: Any?): Mutable = PlatformImplInternal.createTranslatable(key, args)

		@JvmStatic
		fun strip(text: String): String = PlatformImplInternal.stripText(text)
	}
}
