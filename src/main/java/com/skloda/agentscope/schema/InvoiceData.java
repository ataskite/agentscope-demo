package com.skloda.agentscope.schema;

import java.util.List;

/**
 * Structured output schema for invoice information extraction.
 */
public class InvoiceData {

    public String invoiceNumber;
    public String invoiceDate;
    public String invoiceType;
    public Double totalAmount;
    public Double taxAmount;
    public String currency;
    public String sellerName;
    public String sellerTaxId;
    public String buyerName;
    public String buyerTaxId;
    public List<InvoiceItem> items;
    public String remarks;

    public InvoiceData() {}

    public static class InvoiceItem {
        public String name;
        public String specification;
        public String unit;
        public Double quantity;
        public Double unitPrice;
        public Double amount;
        public Double taxRate;
        public Double tax;

        public InvoiceItem() {}
    }
}
