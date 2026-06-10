package com.aas.mw.controller;

import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.service.AuthenticationService;
import com.aas.mw.service.ErpSessionStore;
import com.aas.mw.service.ItemMarginImportService;
import com.aas.mw.service.MasterDataService;
import com.aas.mw.service.UomService;
import com.aas.mw.service.UserService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterDataControllerTest {

    private final MasterDataService masterDataService = mock(MasterDataService.class);
    private final UomService uomService = mock(UomService.class);
    private final UserService userService = mock(UserService.class);
    private final ItemMarginImportService itemMarginImportService = mock(ItemMarginImportService.class);
    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final MasterDataController controller = new MasterDataController(
            masterDataService,
            uomService,
            userService,
            itemMarginImportService,
            authenticationService);

    @Test
    void itemUpdateUsesPrivilegedErpSessionAfterFeatureAuthorization() {
        FieldsRequest fieldsRequest = new FieldsRequest();
        fieldsRequest.setFields(Map.of("aas_vendor_rate", 42));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ErpSessionStore.REQUEST_ATTR, "sid=helper");
        when(authenticationService.getSetupSessionCookie()).thenReturn("sid=setup");
        when(masterDataService.updateItem("ITEM-1", fieldsRequest)).thenReturn(Map.of("name", "ITEM-1"));

        controller.updateItem("ITEM-1", fieldsRequest, request);

        assertEquals("sid=setup", request.getAttribute(ErpSessionStore.REQUEST_ATTR));
        verify(masterDataService).updateItem("ITEM-1", fieldsRequest);
    }
}
