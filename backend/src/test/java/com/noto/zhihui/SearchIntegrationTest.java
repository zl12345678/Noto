package com.noto.zhihui;

import com.fasterxml.jackson.databind.JsonNode;
import com.noto.zhihui.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchIntegrationTest extends AbstractIntegrationTest {

    @Test
    void searchReturnsOffsetForContentMatch() throws Exception {
        String token = registerAndGetToken();
        long workspaceId = fetchWorkspaceId(token);

        Map<String, Object> noteBody = Map.of(
                "title", "搜索测试文档",
                "content", "段落开头唯一关键词XYZ用于定位\n",
                "workspaceId", workspaceId
        );
        mockMvc.perform(post("/api/v1/notes")
                        .header("Authorization", authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult searchResult = mockMvc.perform(get("/api/v1/search")
                        .header("Authorization", authHeader(token))
                        .param("keyword", "唯一关键词XYZ")
                        .param("workspaceId", String.valueOf(workspaceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].offsetStart").exists())
                .andReturn();

        JsonNode record = objectMapper.readTree(searchResult.getResponse().getContentAsString())
                .path("data").path("records").get(0);
        assertNotNull(record.path("offsetStart").asText());
    }

    private long fetchWorkspaceId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode idNode = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").get(0).path("id");
        return idNode.isNumber() ? idNode.asLong() : Long.parseLong(idNode.asText());
    }
}
