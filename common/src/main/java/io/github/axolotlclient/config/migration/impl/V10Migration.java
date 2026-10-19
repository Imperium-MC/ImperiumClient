/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.config.migration.impl;

import com.google.gson.JsonObject;
import io.github.axolotlclient.config.migration.ConfigMigration;

public class V10Migration implements ConfigMigration {
	@Override
	public int version() {
		return 10;
	}

	@Override
	public void apply(JsonObject config) {
		// The nametag background option became an enum: true -> TRANSLUCENT, false -> NONE.
		getObject(config, "rendering").flatMap(rendering -> getObject(rendering, "nametagOptions")).ifPresent(nametags -> {
			if (nametags.has("nametagBackground") && nametags.get("nametagBackground").isJsonPrimitive()
				&& nametags.get("nametagBackground").getAsJsonPrimitive().isBoolean()) {
				boolean background = nametags.get("nametagBackground").getAsBoolean();
				nametags.addProperty("nametagBackground", background ? "TRANSLUCENT" : "NONE");
			}
		});
	}
}
