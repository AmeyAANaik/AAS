package com.aas.mw.controller;

import com.aas.mw.service.OpeningBalanceImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OpeningBalancesController.class)
@AutoConfigureMockMvc(addFilters = false)
class OpeningBalancesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpeningBalanceImportService openingBalanceImportService;

    @Test
    void template_returnsCsvAttachment() throws Exception {
        when(openingBalanceImportService.templateCsv("ACME"))
                .thenReturn("record_type,account\nACCOUNT,Cash\n");

        mockMvc.perform(get("/api/companies/{id}/opening-balances/template", "ACME"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"opening-balances-template.csv\""))
                .andExpect(content().string("record_type,account\nACCOUNT,Cash\n"));
    }
}
