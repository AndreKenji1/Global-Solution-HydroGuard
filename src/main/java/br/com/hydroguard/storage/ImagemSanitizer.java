package br.com.hydroguard.storage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/** Validates decoded content and strips EXIF, text chunks and appended payloads by re-encoding. */
@Service
public class ImagemSanitizer {
    public static final long MAX_BYTES = 4L * 1024 * 1024;
    public static final int MAX_DIMENSAO = 4096;
    public static final long MAX_PIXELS = 12_000_000L;

    public record ImagemSegura(byte[] bytes, String extensao, String contentType) {}

    public ImagemSegura validar(MultipartFile foto) throws IOException {
        if (foto == null || foto.isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Envie uma fotografia");
        if (foto.getSize() > MAX_BYTES) throw erro(HttpStatus.PAYLOAD_TOO_LARGE, "A foto deve ter até 4 MB");
        byte[] original = foto.getBytes();
        if (original.length > MAX_BYTES) throw erro(HttpStatus.PAYLOAD_TOO_LARGE, "A foto deve ter até 4 MB");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw formatoInvalido();
            ImageReader reader = readers.next();
            try {
                String formato = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!formato.equals("jpeg") && !formato.equals("png")) throw formatoInvalido();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSAO || height > MAX_DIMENSAO
                        || (long) width * height > MAX_PIXELS) {
                    throw erro(HttpStatus.UNPROCESSABLE_ENTITY, "A foto deve ter até 4096 pixels por lado e 12 megapixels");
                }
                reader.addIIOReadWarningListener((source, warning) -> { throw formatoInvalido(); });
                BufferedImage imagem = reader.read(0);
                if (imagem == null) throw formatoInvalido();
                // A new raster prevents propagation of source metadata or unexpected color profiles.
                BufferedImage limpa = new BufferedImage(width, height,
                        formato.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
                var graphics = limpa.createGraphics();
                try { graphics.drawImage(imagem, 0, 0, null); }
                finally { graphics.dispose(); imagem.flush(); }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(limpa, formato, output)) throw formatoInvalido();
                limpa.flush();
                if (output.size() > MAX_BYTES) {
                    throw erro(HttpStatus.PAYLOAD_TOO_LARGE, "A imagem processada excede 4 MB; reduza sua resolução");
                }
                return new ImagemSegura(output.toByteArray(), formato.equals("jpeg") ? "jpg" : "png",
                        formato.equals("jpeg") ? "image/jpeg" : "image/png");
            } finally { reader.dispose(); }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw formatoInvalido();
        }
    }

    private static ResponseStatusException formatoInvalido() {
        return erro(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use uma imagem JPEG ou PNG válida");
    }

    private static ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
