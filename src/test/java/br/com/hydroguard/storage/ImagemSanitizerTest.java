package br.com.hydroguard.storage;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ImagemSanitizerTest {
    private final ImagemSanitizer sanitizer = new ImagemSanitizer();

    @Test void pngRealDeveSerReencodificadoSemPayloadAnexado() throws Exception {
        byte[] original = imagem("png", 32, 24);
        String payload = "<script>secret-exif-like-payload</script>";
        ByteArrayOutputStream arquivo = new ByteArrayOutputStream();
        arquivo.write(original);
        arquivo.write(payload.getBytes(StandardCharsets.UTF_8));
        var segura = sanitizer.validar(upload("../../foto.jpg", "image/jpeg", arquivo.toByteArray()));
        assertEquals("png", segura.extensao());
        assertEquals("image/png", segura.contentType());
        assertFalse(new String(segura.bytes(), StandardCharsets.ISO_8859_1).contains(payload));
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(segura.bytes()));
        assertEquals(32, lida.getWidth());
        assertEquals(24, lida.getHeight());
    }

    @Test void metadadosTextuaisPrivadosDevemSerRemovidos() throws Exception {
        BufferedImage imagem = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            var metadata = writer.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(imagem), null);
            var root = new IIOMetadataNode("javax_imageio_png_1.0");
            var text = new IIOMetadataNode("tEXt");
            var entry = new IIOMetadataNode("tEXtEntry");
            entry.setAttribute("keyword", "Description");
            entry.setAttribute("value", "private-gps-marker");
            text.appendChild(entry); root.appendChild(text);
            metadata.mergeTree("javax_imageio_png_1.0", root);
            writer.write(null, new IIOImage(imagem, null, metadata), null);
        } finally { writer.dispose(); imagem.flush(); }
        assertTrue(new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1).contains("private-gps-marker"));
        var limpa = sanitizer.validar(upload("foto.png", "image/png", bytes.toByteArray()));
        assertFalse(new String(limpa.bytes(), StandardCharsets.ISO_8859_1).contains("private-gps-marker"));
    }

    @Test void jpegRealDeveSerAceitoSemConfiarNoNome() throws Exception {
        var segura = sanitizer.validar(upload("arquivo.exe", "application/octet-stream", imagem("jpeg", 10, 10)));
        assertEquals("jpg", segura.extensao());
        assertEquals("image/jpeg", segura.contentType());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(segura.bytes())));
    }

    @Test void assinaturaPngSemImagemDeveSerRejeitada() {
        erro(415, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    }

    @Test void assinaturaJpegSemImagemDeveSerRejeitada() {
        erro(415, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 'x'});
    }

    @Test void pngTruncadoDeveSerRejeitado() throws Exception {
        byte[] png = imagem("png", 100, 100);
        erro(415, Arrays.copyOf(png, png.length / 2));
    }

    @Test void svgAtivoDeveSerRejeitadoMesmoComMimeDeImagem() {
        erro(415, "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));
    }

    @Test void webpSemDecoderSeguroDeveSerRejeitado() {
        erro(415, "RIFF1234WEBPpayload".getBytes(StandardCharsets.US_ASCII));
    }

    @Test void gifDeveSerRejeitado() throws Exception {
        erro(415, imagem("gif", 8, 8));
    }

    @Test void arquivoVazioDeveSerRejeitado() {
        erro(400, new byte[0]);
    }

    @Test void arquivoMuitoGrandeDeveSerRejeitadoAntesDeDecodificar() {
        erro(413, new byte[(int) ImagemSanitizer.MAX_BYTES + 1]);
    }

    @Test void larguraExcessivaDeveSerRejeitada() throws Exception {
        erro(422, imagem("png", ImagemSanitizer.MAX_DIMENSAO + 1, 1));
    }

    @Test void QuantidadeDePixelsExcessivaDeveSerRejeitada() throws Exception {
        erro(422, imagem("png", 3500, 3500));
    }

    private void erro(int status, byte[] bytes) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> sanitizer.validar(upload("fake.png", "image/png", bytes)));
        assertEquals(HttpStatus.valueOf(status), ex.getStatusCode());
    }

    private MockMultipartFile upload(String name, String type, byte[] bytes) {
        return new MockMultipartFile("foto", name, type, bytes);
    }

    private byte[] imagem(String formato, int width, int height) throws Exception {
        BufferedImage imagem = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(imagem, formato, output));
        imagem.flush();
        return output.toByteArray();
    }
}
