package br.com.hydroguard.dto;

import br.com.hydroguard.model.*;
import java.time.Instant;
import java.util.List;

/** Protocol tracking deliberately excludes exact location, source text and audit authors. */
public record AcompanhamentoResponse(
    String protocolo, String bairro, StatusOcorrencia status, Prioridade prioridade,
    String categoria, String tipoAnalise, String justificativa, boolean possuiFoto,
    Instant criadoEm, Instant atualizadoEm, List<Etapa> historico
) {
    public record Etapa(StatusOcorrencia status, Instant alteradoEm) {}
    public static AcompanhamentoResponse from(Ocorrencia o, List<HistoricoStatus> historico) {
        return new AcompanhamentoResponse(o.getProtocolo(), o.getBairro(), o.getStatus(), o.getPrioridade(),
            o.getCategoria(), o.getTipoAnalise(), o.getJustificativa(), o.getCaminhoFoto() != null,
            o.getCriadoEm(), o.getAtualizadoEm(), historico.stream()
                .map(h -> new Etapa(h.getStatus(), h.getAlteradoEm())).toList());
    }
}
