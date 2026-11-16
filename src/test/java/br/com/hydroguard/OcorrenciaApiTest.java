package br.com.hydroguard;

import br.com.hydroguard.repository.OcorrenciaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OcorrenciaRepository repository;
    private String valid() {
        return "{\"descricao\":\"Cano rompido com água jorrando na via pública\",\"endereco\":\"Rua das Flores, 120\",\"bairro\":\"Centro\"}";
    }
    private JsonNode create() throws Exception {
        var result=mvc.perform(post("/api/ocorrencias").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid()))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
    private MockMultipartFile image() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",out);
        return new MockMultipartFile("foto","evidencia.png","image/png",out.toByteArray());
    }
    @Test void createsAndPersistsWithProtocolAndRules() throws Exception {
        var r=create();
        assertEquals("ABERTA",r.path("status").asText());assertEquals("ALTA",r.path("prioridade").asText());
        assertEquals("REGRAS",r.path("tipoAnalise").asText());
        assertTrue(r.path("protocolo").asText().matches("[a-f0-9-]{36}"));
        assertFalse(r.path("tokenUpload").asText().isBlank());
        assertTrue(repository.findById(r.path("id").asLong()).isPresent());
    }
    @Test void publicProtocolOmitsPersonalDataAndSecrets() throws Exception {
        var r=create();
        mvc.perform(get("/api/ocorrencias/protocolo/"+r.path("protocolo").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("ABERTA")))
            .andExpect(jsonPath("$.endereco").doesNotExist()).andExpect(jsonPath("$.descricao").doesNotExist())
            .andExpect(jsonPath("$.id").doesNotExist()).andExpect(jsonPath("$.tokenUpload").doesNotExist())
            .andExpect(jsonPath("$.historico").isArray());
    }
    @Test void rejectsInvalidInputAndUnknownFields() throws Exception {
        long before=repository.count();
        for(String body:new String[]{"{}","{\"descricao\":\"   \"}","{\"descricao\":null}",valid().replace("Centro","Centro\",\"status\":\"RESOLVIDA")})
            mvc.perform(post("/api/ocorrencias").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        assertEquals(before,repository.count());
    }
    @ParameterizedTest
    @ValueSource(strings={"descricao","endereco","bairro"})
    void rejectsControlCharactersBeforePersistence(String field) throws Exception {
        long before=repository.count();
        for(char control:new char[]{0,1,27,127,133}) {
            var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(valid());
            body.put(field,body.path(field).asText()+control+"teste");
            mvc.perform(post("/api/ocorrencias").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors."+field).isString());
        }
        assertEquals(before,repository.count());
    }
    @Test void multilineDescriptionAllowsOrdinaryWhitespace() throws Exception {
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(valid());
        body.put("descricao","Cano rompido na rua.\n\tÁgua jorrando.\r\nRequer análise.");
        mvc.perform(post("/api/ocorrencias").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(body))).andExpect(status().isCreated())
            .andExpect(jsonPath("$.descricao",is(body.path("descricao").asText())));
    }
    @Test void trimsBeforeValidation() throws Exception {
        mvc.perform(post("/api/ocorrencias").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"descricao\":\"           x           \",\"endereco\":\"Rua Flores\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void missingCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON).content(valid()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status",is(403)));
    }
    @Test void publicCannotListOrEnumerate() throws Exception {
        var r=create();
        mvc.perform(get("/api/ocorrencias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/ocorrencias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ocorrencias/"+r.path("id").asLong())).andExpect(status().isUnauthorized());
    }
    @Test void filtersAndPaginationUseDatabase() throws Exception {
        var r=create();
        mvc.perform(get("/api/admin/ocorrencias").with(user("analista").roles("ANALISTA"))
            .param("status","ABERTA").param("prioridade","ALTA").param("q",r.path("protocolo").asText()).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements",is(1)))
            .andExpect(jsonPath("$.content[0].protocolo",is(r.path("protocolo").asText())))
            .andExpect(jsonPath("$.content[0].tokenUpload").doesNotExist());
        mvc.perform(get("/api/admin/ocorrencias").with(user("admin").roles("ADMIN")).param("prioridade","BAIXA"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements",is(0)));
    }
    @Test void invalidFiltersAndJsonHaveStandardErrors() throws Exception {
        for(String param:new String[]{"status","prioridade","page","size"})
            mvc.perform(get("/api/admin/ocorrencias").with(user("admin").roles("ADMIN")).param(param,"invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.timestamp").isString());
        mvc.perform(get("/api/admin/ocorrencias").with(user("admin").roles("ADMIN")).param("size","101"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ocorrencias").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
    }
    @Test void adminUpdatesStatusAndAuditsActor() throws Exception {
        var r=create();long id=r.path("id").asLong();
        mvc.perform(patch("/api/ocorrencias/"+id+"/status").with(csrf()).with(user("gestor").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_ANALISE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status",is("EM_ANALISE")));
        mvc.perform(get("/api/ocorrencias/protocolo/"+r.path("protocolo").asText()))
            .andExpect(jsonPath("$.status",is("EM_ANALISE"))).andExpect(jsonPath("$.historico[*].autor").doesNotExist());
        mvc.perform(get("/api/ocorrencias/"+id).with(user("gestor").roles("ADMIN")))
            .andExpect(jsonPath("$.historico[*].autor",hasItem("gestor")));
    }
    @Test void anonymousAndAnalystCannotChangeStatus() throws Exception {
        long id=create().path("id").asLong();
        mvc.perform(patch("/api/ocorrencias/"+id+"/status").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDA\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/ocorrencias/"+id+"/status").with(csrf()).with(user("analista").roles("ANALISTA"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDA\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/ocorrencias/"+id).with(user("analista").roles("ANALISTA")))
            .andExpect(jsonPath("$.status",is("ABERTA")));
    }
    @Test void nullAndUnknownStatusAreRejected() throws Exception {
        long id=create().path("id").asLong();
        for(String body:new String[]{"{}","{\"status\":null}","{\"status\":\"INVALID\"}"})
            mvc.perform(patch("/api/ocorrencias/"+id+"/status").with(csrf()).with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    @Test void distinguishesMalformedProtocolFromMissingRecord() throws Exception {
        mvc.perform(get("/api/ocorrencias/protocolo/not-a-protocol")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ocorrencias/protocolo/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
        mvc.perform(get("/api/ocorrencias/999999").with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
    }
    @Test void dashboardReflectsPersistedStatus() throws Exception {
        var before=json.readTree(mvc.perform(get("/api/dashboard")).andReturn().getResponse().getContentAsByteArray());
        var r=create();
        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.total",is(before.path("total").asInt()+1)))
            .andExpect(jsonPath("$.abertas",is(before.path("abertas").asInt()+1)))
            .andExpect(jsonPath("$.prioridadeAlta",is(before.path("prioridadeAlta").asInt()+1)));
        mvc.perform(patch("/api/ocorrencias/"+r.path("id").asLong()+"/status").with(csrf()).with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDA\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.resolvidas",is(before.path("resolvidas").asInt()+1)));
    }
    @Test void validUploadRequiresSingleUseOwnerTokenAndPhotoIsPrivate() throws Exception {
        var r=create();long id=r.path("id").asLong();String token=r.path("tokenUpload").asText();
        mvc.perform(multipart("/api/ocorrencias/"+id+"/foto").file(image()).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/ocorrencias/"+id+"/foto").file(image()).with(csrf()).header("X-Upload-Token",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.possuiFoto",is(true))).andExpect(jsonPath("$.tokenUpload").doesNotExist());
        mvc.perform(multipart("/api/ocorrencias/"+id+"/foto").file(image()).with(csrf()).header("X-Upload-Token",token))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/ocorrencias/"+id+"/foto")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ocorrencias/"+id+"/foto").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
            .andExpect(header().string("Cache-Control",containsString("no-store")));
    }
    @Test void forgedEmptyOversizeAndMissingPhotosAreRejected() throws Exception {
        var r=create();long id=r.path("id").asLong();String token=r.path("tokenUpload").asText();
        mvc.perform(multipart("/api/ocorrencias/"+id+"/foto").with(csrf())).andExpect(status().isBadRequest());
        for(var file:new MockMultipartFile[]{new MockMultipartFile("foto","fake.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,1,2}),
            new MockMultipartFile("foto","fake.svg","image/svg+xml","<svg/>".getBytes())})
            mvc.perform(multipart("/api/ocorrencias/"+id+"/foto").file(file).with(csrf()).header("X-Upload-Token",token))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/ocorrencias/"+id+"/foto").file(new MockMultipartFile("foto","empty.png","image/png",new byte[0]))
            .with(csrf()).header("X-Upload-Token",token)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/ocorrencias/"+id+"/foto").file(new MockMultipartFile("foto","huge.png","image/png",new byte[4*1024*1024+1]))
            .with(csrf()).header("X-Upload-Token",token)).andExpect(status().isPayloadTooLarge());
    }
    @Test void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy",containsString("frame-ancestors 'none'")))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Referrer-Policy","no-referrer"));
    }
}
