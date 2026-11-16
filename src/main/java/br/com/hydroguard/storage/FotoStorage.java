package br.com.hydroguard.storage;

import java.io.IOException;

/** Stores only sanitized images; keys never expose the underlying filesystem or cloud URL. */
public interface FotoStorage {
    String salvar(byte[] bytes, String extensao) throws IOException;
    byte[] ler(String nome) throws IOException;
    void excluir(String nome) throws IOException;
}
