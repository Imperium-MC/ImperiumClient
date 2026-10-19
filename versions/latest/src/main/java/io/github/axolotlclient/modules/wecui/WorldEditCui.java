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

package io.github.axolotlclient.modules.wecui;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption;
import io.github.axolotlclient.modules.AbstractModule;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * WorldEdit CUI (Alpine parity: {@code worldedit_cui}) — renders the server-side WorldEdit
 * selection in-world: the cuboid wireframe with the classic per-block grid on the three
 * faces touching the minimum corner, or the ellipsoid with its bounding box and principal
 * circles. Corner markers use their own colours so pos1/pos2 stay tellable apart.
 *
 * <h2>Wire protocol</h2>
 *
 * Speaks the public WorldEdit CUI protocol on the {@code worldedit:cui} plugin channel
 * (EPL-2.0; reference implementations: EngineHub's WorldEdit server events and the
 * WorldEditCUI client). Bukkit plugin messaging carries <em>bare UTF-8 text lines</em>,
 * {@code |}-separated, no length prefix and no NBT framing — so the payload codec is a
 * raw-UTF-8 stream codec (the same lesson as {@code ImperiumIntegration.RAW_UTF8};
 * a {@code fromCodec(Codec.STRING)} decoder reads NBT and fails on bare bytes).
 *
 * <p>Client → server, once per session, announces CUI support so WorldEdit starts
 * dispatching selection updates: {@code v|4}.
 *
 * <p>Server → client, event id first, {@code +} prefix marks multi-selection events
 * (we render the last-announced region):
 * <ul>
 *   <li>{@code s|<shape>} — new selection; known shapes {@code cuboid}, {@code ellipsoid},
 *       {@code polygon2d}, {@code cylinder}; unknown shapes clear the selection.</li>
 *   <li>{@code p|<id>|<x>|<y>|<z>|[area]} — 3D point (id 0 = pos1, 1 = pos2).</li>
 *   <li>{@code p2|<id>|<x>|<z>|[area]} — 2D outline point (polygon); the legacy
 *       3-arg form {@code p2|blockX|blockY|blockZ} sets the second cuboid corner.</li>
 *   <li>{@code e|<id>|<x>|<y>|<z>} — ellipsoid center (id 0) / radii (id 1).</li>
 *   <li>{@code mm|<minY>|<maxY>} — polygon y bounds.</li>
 *   <li>{@code u}, {@code col}, {@code grid} — ignored (update ticks / colour hints).</li>
 * </ul>
 *
 * <p>Legacy one-shot cuboid line accepted for older setups:
 * {@code p|world|minX|minY|minZ|maxX|maxY|maxZ|cuboid}. See docs/wecui-server.md for the
 * exact bytes a server (including ImperiumMC's own region tools) should send.
 *
 * <h2>Rendering</h2>
 *
 * Hooked into Fabric's {@code LevelRenderEvents.BEFORE_GIZMOS} (fired inside
 * {@code LevelRenderer#submitFeatures} right before {@code finalizeGizmoCollection}), where
 * we open the frame's gizmo collector window and submit {@link net.minecraft.gizmos.Gizmos}
 * line/cuboid gizmos in world coordinates. Vanilla's gizmo feature renderer draws them with
 * the game's line pipelines — translucent (alpha &lt; 255) lines are depth-tested but never
 * occlude, matching the classic client-side CUI mods. No mixin of our own is needed.
 *
 * <h2>Server-side adoption (docs/wecui-server.md)</h2>
 *
 * The channel is two-way and server-driven, so the ImperiumMC plugin can visualize its own
 * regions (mine regions, plot regions, event areas) the same way: register the outgoing
 * plugin channel, wait for the client's {@code v|4} announcement, then send
 * {@code s|cuboid} + two {@code p} events per region. Details in docs/wecui-server.md.
 */
public class WorldEditCui extends AbstractModule {

	private static final WorldEditCui Instance = new WorldEditCui();

	/** Protocol version we announce; 4 is what current WorldEdit/WorldEditCUI speak. */
	private static final String PROTOCOL_VERSION = "4";

	private static final CustomPacketPayload.Type<ClientboundCuiPayload> CLIENTBOUND =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("worldedit", "cui"));
	private static final CustomPacketPayload.Type<ServerboundCuiPayload> SERVERBOUND =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("worldedit", "cui"));

	// Classic WorldEditCUI palette: bronze-red grid, green pos1, blue pos2; line_opacity
	// (default 0.8) lands them at the classic ~80% translucency.
	public final BooleanOption enabled = new BooleanOption("enabled", true, value -> {
		if (value) {
			sendHandshakeIfNeeded();
		}
	});
	public final ColorOption gridColor = new ColorOption("grid_color", new Color(0xFFCC4C4C));
	public final ColorOption minCornerColor = new ColorOption("min_corner_color", new Color(0xFF33CC33));
	public final ColorOption maxCornerColor = new ColorOption("max_corner_color", new Color(0xFF3333CC));
	public final FloatOption lineOpacity = new FloatOption("line_opacity", 0.8F, 0.1F, 1.0F);
	public final BooleanOption showGrid = new BooleanOption("show_grid", true);
	public final FloatOption lineWidth = new FloatOption("line_width", 2.0F, 0.5F, 8.0F);

	private final OptionCategory category = OptionCategory.create("worldeditcui");

	/** The handshake is sent at most once per server connection (WorldEdit re-dispatches on re-send). */
	private boolean handshakeSent;
	/** Dimension of the last frame's render; a change means the selection is stale. */
	private String lastDimension;

	public static WorldEditCui getInstance() {
		return Instance;
	}

	private WorldEditCui() {
	}

	@Override
	public void init() {
		category.add(enabled, gridColor, minCornerColor, maxCornerColor, lineOpacity, showGrid, lineWidth);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);

		registerChannel();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			handshakeSent = false;
			WESelectionHolder.getInstance().clear();
			if (enabled.get()) {
				sendHandshake();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			handshakeSent = false;
			WESelectionHolder.getInstance().clear();
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			if (!enabled.get()) {
				return;
			}
			Minecraft client = Minecraft.getInstance();
			if (client.level == null) {
				return;
			}
			// World change within a server (portal): the old world's selection is stale; the
			// server re-announces when a new selection is made there.
			String dimension = client.level.dimension().identifier().toString();
			if (!dimension.equals(lastDimension)) {
				lastDimension = dimension;
				WESelectionHolder.getInstance().clear();
			}
			WESelectionHolder selection = WESelectionHolder.getInstance();
			if (!selection.hasSelection()) {
				return;
			}
			WESelectionRenderer.Palette palette = new WESelectionRenderer.Palette(
				withOpacity(gridColor.get()), withOpacity(minCornerColor.get()),
				withOpacity(maxCornerColor.get()));
			List<WESelectionRenderer.Segment> segments =
				WESelectionRenderer.buildSegments(selection, palette, showGrid.get());
			if (segments.isEmpty()) {
				return;
			}
			// The frame's gizmo collection window: everything added inside is drained by
			// LevelRenderer.finalizeGizmoCollection a moment later.
			try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
				WESelectionRenderer.submit(segments, lineWidth.get());
			}
		});
	}

	/**
	 * Channel registration follows the FeatureDisabler shape: {@code PayloadTypeRegistry} for
	 * both directions plus a global receiver, so Bukkit plugin messages on {@code worldedit:cui}
	 * decode instead of being dropped as an unknown payload. The codec is bare UTF-8 (see
	 * class docs) — WorldEdit writes raw text, no NBT framing.
	 */
	private void registerChannel() {
		PayloadTypeRegistry.clientboundPlay().register(CLIENTBOUND, ClientboundCuiPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SERVERBOUND, ServerboundCuiPayload.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(CLIENTBOUND, (payload, ctx) -> ctx.client().execute(() -> {
			String message = payload.message();
			// "type|a|b|c..." — a leading '+' marks a multi-selection event.
			boolean multi = message.startsWith("+");
			if (multi) {
				message = message.substring(1);
			}
			String[] split = message.split("\\|", -1);
			String[] args = new String[Math.max(0, split.length - 1)];
			System.arraycopy(split, 1, args, 0, args.length);
			WESelectionHolder.getInstance().handle(multi, split[0], Arrays.asList(args));
		}));
	}

	private void sendHandshakeIfNeeded() {
		if (Minecraft.getInstance().getConnection() != null && !handshakeSent) {
			sendHandshake();
		}
	}

	private void sendHandshake() {
		if (handshakeSent) {
			return;
		}
		handshakeSent = true;
		ClientPlayNetworking.send(new ServerboundCuiPayload("v|" + PROTOCOL_VERSION));
	}

	private int withOpacity(Color color) {
		return color.withAlpha((int) (color.getAlpha() * lineOpacity.get())).toInt();
	}

	/** Bare-UTF-8 codec: decode consumes the whole remaining buffer; encode writes raw bytes. */
	static final StreamCodec<ByteBuf, String> RAW_UTF8 = new StreamCodec<>() {
		@Override
		public String decode(ByteBuf buf) {
			var bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			return new String(bytes, StandardCharsets.UTF_8);
		}

		@Override
		public void encode(ByteBuf buf, String value) {
			buf.writeBytes(value.getBytes(StandardCharsets.UTF_8));
		}
	};

	@NullMarked
	public record ClientboundCuiPayload(String message) implements CustomPacketPayload {
		public static final StreamCodec<ByteBuf, ClientboundCuiPayload> CODEC =
			RAW_UTF8.map(ClientboundCuiPayload::new, ClientboundCuiPayload::message);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return CLIENTBOUND;
		}
	}

	@NullMarked
	public record ServerboundCuiPayload(String message) implements CustomPacketPayload {
		public static final StreamCodec<ByteBuf, ServerboundCuiPayload> CODEC =
			RAW_UTF8.map(ServerboundCuiPayload::new, ServerboundCuiPayload::message);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return SERVERBOUND;
		}
	}
}
