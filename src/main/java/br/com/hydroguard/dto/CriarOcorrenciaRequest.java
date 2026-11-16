package br.com.hydroguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriarOcorrenciaRequest(
    @NotBlank @Size(min=12, max=1000)
    @Pattern(regexp="[\\P{Cc}\\t\\n\\r]*", message="não deve conter caracteres de controle") String descricao,
    @NotBlank @Size(min=5, max=180)
    @Pattern(regexp="\\P{Cc}*", message="não deve conter caracteres de controle") String endereco,
    @Size(max=90)
    @Pattern(regexp="\\P{Cc}*", message="não deve conter caracteres de controle") String bairro
) {
    public CriarOcorrenciaRequest {
        descricao = descricao == null ? null : descricao.strip();
        endereco = endereco == null ? null : endereco.strip();
        bairro = bairro == null ? "" : bairro.strip();
    }
}
