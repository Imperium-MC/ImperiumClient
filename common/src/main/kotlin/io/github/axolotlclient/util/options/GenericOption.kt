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

import io.github.axolotlclient.AxolotlClientConfig.impl.options.OptionBase

open class GenericOption(name: String, val label: String, listener: ActionListener) :
    OptionBase<GenericOption.ActionListener>(name, listener) {

    override fun set(value: ActionListener?) {
    }

    override fun get(): ActionListener? = getDefault()

    override fun toSerializedValue(): String? = null

    override fun fromSerializedValue(s: String?) {
    }

    override fun getWidgetIdentifier(): String = "generic"

    fun interface ActionListener {
        fun onClick()
    }
}
