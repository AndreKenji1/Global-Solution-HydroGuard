package br.com.hydroguard.repository;
import br.com.hydroguard.model.HistoricoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatus, Long> {
    List<HistoricoStatus> findByOcorrenciaIdOrderByAlteradoEmAscIdAsc(Long ocorrenciaId);
}
