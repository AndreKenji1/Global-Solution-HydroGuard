package br.com.hydroguard.repository;

import br.com.hydroguard.model.Ocorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long>, JpaSpecificationExecutor<Ocorrencia> {
    Optional<Ocorrencia> findByProtocolo(String protocolo);
    @Query("""
        select count(o) as total,
          coalesce(sum(case when o.status = br.com.hydroguard.model.StatusOcorrencia.ABERTA then 1 else 0 end), 0) as abertas,
          coalesce(sum(case when o.prioridade = br.com.hydroguard.model.Prioridade.ALTA then 1 else 0 end), 0) as prioridadeAlta,
          coalesce(sum(case when o.status = br.com.hydroguard.model.StatusOcorrencia.RESOLVIDA then 1 else 0 end), 0) as resolvidas
        from Ocorrencia o
        """)
    Totais dashboard();
    interface Totais {
        long getTotal();
        long getAbertas();
        long getPrioridadeAlta();
        long getResolvidas();
    }
}
