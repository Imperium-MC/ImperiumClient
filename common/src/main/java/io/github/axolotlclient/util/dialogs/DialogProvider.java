package io.github.axolotlclient.util.dialogs;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.util.CommonUtil;

public sealed interface DialogProvider permits DummyImpl, SDLImpl, TFDImpl {

	DialogProvider IMPL = CommonUtil.make(() -> {
		try {
			Class.forName("org.lwjgl.sdl.SDL");
			return new SDLImpl();
		} catch (Exception ignored) {
		}
		try {
			Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
			return new TFDImpl();
		} catch (Exception ignored) {
		}
		AxolotlClientCommon.getInstance().getLogger().warn("Neither SDL nor TFD were found. Dialogs are unavailable.");
		return new DummyImpl();
	});

	CompletableFuture<Optional<Path>> save(String title, Path origin, String filter);

	CompletableFuture<Optional<Path>> open(String title, Path origin, String filter);

	CompletableFuture<Optional<List<Path>>> openMultiple(String title, Path origin, String filter);
}
