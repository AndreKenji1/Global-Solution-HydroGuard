package br.com.hydroguard;

import br.com.hydroguard.model.Prioridade;
import br.com.hydroguard.service.TriagemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriagemServiceTest {
    private final TriagemService service = new TriagemService("", "gemini-2.5-flash", new ObjectMapper());

    @Test void rompimentoDeCanoDeveTerPrioridadeAlta() {
        var res = service.classificar("Cano rompido na rua com muita água jorrando");
        assertEquals(Prioridade.ALTA, res.prioridade());
        assertEquals("REGRAS", res.tipoAnalise());
    }
    @Test void gotejamentoDeveTerPrioridadeBaixa() {
        assertEquals(Prioridade.BAIXA, service.classificar("Torneira pingando devagar").prioridade());
    }
    @Test void relatoComumDeveTerPrioridadeMedia() {
        assertEquals(Prioridade.MEDIA, service.classificar("Vazamento na calçada").prioridade());
    }
}
