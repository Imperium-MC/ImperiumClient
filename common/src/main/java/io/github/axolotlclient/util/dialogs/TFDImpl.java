package io.github.axolotlclient.util.dialogs;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class TFDImpl implements DialogProvider {
	@Override
	public CompletableFuture<Optional<Path>> save(String title, Path origin, String filter) {
		return CompletableFuture.supplyAsync(() -> {
			try (var stack = MemoryStack.stackPush()) {
				var pointers = stack.mallocPointer(1);
				pointers.put(stack.UTF8("*"+filter));
				pointers.flip();
				var file = TinyFileDialogs.tinyfd_saveFileDialog(title, origin.toString(), pointers, null);
				return Optional.ofNullable(file).map(Path::of);
			}
		});
	}

	@Override
	public CompletableFuture<Optional<Path>> open(String title, Path origin, String filter) {
		return CompletableFuture.supplyAsync(() -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				var pointers = stack.mallocPointer(1);
				pointers.put(stack.UTF8("*"+filter));
				pointers.flip();
				var file = TinyFileDialogs.tinyfd_openFileDialog(title, origin.toString(), pointers,
					null, false
				);
				return Optional.ofNullable(file).map(Path::of);
			}
		});
	}

	@Override
	public CompletableFuture<Optional<List<Path>>> openMultiple(String title, Path origin, String filter) {
		return CompletableFuture.supplyAsync(() -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				var pointers = stack.mallocPointer(1);
				pointers.put(stack.UTF8("*"+filter));
				pointers.flip();
				var files = TinyFileDialogs.tinyfd_openFileDialog(title, origin.toString(), pointers,
					null, true
				);
				return Optional.ofNullable(files).map(s -> Arrays.stream(s.split("\\|")).map(Path::of).toList());
			}
		});
	}
}
