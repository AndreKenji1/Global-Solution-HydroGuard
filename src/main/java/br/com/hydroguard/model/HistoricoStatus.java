package br.com.hydroguard.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "historico_status")
public class HistoricoStatus {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private Long ocorrenciaId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private StatusOcorrencia status;
    @Column(nullable = false, length = 100, updatable = false)
    private String autor;
    @Column(nullable = false, updatable = false)
    private Instant alteradoEm;

    protected HistoricoStatus() {}
    public HistoricoStatus(Long ocorrenciaId, StatusOcorrencia status, String autor) {
        this.ocorrenciaId = ocorrenciaId;
        this.status = status;
        this.autor = autor;
        this.alteradoEm = Instant.now();
    }
    public Long getId() { return id; }
    public Long getOcorrenciaId() { return ocorrenciaId; }
    public StatusOcorrencia getStatus() { return status; }
    public String getAutor() { return autor; }
    public Instant getAlteradoEm() { return alteradoEm; }
}
