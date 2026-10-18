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

package io.github.axolotlclient.imperium;

import io.github.axolotlclient.AxolotlClientCommon;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * The client half of the ImperiumMC handshake (roadmap M5 slice 1; the server half is
 * ImperiumClientIntegrationService in the ImperiumMC plugin). Two channels:
 *
 *  - {@code imperiumclient:hello} (serverbound, this class sends): a JSON introduction
 *    {"version":"<mod version>","features":[...]} sent right after join so the server
 *    can mark the session first-party and reply with grants immediately.
 *  - {@code imperiumclient:features} (clientbound, this class receives): the server's
 *    gated-module grant/deny packet per docs/server-gated-features.md. Applied to
 *    {@link ImperiumClientSession}; without it everything gated stays locked — the
 *    protocol's own safe default.
 *
 * Follows the FeatureDisabler channel pattern (PayloadTypeRegistry + custom payload
 * record) so it composes with the Fabric networking stack rather than raw plugin messages.
 */
public final class ImperiumIntegration {

	private static final String NAMESPACE = "imperiumclient";
	private static final CustomPacketPayload.Type<HelloPayload> HELLO =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, "hello"));
	private static final CustomPacketPayload.Type<FeaturesPayload> FEATURES =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, "features"));

	private static boolean initialized = false;

	public static synchronized void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		// Serverbound hello: the server marks the session first-party on this channel.
		PayloadTypeRegistry.serverboundPlay().register(HELLO, HelloPayload.CODEC);
		// Clientbound features: grants/denies from the server.
		PayloadTypeRegistry.clientboundPlay().register(FEATURES, FeaturesPayload.CODEC);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ImperiumClientSession.getInstance().reset();
			if (sender != null) {
				sender.sendPacket(new HelloPayload(helloJson()));
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
			ImperiumClientSession.getInstance().reset());

		ClientPlayNetworking.registerGlobalReceiver(FEATURES, (payload, ctx) ->
			ctx.client().execute(() -> ImperiumClientSession.getInstance().applyFeatures(payload.json())));

		AxolotlClientCommon.getInstance().getLogger().info("ImperiumMC integration ready (hello/features channels)");
	}

	/**
	 * {"version":"0.1.0-alpha.1","game":"26.3","features":["protocol_v1"]} — the mod version
	 * (AxolotlClientCommon.VERSION, from fabric.mod.json at build time), the game version, and
	 * the feature flags the client currently implements that a server may care about.
	 */
	private static String helloJson() {
		return "{\"version\":\"" + jsonSafe(AxolotlClientCommon.VERSION) + "\","
			+ "\"game\":\"" + jsonSafe(AxolotlClientCommon.GAME_VERSION) + "\","
			+ "\"features\":[\"protocol_v1\"]}";
	}

	private static String jsonSafe(String s) {
		return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/**
	 * RAW UTF-8 stream codec for both payloads. The ImperiumMC plugin speaks Bukkit plugin
	 * messaging, which carries BARE UTF-8 JSON — no length prefix, no NBT framing. A
	 * {@code ByteBufCodecs.fromCodec(Codec.STRING)} reads NBT on decode and fails on bare
	 * bytes, DISCONNECTING the player (found in live testing against the plugin's exact
	 * output; see docs/dev-notes.md "Server protocol"). Encode writes the raw bytes; decode
	 * consumes the whole remaining buffer as UTF-8.
	 */
	static final StreamCodec<ByteBuf, String> RAW_UTF8 = new StreamCodec<>() {
		@Override
		public String decode(ByteBuf buf) {
			var bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
		}

		@Override
		public void encode(ByteBuf buf, String value) {
			buf.writeBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	};

	@NullMarked
	public record HelloPayload(String json) implements CustomPacketPayload {
		public static final StreamCodec<ByteBuf, HelloPayload> CODEC =
			RAW_UTF8.map(HelloPayload::new, HelloPayload::json);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return HELLO;
		}
	}

	@NullMarked
	public record FeaturesPayload(String json) implements CustomPacketPayload {
		public static final StreamCodec<ByteBuf, FeaturesPayload> CODEC =
			RAW_UTF8.map(FeaturesPayload::new, FeaturesPayload::json);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return FEATURES;
		}
	}
}
