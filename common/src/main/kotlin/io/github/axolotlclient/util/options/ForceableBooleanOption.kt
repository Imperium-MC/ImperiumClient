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

package io.github.axolotlclient.util.options

import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.OptionBase

class ForceableBooleanOption : BooleanOption {
    constructor(name: String, defaultValue: Boolean) : super(name, defaultValue)

    constructor(name: String, defaultValue: Boolean, changeListener: OptionBase.ChangeListener<Boolean>) :
            super(name, defaultValue, changeListener)

    var isForceOff: Boolean = false
        private set
    var reason: String? = null
        private set

    override fun get(): Boolean = !isForceOff && super.get()!!

    fun setForceOff(value: Boolean, reason: String) {
        isForceOff = value
        this.reason = reason
    }

    override fun getTooltip(): String? = if (isForceOff) reason else super.getTooltip()

    override fun getWidgetIdentifier(): String = "forceableboolean"
}
