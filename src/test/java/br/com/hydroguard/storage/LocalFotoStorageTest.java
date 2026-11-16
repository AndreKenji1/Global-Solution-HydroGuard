package br.com.hydroguard.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFotoStorageTest {
    @TempDir Path temp;

    @Test void salvarLerExcluirDevePreservarConteudoEChaveOpaca() throws Exception {
        FotoStorage storage = new LocalFotoStorage(temp.resolve("fotos").toString());
        byte[] bytes = new byte[]{1, 2, 3};
        String key = storage.salvar(bytes, "png");
        assertTrue(key.matches("[a-f0-9-]{36}\\.png"));
        assertArrayEquals(bytes, storage.ler(key));
        storage.excluir(key);
        assertThrows(IOException.class, () -> storage.ler(key));
        storage.excluir(key);
    }

    @Test void caminhoDoClienteNuncaDeveSerResolvido() throws Exception {
        FotoStorage storage = new LocalFotoStorage(temp.resolve("fotos").toString());
        assertThrows(IOException.class, () -> storage.ler("../segredo.png"));
        assertThrows(IOException.class, () -> storage.excluir("../segredo.png"));
        assertThrows(IOException.class, () -> storage.salvar(new byte[]{1}, "../html"));
    }

    @Test void symlinkNuncaDeveSerLido() throws Exception {
        Path pasta = temp.resolve("fotos");
        FotoStorage storage = new LocalFotoStorage(pasta.toString());
        Path segredo = temp.resolve("segredo");
        Files.writeString(segredo, "segredo");
        String key = "00000000-0000-0000-0000-000000000000.png";
        symlink(pasta.resolve(key), segredo);
        assertThrows(IOException.class, () -> storage.ler(key));
        storage.excluir(key);
        assertEquals("segredo", Files.readString(segredo));
    }

    @Test void diretorioComoSymlinkDeveSerRejeitado() throws Exception {
        Path real = Files.createDirectory(temp.resolve("real"));
        Path link = temp.resolve("link");
        symlink(link, real);
        assertThrows(IOException.class, () -> new LocalFotoStorage(link.toString()));
    }
    private void symlink(Path link, Path target) throws IOException {
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
            Assumptions.assumeTrue(false, "Sistema não oferece criação de symlink para este usuário");
        }
    }
}
