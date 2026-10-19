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

public class V11Migration implements ConfigMigration {
	@Override
	public int version() {
		return 11;
	}

	@Override
	public void apply(JsonObject config) {
		// The no-hurt-cam boolean became the hurt_cam enum (Alpine parity): true -> OFF, false -> FULL.
		// Enum options serialize via toString(), so the migrated value uses the "hurt_cam.<mode>" form.
		getObject(config, "rendering").ifPresent(rendering -> {
			if (rendering.has("noHurtCam") && rendering.get("noHurtCam").isJsonPrimitive()
				&& rendering.get("noHurtCam").getAsJsonPrimitive().isBoolean()) {
				boolean noHurtCam = rendering.get("noHurtCam").getAsBoolean();
				rendering.remove("noHurtCam");
				rendering.addProperty("hurt_cam", noHurtCam ? "hurt_cam.off" : "hurt_cam.full");
			}
		});
	}
}
