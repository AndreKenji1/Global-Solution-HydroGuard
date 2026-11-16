package br.com.hydroguard;

import br.com.hydroguard.dto.*;
import br.com.hydroguard.model.*;
import br.com.hydroguard.repository.*;
import br.com.hydroguard.service.*;
import br.com.hydroguard.storage.FotoStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcorrenciaServiceTest {
    @Mock OcorrenciaRepository repository;
    @Mock HistoricoStatusRepository historico;
    @Mock TriagemService triagem;
    @Mock FotoStorage fotos;
    @Mock PlatformTransactionManager transactions;
    private OcorrenciaService service;

    @BeforeEach void configurar() {
        service = new OcorrenciaService(repository, historico, triagem, fotos, transactions);
    }

    @AfterEach void limparSincronizacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void criacaoGeraProtocoloESegredoEfemeroSemPersistirSegredoEmClaro() {
        var entrada = new CriarOcorrenciaRequest("  Cano rompido na via pública  ", "  Rua Verde, 20  ", "  Centro  ");
        when(triagem.classificar("Cano rompido na via pública"))
            .thenReturn(new TriagemService.Resultado(Prioridade.ALTA, "Vazamento intenso", "REGRAS", "Fluxo intenso"));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            Ocorrencia o = invocation.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 7L);
            o.prePersist();
            return o;
        });
        when(historico.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OcorrenciaResponse criado = service.criar(entrada);

        assertDoesNotThrow(() -> UUID.fromString(criado.protocolo()));
        assertEquals(43, criado.tokenUpload().length());
        assertEquals("Rua Verde, 20", criado.endereco());
        assertEquals("Centro", criado.bairro());
        assertEquals(StatusOcorrencia.ABERTA, criado.status());
        assertEquals(1, criado.historico().size());
        ArgumentCaptor<Ocorrencia> salvo = ArgumentCaptor.forClass(Ocorrencia.class);
        verify(repository).saveAndFlush(salvo.capture());
        assertNotEquals(criado.tokenUpload(), salvo.getValue().getTokenUploadHash());
        assertEquals(64, salvo.getValue().getTokenUploadHash().length());
        assertTrue(salvo.getValue().getTokenUploadExpiraEm().isAfter(Instant.now().plusSeconds(1750)));
        InOrder ordem = inOrder(triagem, transactions, repository);
        ordem.verify(triagem).classificar(anyString());
        ordem.verify(transactions).getTransaction(any());
        ordem.verify(repository).saveAndFlush(any());
        verify(transactions).commit(any());
    }

    @Test void alteracaoDeStatusRegistraAutor() {
        Ocorrencia o = existente();
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        when(historico.findByOcorrenciaIdOrderByAlteradoEmAscIdAsc(7L)).thenReturn(List.of());

        OcorrenciaResponse atualizado = service.mudarStatus(7L, StatusOcorrencia.EM_ANALISE, "gestor");

        assertEquals(StatusOcorrencia.EM_ANALISE, atualizado.status());
        ArgumentCaptor<HistoricoStatus> evento = ArgumentCaptor.forClass(HistoricoStatus.class);
        verify(historico).saveAndFlush(evento.capture());
        assertEquals("gestor", evento.getValue().getAutor());
        assertEquals(7L, evento.getValue().getOcorrenciaId());
        assertNull(atualizado.tokenUpload());
    }

    @Test void repetirStatusNaoCriaEventoDuplicado() {
        when(repository.findById(7L)).thenReturn(Optional.of(existente()));
        when(historico.findByOcorrenciaIdOrderByAlteradoEmAscIdAsc(7L)).thenReturn(List.of());
        service.mudarStatus(7L, StatusOcorrencia.ABERTA, "gestor");
        verify(repository, never()).saveAndFlush(any());
        verify(historico, never()).saveAndFlush(any());
    }

    @Test void uploadSemAutorizacaoNaoGravaArquivo() {
        when(repository.findById(7L)).thenReturn(Optional.of(existente()));
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
            () -> service.salvarFoto(7L, new byte[]{1}, "png", "image/png", null, false));
        assertEquals(403, erro.getStatusCode().value());
        verifyNoInteractions(fotos);
    }

    @Test void prevalidacaoDeUploadRejeitaSemTokenAntesDeArmazenar() {
        when(repository.findById(7L)).thenReturn(Optional.of(existente()));
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> service.validarAcessoUpload(7L, null, false)).getStatusCode().value());
        verifyNoInteractions(fotos);
    }

    @Test void uploadComTokenErradoNaoGravaArquivo() throws Exception {
        Ocorrencia o = comToken("segredo-correto");
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        assertThrows(ResponseStatusException.class,
            () -> service.salvarFoto(7L, new byte[]{1}, "png", "image/png", "outro-segredo", false));
        verifyNoInteractions(fotos);
    }

    @Test void uploadComTokenExpiradoNaoGravaArquivo() throws Exception {
        Ocorrencia o = comToken("segredo-correto");
        o.setTokenUploadExpiraEm(Instant.now().minusSeconds(1));
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        assertThrows(ResponseStatusException.class,
            () -> service.salvarFoto(7L, new byte[]{1}, "png", "image/png", "segredo-correto", false));
        verifyNoInteractions(fotos);
    }

    @Test void uploadAutorizadoConsomeTokenEImpedeReuso() throws Exception {
        Ocorrencia o = comToken("segredo-correto");
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        when(fotos.salvar(any(), eq("png"))).thenReturn("nova.png");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OcorrenciaResponse resposta = service.salvarFoto(7L, new byte[]{1}, "png", "image/png", "segredo-correto", false);
        assertTrue(resposta.possuiFoto());
        assertNull(o.getTokenUploadHash());
        assertNull(o.getTokenUploadExpiraEm());
        assertNull(resposta.tokenUpload());
        assertThrows(ResponseStatusException.class,
            () -> service.salvarFoto(7L, new byte[]{1}, "png", "image/png", "segredo-correto", false));
        verify(fotos, times(1)).salvar(any(), anyString());
    }

    @Test void administradorPodeSubstituirFotoSemToken() throws Exception {
        Ocorrencia o = existente();
        o.setCaminhoFoto("antiga.png");
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        when(fotos.salvar(any(), anyString())).thenReturn("nova.png");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.salvarFoto(7L, new byte[]{1}, "png", "image/png", null, true);
        verify(fotos, never()).excluir(anyString());
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
            s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(fotos).excluir("antiga.png");
        verify(fotos, never()).excluir("nova.png");
    }

    @Test void rollbackRemoveNovaFotoEPreservaAnterior() throws Exception {
        Ocorrencia o = existente();
        o.setCaminhoFoto("antiga.png");
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        when(fotos.salvar(any(), anyString())).thenReturn("nova.png");
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("falha DB"));
        TransactionSynchronizationManager.initSynchronization();
        assertThrows(IllegalStateException.class,
            () -> service.salvarFoto(7L, new byte[]{1}, "png", "image/png", null, true));
        verify(fotos, never()).excluir(anyString());
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations())
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fotos).excluir("nova.png");
        verify(fotos, never()).excluir("antiga.png");
    }

    @Test void falhaAoArmazenarPreservaTokenEReferenciaDaFoto() throws Exception {
        Ocorrencia o = comToken("segredo-correto");
        o.setCaminhoFoto("antiga.png");
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        when(fotos.salvar(any(), anyString())).thenThrow(new IOException("indisponível"));
        assertThrows(IOException.class,
            () -> service.salvarFoto(7L, new byte[]{1}, "png", "image/png", "segredo-correto", false));
        assertEquals("antiga.png", o.getCaminhoFoto());
        assertNotNull(o.getTokenUploadHash());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void consultaInexistenteRetorna404() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.buscar(7L)).getStatusCode().value());
    }

    @Test void protocoloMalformadoNaoConsultaBanco() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> service.buscarProtocolo("123")).getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test void paginaExcessivaNaoConsultaBanco() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> service.listar(null, null, null, PageRequest.of(0, 101))).getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test void dashboardUsaAgregadosDoRepositorio() {
        OcorrenciaRepository.Totais totais = mock(OcorrenciaRepository.Totais.class);
        when(repository.dashboard()).thenReturn(totais);
        when(totais.getTotal()).thenReturn(23L);
        when(totais.getAbertas()).thenReturn(10L);
        when(totais.getPrioridadeAlta()).thenReturn(4L);
        when(totais.getResolvidas()).thenReturn(8L);
        assertEquals(new DashboardResponse(23, 10, 4, 8), service.dashboard());
    }

    private Ocorrencia existente() {
        Ocorrencia o = new Ocorrencia();
        ReflectionTestUtils.setField(o, "id", 7L);
        o.setProtocolo(UUID.randomUUID().toString());
        o.setDescricao("Cano rompido na via pública");
        o.setEndereco("Rua Verde, 20");
        o.setBairro("Centro");
        o.setPrioridade(Prioridade.ALTA);
        o.setCategoria("Vazamento intenso");
        o.setTipoAnalise("REGRAS");
        o.setJustificativa("Fluxo intenso");
        o.prePersist();
        return o;
    }

    private Ocorrencia comToken(String token) throws Exception {
        Ocorrencia o = existente();
        o.setTokenUploadHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(token.getBytes(StandardCharsets.UTF_8))));
        o.setTokenUploadExpiraEm(Instant.now().plusSeconds(120));
        return o;
    }
}
