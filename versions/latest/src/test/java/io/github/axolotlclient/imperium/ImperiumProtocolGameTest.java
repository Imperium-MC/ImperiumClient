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

package io.github.axolotlclient.imperium;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Checks the client against the wire format the ImperiumMC plugin uses: bare UTF-8 JSON written and read
 * with Bukkit plugin messaging (ImperiumClientIntegrationService).
 */
public class ImperiumProtocolGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		ImperiumClientSession session = ImperiumClientSession.getInstance();

		String features = "{\"version\":1,\"allow\":[\"auto_fish\"],\"deny\":[\"freelook\"]}";
		ByteBuf fromServer = Unpooled.wrappedBuffer(features.getBytes(StandardCharsets.UTF_8));
		var decoded = ImperiumIntegration.FeaturesPayload.CODEC.decode(fromServer);
		check(decoded.json().equals(features), "features payload should decode as plain UTF-8, got " + decoded.json());
		check(fromServer.readableBytes() == 0, "decoder should consume the whole payload");

		session.applyFeatures(decoded.json());
		check(session.serverResponded(), "a features packet should mark the server as responded");
		check(session.isAllowed("auto_fish"), "auto_fish should be allowed");
		check(session.isDenied("freelook"), "freelook should be denied");
		check(!session.isAllowed("auto_click"), "unlisted modules stay locked");

		session.applyFeatures("{\"version\":1,\"allow\":[]}");
		check(!session.isAllowed("auto_fish"), "a new packet replaces earlier grants");

		session.applyFeatures("{\"version\":1,\"allow\":[\"auto_fish\"]}");
		session.applyFeatures("{not json");
		check(session.isAllowed("auto_fish"), "a malformed packet leaves the last valid state untouched");

		session.reset();
		check(!session.serverResponded() && !session.isAllowed("auto_fish"), "reset clears grants");

		String hello = "{\"version\":\"0.1.0\",\"game\":\"26.3\",\"features\":[\"protocol_v1\"]}";
		ByteBuf toServer = Unpooled.buffer();
		ImperiumIntegration.HelloPayload.CODEC.encode(toServer, new ImperiumIntegration.HelloPayload(hello));
		String onServer = toServer.toString(StandardCharsets.UTF_8);
		check(onServer.equals(hello), "hello should reach the plugin as plain UTF-8, got " + onServer);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
