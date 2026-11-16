package br.com.hydroguard.controller;

import br.com.hydroguard.dto.*;
import br.com.hydroguard.model.Prioridade;
import br.com.hydroguard.model.StatusOcorrencia;
import br.com.hydroguard.service.OcorrenciaService;
import br.com.hydroguard.storage.ImagemSanitizer;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OcorrenciaController {
    private final OcorrenciaService service;
    private final ImagemSanitizer imagens;
    public OcorrenciaController(OcorrenciaService service, ImagemSanitizer imagens) {
        this.service=service;this.imagens=imagens;
    }
    @GetMapping("/info") public Map<String,String> info() {
        return Map.of("app","HydroGuard Citizen","version","1.0.0","status","online");
    }
    @PostMapping("/ocorrencias") public ResponseEntity<OcorrenciaResponse> criar(@Valid @RequestBody CriarOcorrenciaRequest request) {
        var r=service.criar(request);
        return ResponseEntity.created(URI.create("/api/ocorrencias/protocolo/"+r.protocolo()))
            .cacheControl(CacheControl.noStore()).body(r);
    }
    @GetMapping({"/ocorrencias","/admin/ocorrencias"}) public PaginaResponse<OcorrenciaResponse> listar(
        @RequestParam(required=false) StatusOcorrencia status,
        @RequestParam(required=false) Prioridade prioridade,
        @RequestParam(required=false) String q,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        if(page<0 || size<1 || size>100 || q!=null && q.length()>180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginação ou filtro inválido (tamanho de 1 a 100)");
        return PaginaResponse.from(service.listar(status,prioridade,q,PageRequest.of(page,size)));
    }
    @GetMapping("/ocorrencias/{id}") public OcorrenciaResponse buscar(@PathVariable Long id) { return service.buscar(id); }
    @GetMapping("/ocorrencias/protocolo/{protocolo}") public AcompanhamentoResponse protocolo(@PathVariable String protocolo) {
        return service.buscarProtocolo(protocolo);
    }
    @GetMapping("/dashboard") public DashboardResponse dashboard() { return service.dashboard(); }
    @PatchMapping("/ocorrencias/{id}/status") public OcorrenciaResponse atualizarStatus(@PathVariable Long id,
        @Valid @RequestBody AtualizarStatusRequest request, Authentication auth) {
        return service.mudarStatus(id,request.status(),auth.getName());
    }
    @PostMapping(value="/ocorrencias/{id}/foto",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcorrenciaResponse> enviarFoto(@PathVariable Long id,
        @RequestParam("foto") MultipartFile foto,
        @RequestHeader(value="X-Upload-Token",required=false) String token, Authentication auth) throws IOException {
        boolean admin=auth!=null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        service.validarAcessoUpload(id,token,admin);
        var imagem=imagens.validar(foto);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(service.salvarFoto(id,imagem.bytes(),imagem.extensao(),imagem.contentType(),token,admin));
    }
    @GetMapping("/ocorrencias/{id}/foto") public ResponseEntity<byte[]> obterFoto(@PathVariable Long id) throws IOException {
        var foto=service.recuperarFoto(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(foto.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"ocorrencia."+(foto.contentType().equals("image/png")?"png":"jpg")+"\"")
            .cacheControl(CacheControl.noStore()).body(foto.bytes());
    }
}
