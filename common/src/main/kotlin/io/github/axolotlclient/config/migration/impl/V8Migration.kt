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

package io.github.axolotlclient.config.migration.impl

import com.google.gson.JsonObject
import io.github.axolotlclient.config.migration.ConfigMigration

class V8Migration : ConfigMigration {
    override fun version() = 8

    override fun apply(config: JsonObject) {
        getObject(config, "nametagOptions").ifPresent { n ->
            val showOwnNametag = getBooleanOrFalse(n, "showOwnNametag")
            val shadows = getBooleanOrFalse(n, "useShadows")
            val nametagBackground = getBooleanOrFalse(n, "nametagBackground")
            val badges = getBooleanOrFalse(n, "showBadges")
            val customBadge = getBooleanOrFalse(n, "customBadge")
            val customBadgeText = getString(n, "customBadgeText")
            val rendering = getOrAddObject(config, "rendering")
            val nametagRenderingOptions = JsonObject()
            rendering.add("nametagOptions", nametagRenderingOptions)
            nametagRenderingOptions.addProperty("showOwnNametag", showOwnNametag)
            nametagRenderingOptions.addProperty("useShadows", shadows)
            nametagRenderingOptions.addProperty("nametagBackground", nametagBackground)
            val apiOptions = getOrAddObject(config, "api.category")
            val badgeOptions = JsonObject()
            apiOptions.add("api.badge_options", badgeOptions)
            badgeOptions.addProperty("showBadges", badges)
            badgeOptions.addProperty("customBadge", customBadge)
            customBadgeText.ifPresent { s -> badgeOptions.addProperty("customBadgeText", s) }
        }
    }
}
