package br.com.hydroguard.dto;
import br.com.hydroguard.model.HistoricoStatus;
import br.com.hydroguard.model.StatusOcorrencia;
import java.time.Instant;
public record HistoricoStatusResponse(StatusOcorrencia status, Instant alteradoEm, String autor) {
    public static HistoricoStatusResponse from(HistoricoStatus h) {
        return new HistoricoStatusResponse(h.getStatus(), h.getAlteradoEm(), h.getAutor());
    }
}
