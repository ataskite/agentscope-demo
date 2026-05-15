package com.skloda.agentscope.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceDataTest {

    @Test
    void constructor_createsEmptyInvoiceData() {
        InvoiceData invoice = new InvoiceData();

        assertNotNull(invoice);
        assertNull(invoice.invoiceNumber);
        assertNull(invoice.invoiceDate);
        assertNull(invoice.invoiceType);
        assertNull(invoice.totalAmount);
        assertNull(invoice.taxAmount);
        assertNull(invoice.currency);
        assertNull(invoice.sellerName);
        assertNull(invoice.sellerTaxId);
        assertNull(invoice.buyerName);
        assertNull(invoice.buyerTaxId);
        assertNull(invoice.items);
        assertNull(invoice.remarks);
    }

    @Test
    void fieldsCanBeSet() {
        InvoiceData invoice = new InvoiceData();
        InvoiceData.InvoiceItem item = new InvoiceData.InvoiceItem();

        invoice.invoiceNumber = "INV001";
        invoice.invoiceDate = "2026-05-12";
        invoice.invoiceType = "增值税专用发票";
        invoice.totalAmount = 1000.0;
        invoice.taxAmount = 130.0;
        invoice.currency = "CNY";
        invoice.sellerName = "销售方公司";
        invoice.sellerTaxId = "123456789";
        invoice.buyerName = "购买方公司";
        invoice.buyerTaxId = "987654321";
        invoice.items = List.of(item);
        invoice.remarks = "测试备注";

        assertEquals("INV001", invoice.invoiceNumber);
        assertEquals("2026-05-12", invoice.invoiceDate);
        assertEquals("增值税专用发票", invoice.invoiceType);
        assertEquals(1000.0, invoice.totalAmount);
        assertEquals(130.0, invoice.taxAmount);
        assertEquals("CNY", invoice.currency);
        assertEquals("销售方公司", invoice.sellerName);
        assertEquals("123456789", invoice.sellerTaxId);
        assertEquals("购买方公司", invoice.buyerName);
        assertEquals("987654321", invoice.buyerTaxId);
        assertEquals(1, invoice.items.size());
        assertEquals("测试备注", invoice.remarks);
    }

    @Test
    void invoiceItemConstructor_createsEmptyItem() {
        InvoiceData.InvoiceItem item = new InvoiceData.InvoiceItem();

        assertNotNull(item);
        assertNull(item.name);
        assertNull(item.specification);
        assertNull(item.unit);
        assertNull(item.quantity);
        assertNull(item.unitPrice);
        assertNull(item.amount);
        assertNull(item.taxRate);
        assertNull(item.tax);
    }

    @Test
    void invoiceItemFieldsCanBeSet() {
        InvoiceData.InvoiceItem item = new InvoiceData.InvoiceItem();

        item.name = "商品A";
        item.specification = "标准规格";
        item.unit = "件";
        item.quantity = 10.0;
        item.unitPrice = 100.0;
        item.amount = 1000.0;
        item.taxRate = 0.13;
        item.tax = 130.0;

        assertEquals("商品A", item.name);
        assertEquals("标准规格", item.specification);
        assertEquals("件", item.unit);
        assertEquals(10.0, item.quantity);
        assertEquals(100.0, item.unitPrice);
        assertEquals(1000.0, item.amount);
        assertEquals(0.13, item.taxRate);
        assertEquals(130.0, item.tax);
    }
}
