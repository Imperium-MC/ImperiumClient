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

package io.github.axolotlclient.modules.mcci;

import com.noxcrew.noxesium.core.mcc.MccPackets;
import com.noxcrew.noxesium.core.mcc.ClientboundMccGameStatePacket;
import com.noxcrew.noxesium.core.mcc.ClientboundMccServerPacket;
import io.github.axolotlclient.api.Request;
import io.github.axolotlclient.api.requests.StatusUpdate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public class NoxesiumIntegration {

	public static final boolean NOXESIUM_INSTALLED = FabricLoader.getInstance().isModLoaded("noxesium");

	private ClientboundMccServerPacket currentServer;
	private ClientboundMccGameStatePacket currentGameState;

	private boolean inizialized = false;

	public void init() {
		if (NOXESIUM_INSTALLED) {
			ClientTickEvents.END_CLIENT_TICK.register(c -> {
				if (!inizialized) {
					inizialized = true;

					MccPackets.CLIENTBOUND_MCC_SERVER.addListener(this, ClientboundMccServerPacket.class, (self, packet, uuid) -> currentServer = packet);
					MccPackets.CLIENTBOUND_MCC_GAME_STATE.addListener(this, ClientboundMccGameStatePacket.class, (self, packet, uuid) -> currentGameState = packet);
				}
			});

		}
	}

	public Request getCurrentStatus() {
		if (currentServer != null) {
			String mapName = "";
			if (currentGameState != null) {
				mapName = currentGameState.mapName();
			}
			return StatusUpdate.inGame(StatusUpdate.SupportedServer.MCC_ISLAND, MccIslandGameType.getServerType(currentServer.server()).getName(), "", mapName);
		}

		return null;
	}
}
