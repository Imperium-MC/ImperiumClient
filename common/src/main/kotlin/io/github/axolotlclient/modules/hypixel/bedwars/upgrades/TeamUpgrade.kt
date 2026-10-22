/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.hypixel.bedwars.upgrades

import java.util.regex.Matcher
import java.util.regex.Pattern

import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMessages
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMode

/**
 * @author DarkKronicle
 */
abstract class TeamUpgrade(val name: String, protected val regex: Array<Pattern>) {

    constructor(name: String, pattern: Pattern) : this(name, arrayOf(pattern))

    fun match(unformatedMessage: String): Boolean =
        BedwarsMessages.matched(regex, unformatedMessage) { matcher -> onMatch(this, matcher) }

    abstract fun draw(context: AxoRenderContext, x: Int, y: Int, width: Int, height: Int)

    open fun isMultiUpgrade(): Boolean {
        // Basically only trap
        return false
    }

    protected abstract fun onMatch(upgrade: TeamUpgrade, matcher: Matcher)

    abstract fun getPrice(mode: BedwarsMode): Int

    abstract fun isPurchased(): Boolean
}
