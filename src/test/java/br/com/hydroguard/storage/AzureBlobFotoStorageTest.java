package br.com.hydroguard.storage;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AzureBlobFotoStorageTest {
    private static final String KEY = "00000000-0000-0000-0000-000000000000.png";

    @Test void uploadDownloadDeleteDevemUsarSomenteChavesPrivadas() throws Exception {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        when(container.getBlobClient(anyString())).thenReturn(blob);
        when(blob.downloadContent()).thenReturn(BinaryData.fromBytes(new byte[]{1, 2}));
        FotoStorage storage = new AzureBlobFotoStorage(container);
        String key = storage.salvar(new byte[]{1, 2}, "png");
        assertTrue(key.matches("[a-f0-9-]{36}\\.png"));
        ArgumentCaptor<BlobParallelUploadOptions> options = ArgumentCaptor.forClass(BlobParallelUploadOptions.class);
        verify(blob).uploadWithResponse(options.capture(), eq(Duration.ofSeconds(20)), eq(Context.NONE));
        assertEquals("image/png", options.getValue().getHeaders().getContentType());
        assertEquals("no-store", options.getValue().getHeaders().getCacheControl());
        assertArrayEquals(new byte[]{1, 2}, storage.ler(key));
        storage.excluir(key);
        verify(blob).deleteIfExists();
        verify(blob, never()).getBlobUrl();
    }

    @Test void nomeInvalidoNaoDeveChamarAzure() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        FotoStorage storage = new AzureBlobFotoStorage(container);
        assertThrows(IOException.class, () -> storage.ler("../foto.png"));
        assertThrows(IOException.class, () -> storage.excluir("https://example.invalid/foto.png"));
        verifyNoInteractions(container);
    }

    @Test void falhaSdkNaoDeveExporSegredosEmExcecao() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        when(container.getBlobClient(KEY)).thenThrow(new IllegalStateException("sig=SEGREDO"));
        IOException ex = assertThrows(IOException.class, () -> new AzureBlobFotoStorage(container).ler(KEY));
        assertEquals("Não foi possível acessar o armazenamento de fotos", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test void managedIdentityExigeEndpointHttpsSemSas() {
        assertThrows(IllegalArgumentException.class,
                () -> new AzureBlobFotoStorage("http://example.invalid", "fotos", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new AzureBlobFotoStorage("https://example.invalid?sig=secreto", "fotos", ""));
    }
}
