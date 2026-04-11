FROM frappe/erpnext:v15.94.3

ARG AAS_TWILIO_INTEGRATION_REPO=

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends wkhtmltopdf \
    && rm -rf /var/lib/apt/lists/*

USER frappe

RUN if [ -n "$AAS_TWILIO_INTEGRATION_REPO" ]; then \
      cd /home/frappe/frappe-bench && bench get-app "$AAS_TWILIO_INTEGRATION_REPO"; \
    fi
