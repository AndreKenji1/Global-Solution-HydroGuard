package br.com.hydroguard.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.channels.Channels;
import java.nio.file.OpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

@Service
@ConditionalOnProperty(name = "hydroguard.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFotoStorage implements FotoStorage {
    private final Path diretorio;

    public LocalFotoStorage(@Value("${hydroguard.upload-dir:./uploads}") String uploadDir) throws IOException {
        diretorio = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(diretorio);
        if (Files.isSymbolicLink(diretorio)) throw new IOException("Diretório de imagens inválido");
        if (Files.getFileStore(diretorio).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(diretorio, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Override
    public String salvar(byte[] bytes, String extensao) throws IOException {
        String nome = FotoKey.criar(extensao);
        Path destino = diretorio.resolve(nome);
        boolean criado = false;
        FileAttribute<?>[] atributos = Files.getFileStore(diretorio).supportsFileAttributeView("posix")
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                : new FileAttribute<?>[0];
        Set<OpenOption> opcoes = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try (var channel = Files.newByteChannel(destino, opcoes, atributos)) {
            criado = true;
            try (var output = Channels.newOutputStream(channel)) { output.write(bytes); }
        } catch (IOException ex) {
            if (criado) Files.deleteIfExists(destino);
            throw ex;
        }
        return nome;
    }

    @Override
    public byte[] ler(String nome) throws IOException {
        Path origem = diretorio.resolve(FotoKey.validar(nome));
        try (InputStream input = Files.newInputStream(origem, LinkOption.NOFOLLOW_LINKS)) {
            return input.readAllBytes();
        }
    }

    @Override
    public void excluir(String nome) throws IOException {
        if (nome != null) Files.deleteIfExists(diretorio.resolve(FotoKey.validar(nome)));
    }
}
