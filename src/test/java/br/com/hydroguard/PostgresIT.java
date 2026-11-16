package br.com.hydroguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Failsafe only: requires a disposable PostgreSQL, never a production database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres-it")
@Transactional
class PostgresIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Test void nullCharactersAreRejectedBeforePostgresWrite() throws Exception {
        long before=jdbc.queryForObject("select count(*) from ocorrencias",Long.class);
        for(String field:new String[]{"descricao","endereco","bairro"}) {
            var body=new java.util.HashMap<String,String>(Map.of(
                "descricao","Cano rompido com água jorrando na via pública",
                "endereco","Rua Teste PostgreSQL, 12","bairro","Centro"));
            body.put(field,body.get(field)+(char)0+"teste");
            mvc.perform(post("/api/ocorrencias").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors."+field).isString());
        }
        assertEquals(before,jdbc.queryForObject("select count(*) from ocorrencias",Long.class));
    }
    @Test void postgresMigrationPersistenceFiltersAndAudit() throws Exception {
        try(var connection=jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getDatabaseProductName().contains("PostgreSQL"));
        }
        var result=mvc.perform(post("/api/ocorrencias").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"descricao\":\"Cano rompido com água jorrando na via pública\",\"endereco\":\"Rua Teste PostgreSQL, 12\",\"bairro\":\"Centro\"}"))
            .andExpect(status().isCreated()).andReturn();
        var row=json.readTree(result.getResponse().getContentAsByteArray());long id=row.path("id").asLong();
        assertEquals(1,jdbc.queryForObject("select count(*) from ocorrencias where id=?",Integer.class,id));
        mvc.perform(patch("/api/ocorrencias/"+id+"/status").with(csrf()).with(user("postgres-admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_ANALISE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/ocorrencias/protocolo/"+row.path("protocolo").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_ANALISE"));
        mvc.perform(get("/api/admin/ocorrencias").with(user("postgres-admin").roles("ADMIN"))
            .param("status","EM_ANALISE").param("prioridade","ALTA").param("q",row.path("protocolo").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertEquals("EM_ANALISE",jdbc.queryForObject("select status from ocorrencias where id=?",String.class,id));
    }
}
