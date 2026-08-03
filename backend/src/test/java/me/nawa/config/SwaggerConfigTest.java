package me.nawa.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import springfox.documentation.spring.web.plugins.Docket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        ServletConfig.class,
        SwaggerConfig.class,
        SwaggerConfigTest.TestController.class
})
class SwaggerConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void swaggerConfig_registersDocket() {
        assertNotNull(context.getBean(Docket.class));
    }

    @Test
    void webConfig_registersSwaggerConfig() {
        assertArrayEquals(
                new Class<?>[]{ServletConfig.class, SwaggerConfig.class},
                new WebConfig().getServletConfigClasses());
    }

    @Test
    void apiDocs_exposesOnlyApiPaths() throws Exception {
        String responseBody = mockMvc.perform(get("/v2/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode apiDocs = new ObjectMapper().readTree(responseBody);

        assertEquals("2.0", apiDocs.path("swagger").asText());
        assertTrue(apiDocs.path("paths").has("/api/swagger-test"));
        assertFalse(apiDocs.path("paths").has("/internal/swagger-test"));
    }

    @Test
    void swaggerResources_returnsResourceList() throws Exception {
        mockMvc.perform(get("/swagger-resources"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void swaggerUi_returnsHtml() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void swaggerUiWebJar_returnsJavascript() throws Exception {
        byte[] responseBody = mockMvc.perform(
                        get("/webjars/springfox-swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(responseBody.length > 0);
    }

    @RestController
    static class TestController {

        @GetMapping("/api/swagger-test")
        String apiPath() {
            return "api";
        }

        @GetMapping("/internal/swagger-test")
        String internalPath() {
            return "internal";
        }
    }
}
