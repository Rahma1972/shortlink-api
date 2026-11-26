package com.example.shortlink;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
@SpringBootTest
@AutoConfigureMockMvc
class UrlControllerTest {
    @Autowired
    MockMvc mvc;
    @Test
    void shortenRedirectAndStats() throws Exception {
        String body = mvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");
        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));

        mvc.perform(get("/stats/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clicks").value(1));
    }
    @Test
    void rejectsNonHttpUrl() throws Exception {
        mvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test
    void unknownCodeReturns404() throws Exception {
        mvc.perform(get("/nope123")).andExpect(status().isNotFound());
    }

    @Test
    void customAliasThenConflict() throws Exception {
        String json = "{\"url\":\"https://example.com\",\"alias\":\"demo1\"}";
        mvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("demo1"));
        mvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRemovesLink() throws Exception {
        mvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"alias\":\"todelete\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/todelete")).andExpect(status().isNoContent());
        mvc.perform(get("/todelete")).andExpect(status().isNotFound());
    }

    @Test
    void listReturnsOk() throws Exception {
        mvc.perform(get("/links")).andExpect(status().isOk());
    }
}