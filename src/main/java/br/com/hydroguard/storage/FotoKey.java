package br.com.hydroguard.storage;

import java.io.IOException;
import java.util.UUID;

final class FotoKey {
    private FotoKey() {}

    static String criar(String extensao) throws IOException {
        if (!"jpg".equals(extensao) && !"png".equals(extensao)) {
            throw new IOException("Formato de armazenamento inválido");
        }
        return UUID.randomUUID() + "." + extensao;
    }

    static String validar(String nome) throws IOException {
        if (nome == null || !nome.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|png)")) {
            throw new IOException("Identificador de imagem inválido");
        }
        return nome;
    }
}
