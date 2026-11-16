package br.com.hydroguard.service;

import br.com.hydroguard.dto.*;
import br.com.hydroguard.model.*;
import br.com.hydroguard.repository.OcorrenciaRepository;
import br.com.hydroguard.repository.HistoricoStatusRepository;
import br.com.hydroguard.storage.FotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class OcorrenciaService {
    private static final Logger LOG = LoggerFactory.getLogger(OcorrenciaService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final OcorrenciaRepository repository;
    private final HistoricoStatusRepository historico;
    private final TriagemService triagem;
    private final FotoStorage fotos;
    private final TransactionTemplate escrita;

    public OcorrenciaService(OcorrenciaRepository repository, HistoricoStatusRepository historico,
                             TriagemService triagem, FotoStorage fotos, PlatformTransactionManager transactions) {
        this.repository = repository;
        this.historico = historico;
        this.triagem = triagem;
        this.fotos = fotos;
        this.escrita = new TransactionTemplate(transactions);
    }

    public OcorrenciaResponse criar(CriarOcorrenciaRequest request) {
        // The external call completes before opening a write transaction/borrowing a DB connection.
        TriagemService.Resultado resultado = triagem.classificar(request.descricao());
        byte[] segredo = new byte[32];
        RANDOM.nextBytes(segredo);
        String tokenUpload = Base64.getUrlEncoder().withoutPadding().encodeToString(segredo);
        return Objects.requireNonNull(escrita.execute(transaction -> {
            Ocorrencia o = new Ocorrencia();
            o.setProtocolo(UUID.randomUUID().toString());
            o.setDescricao(request.descricao());
            o.setEndereco(request.endereco());
            o.setBairro(request.bairro());
            o.setPrioridade(resultado.prioridade());
            o.setCategoria(resultado.categoria());
            o.setTipoAnalise(resultado.tipoAnalise());
            o.setJustificativa(resultado.justificativa());
            o.setTokenUploadHash(hash(tokenUpload));
            o.setTokenUploadExpiraEm(Instant.now().plus(Duration.ofMinutes(30)));
            repository.saveAndFlush(o);
            HistoricoStatus inicio = historico.saveAndFlush(new HistoricoStatus(o.getId(), o.getStatus(), "CIDADAO"));
            return OcorrenciaResponse.from(o, List.of(HistoricoStatusResponse.from(inicio)), tokenUpload);
        }));
    }

    @Transactional(readOnly = true)
    public Page<OcorrenciaResponse> listar(StatusOcorrencia status, Prioridade prioridade, String q, Pageable pagina) {
        if (pagina.isUnpaged() || pagina.getPageSize() > 100 || pagina.getPageSize() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A página deve conter de 1 a 100 registros");
        if (q != null && q.length() > 180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Busca deve ter até 180 caracteres");
        Specification<Ocorrencia> filtro = (root, query, cb) -> cb.conjunction();
        if (status != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (prioridade != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("prioridade"), prioridade));
        if (q != null && !q.isBlank()) {
            String busca = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
            filtro = filtro.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("endereco")), busca, '\\'),
                cb.like(cb.lower(root.get("bairro")), busca, '\\'),
                cb.like(cb.lower(root.get("protocolo")), busca, '\\')));
        }
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(),
            Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id")));
        return repository.findAll(filtro, ordenada).map(OcorrenciaResponse::from);
    }

    @Transactional(readOnly = true)
    public OcorrenciaResponse buscar(Long id) {
        Ocorrencia o = obter(id);
        return OcorrenciaResponse.from(o, historicoDoRegistro(id), null);
    }

    @Transactional(readOnly = true)
    public AcompanhamentoResponse buscarProtocolo(String protocolo) {
        if (protocolo == null || !protocolo.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Protocolo inválido");
        Ocorrencia o = repository.findByProtocolo(protocolo.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocorrência não encontrada"));
        return AcompanhamentoResponse.from(o, historico.findByOcorrenciaIdOrderByAlteradoEmAscIdAsc(o.getId()));
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        OcorrenciaRepository.Totais totais = repository.dashboard();
        return new DashboardResponse(totais.getTotal(), totais.getAbertas(), totais.getPrioridadeAlta(), totais.getResolvidas());
    }

    @Transactional
    public OcorrenciaResponse mudarStatus(Long id, StatusOcorrencia status, String autor) {
        if (status == null || autor == null || autor.isBlank() || autor.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status e autor válidos são obrigatórios");
        Ocorrencia o = obter(id);
        if (o.getStatus() != status) {
            o.setStatus(status);
            repository.saveAndFlush(o);
            historico.saveAndFlush(new HistoricoStatus(id, status, autor));
        }
        return OcorrenciaResponse.from(o, historicoDoRegistro(id), null);
    }

    @Transactional(readOnly = true)
    public void validarAcessoUpload(Long id, String tokenUpload, boolean admin) {
        Ocorrencia o = obter(id);
        if (!admin) exigirTokenUpload(o, tokenUpload);
    }

    @Transactional(rollbackFor = IOException.class)
    public OcorrenciaResponse salvarFoto(Long id, byte[] imagem, String extensao, String tipo,
                                         String tokenUpload, boolean admin) throws IOException {
        Ocorrencia o = obter(id);
        if (!admin) exigirTokenUpload(o, tokenUpload);
        String caminhoAntigo = o.getCaminhoFoto();
        String novoCaminho = fotos.salvar(imagem, extensao);
        boolean sincronizado = TransactionSynchronizationManager.isSynchronizationActive();
        if (sincronizado) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { excluirSemInterromper(caminhoAntigo); }
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) excluirSemInterromper(novoCaminho);
                }
            });
        }
        try {
            o.setCaminhoFoto(novoCaminho);
            o.setTipoFoto(tipo);
            o.setTokenUploadHash(null);
            o.setTokenUploadExpiraEm(null);
            OcorrenciaResponse result = OcorrenciaResponse.from(repository.saveAndFlush(o));
            if (!sincronizado) excluirSemInterromper(caminhoAntigo);
            return result;
        } catch (RuntimeException e) {
            if (!sincronizado) excluirSemInterromper(novoCaminho);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public FotoRecuperada recuperarFoto(Long id) throws IOException {
        Ocorrencia o = obter(id);
        if (o.getCaminhoFoto() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não encontrada");
        return new FotoRecuperada(fotos.ler(o.getCaminhoFoto()), o.getTipoFoto());
    }
    public record FotoRecuperada(byte[] bytes, String contentType) {}

    private void exigirTokenUpload(Ocorrencia o, String token) {
        if (token == null || token.length() > 128 || o.getTokenUploadHash() == null ||
            o.getTokenUploadExpiraEm() == null || !o.getTokenUploadExpiraEm().isAfter(Instant.now()) ||
            !MessageDigest.isEqual(o.getTokenUploadHash().getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Autorização de fotografia inválida ou expirada");
    }

    private List<HistoricoStatusResponse> historicoDoRegistro(Long id) {
        return historico.findByOcorrenciaIdOrderByAlteradoEmAscIdAsc(id).stream().map(HistoricoStatusResponse::from).toList();
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private void excluirSemInterromper(String nome) {
        if (nome == null) return;
        try { fotos.excluir(nome); }
        catch (IOException | RuntimeException e) {
            LOG.warn("Fotografia obsoleta não removida; verificar armazenamento e limpeza operacional");
        }
    }

    private Ocorrencia obter(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocorrência não encontrada"));
    }
}
