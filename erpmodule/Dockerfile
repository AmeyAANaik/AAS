FROM frappe/erpnext:v15.94.3

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends wkhtmltopdf \
    && rm -rf /var/lib/apt/lists/*

USER frappe
