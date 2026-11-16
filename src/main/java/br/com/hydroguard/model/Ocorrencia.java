package br.com.hydroguard.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ocorrencias")
public class Ocorrencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 36, updatable = false)
    private String protocolo;
    @Version
    @Column(nullable = false)
    private long versao;
    @Column(length = 64)
    private String tokenUploadHash;
    private Instant tokenUploadExpiraEm;
    @Column(nullable = false, length = 1000)
    private String descricao;
    @Column(nullable = false, length = 180)
    private String endereco;
    @Column(length = 90)
    private String bairro;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusOcorrencia status = StatusOcorrencia.ABERTA;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Prioridade prioridade;
    @Column(nullable = false, length = 80)
    private String categoria;
    @Column(nullable = false, length = 32)
    private String tipoAnalise;
    @Column(nullable = false, length = 500)
    private String justificativa;
    @Column(length = 120)
    private String caminhoFoto;
    @Column(length = 30)
    private String tipoFoto;
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;
    @Column(nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    public void prePersist() { criadoEm = Instant.now(); atualizadoEm = criadoEm; }
    @PreUpdate
    public void preUpdate() { atualizadoEm = Instant.now(); }

    public Long getId() { return id; }
    public String getProtocolo() { return protocolo; }
    public void setProtocolo(String protocolo) { this.protocolo = protocolo; }
    public long getVersao() { return versao; }
    public String getTokenUploadHash() { return tokenUploadHash; }
    public void setTokenUploadHash(String tokenUploadHash) { this.tokenUploadHash = tokenUploadHash; }
    public Instant getTokenUploadExpiraEm() { return tokenUploadExpiraEm; }
    public void setTokenUploadExpiraEm(Instant expiraEm) { this.tokenUploadExpiraEm = expiraEm; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public StatusOcorrencia getStatus() { return status; }
    public void setStatus(StatusOcorrencia status) { this.status = status; }
    public Prioridade getPrioridade() { return prioridade; }
    public void setPrioridade(Prioridade prioridade) { this.prioridade = prioridade; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getTipoAnalise() { return tipoAnalise; }
    public void setTipoAnalise(String tipoAnalise) { this.tipoAnalise = tipoAnalise; }
    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }
    public String getCaminhoFoto() { return caminhoFoto; }
    public void setCaminhoFoto(String caminhoFoto) { this.caminhoFoto = caminhoFoto; }
    public String getTipoFoto() { return tipoFoto; }
    public void setTipoFoto(String tipoFoto) { this.tipoFoto = tipoFoto; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
