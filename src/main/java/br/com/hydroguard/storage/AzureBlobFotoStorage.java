package br.com.hydroguard.storage;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/** Private Blob container adapter. Access stays behind the authorized API; no public URLs or SAS are returned. */
@Service
@ConditionalOnProperty(name = "hydroguard.storage.type", havingValue = "azure")
public class AzureBlobFotoStorage implements FotoStorage {
    private final BlobContainerClient container;

    @Autowired
    public AzureBlobFotoStorage(
            @Value("${hydroguard.storage.azure.endpoint:}") String endpoint,
            @Value("${hydroguard.storage.azure.container:hydroguard-fotos}") String containerName,
            @Value("${hydroguard.storage.azure.connection-string:}") String connectionString) {
        if (!containerName.matches("[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])") || containerName.contains("--")) {
            throw new IllegalArgumentException("Nome do container de fotos inválido");
        }
        BlobContainerClientBuilder builder = new BlobContainerClientBuilder().containerName(containerName);
        if (connectionString != null && !connectionString.isBlank()) {
            builder.connectionString(connectionString);
        } else {
            URI uri = URI.create(endpoint);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Configure o endpoint HTTPS do Blob Storage");
            }
            builder.endpoint(endpoint).credential(new DefaultAzureCredentialBuilder().build());
        }
        container = builder.buildClient();
    }

    AzureBlobFotoStorage(BlobContainerClient container) {
        this.container = container;
    }

    @Override
    public String salvar(byte[] bytes, String extensao) throws IOException {
        String nome = FotoKey.criar(extensao);
        try {
            // Provision the container privately and disable account anonymous blob access in Azure.
            var options = new BlobParallelUploadOptions(BinaryData.fromBytes(bytes))
                    .setHeaders(new BlobHttpHeaders().setContentType(extensao.equals("jpg") ? "image/jpeg" : "image/png")
                            .setCacheControl("no-store"));
            container.getBlobClient(nome).uploadWithResponse(options, Duration.ofSeconds(20), Context.NONE);
            return nome;
        } catch (RuntimeException ex) {
            throw falha();
        }
    }

    @Override
    public byte[] ler(String nome) throws IOException {
        FotoKey.validar(nome);
        try { return container.getBlobClient(nome).downloadContent().toBytes(); }
        catch (RuntimeException ex) { throw falha(); }
    }

    @Override
    public void excluir(String nome) throws IOException {
        if (nome == null) return;
        FotoKey.validar(nome);
        try { container.getBlobClient(nome).deleteIfExists(); }
        catch (RuntimeException ex) { throw falha(); }
    }

    private static IOException falha() {
        // SDK errors may contain SAS or endpoint details. Expose only a stable storage error.
        return new IOException("Não foi possível acessar o armazenamento de fotos");
    }
}
