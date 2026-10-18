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

package io.github.axolotlclient.bridge.mixin;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.texture.NativeImage;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.api.util.MojangAuth;
import io.github.axolotlclient.bridge.PlatformDispatch;
import io.github.axolotlclient.bridge.impl.AxoSpriteImpl;
import io.github.axolotlclient.bridge.render.AxoSprite;
import io.github.axolotlclient.mixin.MinecraftServerAccessor;
import io.github.axolotlclient.modules.hypixel.autoboop.FilterListConfigurationScreen;
import io.github.axolotlclient.modules.hypixel.bedwars.SessionStatsHudEntryConfigScreen;
import io.github.axolotlclient.util.ClientColors;
import io.github.axolotlclient.util.ThreadExecuter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.network.Address;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.listener.ClientQueryPacketListener;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.query.MetadataQueryC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.s2c.query.QueryPongS2CPacket;
import net.minecraft.network.packet.s2c.query.ServerMetadataS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.mutable.MutableInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = PlatformDispatch.class, remap = false)
public abstract class PlatformDispatchMixin {
	@Unique
	private static void getRealTimeServerPing(ServerInfo server, MutableInt currentServerPing) {
		ThreadExecuter.scheduleTask(() -> {
			try {
				var address = ServerAddress.parse(server.address);
				var optional = AllowedAddressResolver.DEFAULT.resolve(address).map(Address::getInetSocketAddress);

				if (optional.isPresent()) {
					final ClientConnection clientConnection = ClientConnection.connect(optional.get(), false);
					ClientQueryPacketListener listener = new ClientQueryPacketListener() {

						private long currentSystemTime = 0L;

						@Override
						public void onServerMetadata(ServerMetadataS2CPacket packet) {
							this.currentSystemTime = net.minecraft.util.Util.getMeasuringTimeMs();
							clientConnection.send(new QueryPingC2SPacket(this.currentSystemTime));
						}

						@Override
						public void onPong(QueryPongS2CPacket packet) {
							var time = this.currentSystemTime;
							var latency = net.minecraft.util.Util.getMeasuringTimeMs();
							currentServerPing.setValue((int) (latency - time));
							clientConnection.disconnect(Text.translatable("multiplayer.status.finished"));
						}

						@Override
						public void onDisconnected(Text reason) {
						}

						@Override
						public boolean isConnected() {
							return clientConnection.isOpen();
						}
					};
					clientConnection.setPacketListener(listener);
					clientConnection.send(new HandshakeC2SPacket(address.getAddress(), address.getPort(),
						NetworkState.STATUS));
					clientConnection.send(new MetadataQueryC2SPacket());
				}
			} catch (Exception ignored) {
			}
		});
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge.
	 */
	@Overwrite
	public static void pingHud$updatePing(MutableInt currentServerPing) {
		final var minecraft = MinecraftClient.getInstance();
		if (minecraft.getCurrentServerEntry() != null) {
			getRealTimeServerPing(minecraft.getCurrentServerEntry(), currentServerPing);
		} else if (minecraft.isIntegratedServerRunning()) {
			currentServerPing.setValue(1);
		}
	}

	/**
	 * @author Flowey
	 * @reason Implement bridge.
	 */
	@SuppressWarnings("deprecation")
	@Overwrite
	public static AxoSprite.Dynamic ipHud$getServerIcon() throws IOException {
		final var minecraft = MinecraftClient.getInstance();
		final var serverEntry = minecraft.getCurrentServerEntry();
		final NativeImage img;
		if (serverEntry == null) {
			var icon = minecraft.getServer().getServerMetadata().favicon();
			if (icon.isEmpty()) {
				return null;
			} else {
				img = NativeImage.read(icon.get().iconBytes());
			}
		} else {
			img = NativeImage.read(serverEntry.getFavicon());
		}
		final var icon = new NativeImageBackedTexture(img);
		final var iconId = new Identifier(AxolotlClientCommon.MODID,
			serverEntry == null ? "worlds/" + Hashing.sha1().hashUnencodedChars(((MinecraftServerAccessor) minecraft.getServer()).getStorageSource().getDirectoryName()) + "/icon" :
				"servers/" + Hashing.sha1().hashUnencodedChars(minecraft.getCurrentServerEntry().address) + "/icon"
		);

		icon.upload();
		minecraft.getTextureManager().registerTexture(iconId, icon);

		class Impl implements AxoSprite.Dynamic, AxoSpriteImpl {
			@Override
			public void draw(MinecraftClient client, GuiGraphics stack, int sX, int sY, int sW, int sH, int color) {
				client.getTextureManager().bindTexture(iconId);
				stack.setShaderColor(ClientColors.ARGB.redFloat(color), ClientColors.ARGB.greenFloat(color), ClientColors.ARGB.blueFloat(color), ClientColors.ARGB.alphaFloat(color));
				stack.drawTexture(iconId, sX, sY, 0, 0, sW, sH, sW, sH);
				stack.setShaderColor(1, 1, 1, 1);
			}

			@Override
			public void close() {
				minecraft.execute(() -> {
					minecraft.getTextureManager().destroyTexture(iconId);
					icon.close();
				});
			}
		}

		return new Impl();
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge.
	 */
	@Overwrite
	public static void autoBoop$openFiltersScreen(List<String> filters) {
		MinecraftClient.getInstance().setScreen(new FilterListConfigurationScreen(filters, MinecraftClient.getInstance().currentScreen));
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge.
	 */
	@Overwrite
	public static void bedwars$sessionstats$openEntryConfigScreen() {
		MinecraftClient.getInstance().setScreen(new SessionStatsHudEntryConfigScreen(MinecraftClient.getInstance().currentScreen));
	}

	/**
	 * @author moehreag
	 * @reason Implement bridge in preparation for sandboxing
	 */
	@Overwrite
	public static void api$login(UUID profileId, String token, String serverId) throws Exception {
		MinecraftClient.getInstance().getSessionService().joinServer(new GameProfile(profileId, null), token, serverId);
	}
}
