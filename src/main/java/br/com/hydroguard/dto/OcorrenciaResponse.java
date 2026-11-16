package br.com.hydroguard.dto;

import br.com.hydroguard.model.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record OcorrenciaResponse(
    Long id, String protocolo, String descricao, String endereco, String bairro,
    StatusOcorrencia status, Prioridade prioridade, String categoria,
    String tipoAnalise, String justificativa, boolean possuiFoto,
    Instant criadoEm, Instant atualizadoEm, List<HistoricoStatusResponse> historico,
    @JsonInclude(JsonInclude.Include.NON_NULL) String tokenUpload
) {
    public static OcorrenciaResponse from(Ocorrencia o) {
        return from(o, List.of(), null);
    }
    public static OcorrenciaResponse from(Ocorrencia o, List<HistoricoStatusResponse> historico, String tokenUpload) {
        return new OcorrenciaResponse(o.getId(), o.getProtocolo(), o.getDescricao(), o.getEndereco(),
            o.getBairro(), o.getStatus(), o.getPrioridade(), o.getCategoria(), o.getTipoAnalise(),
            o.getJustificativa(), o.getCaminhoFoto() != null, o.getCriadoEm(), o.getAtualizadoEm(),
            List.copyOf(historico), tokenUpload);
    }
}
