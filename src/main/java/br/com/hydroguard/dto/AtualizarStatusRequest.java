package br.com.hydroguard.dto;
import br.com.hydroguard.model.StatusOcorrencia;
import jakarta.validation.constraints.NotNull;
public record AtualizarStatusRequest(@NotNull StatusOcorrencia status) {}
