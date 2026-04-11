package com.aas.mw.service;

import java.util.Map;

public interface InvoiceChannelAdapter {

    String channel();

    boolean isConfigured();

    String configurationHint();

    Map<String, Object> send(InvoiceDeliveryContext context);
}
