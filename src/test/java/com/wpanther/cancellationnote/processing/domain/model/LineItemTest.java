package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class LineItemTest {

    private static final Money UNIT_PRICE = Money.of(new BigDecimal("100.00"), "THB");

    @Test
    void constructorWithValidFields() {
        LineItem item = new LineItem("Widget", 2, UNIT_PRICE, new BigDecimal("7.00"));
        assertEquals("Widget", item.description());
        assertEquals(2, item.quantity());
        assertEquals(UNIT_PRICE, item.unitPrice());
        assertEquals(0, new BigDecimal("7.00").compareTo(item.taxRate()));
    }

    @Test
    void constructorWithNullDescriptionThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new LineItem(null, 1, UNIT_PRICE, BigDecimal.ZERO));
        assertEquals("Line item description cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithBlankDescriptionThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new LineItem("  ", 1, UNIT_PRICE, BigDecimal.ZERO));
        assertEquals("Line item description cannot be blank", ex.getMessage());
    }

    @Test
    void constructorWithNullUnitPriceThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new LineItem("Widget", 1, null, BigDecimal.ZERO));
        assertEquals("Unit price cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithNullTaxRateThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new LineItem("Widget", 1, UNIT_PRICE, null));
        assertEquals("Tax rate cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithZeroQuantityThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new LineItem("Widget", 0, UNIT_PRICE, BigDecimal.ZERO));
        assertEquals("Quantity must be positive", ex.getMessage());
    }

    @Test
    void constructorWithNegativeQuantityThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new LineItem("Widget", -1, UNIT_PRICE, BigDecimal.ZERO));
        assertEquals("Quantity must be positive", ex.getMessage());
    }

    @Test
    void constructorWithNegativeTaxRateThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new LineItem("Widget", 1, UNIT_PRICE, new BigDecimal("-1.00")));
        assertEquals("Tax rate cannot be negative", ex.getMessage());
    }

    @Test
    void allowsZeroTaxRate() {
        LineItem item = new LineItem("Widget", 1, UNIT_PRICE, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(item.taxRate()));
    }

    @Test
    void getLineTotalReturnsQuantityTimesUnitPrice() {
        LineItem item = new LineItem("Widget", 3, UNIT_PRICE, BigDecimal.ZERO);
        Money total = item.getLineTotal();
        assertEquals(new BigDecimal("300.00"), total.amount());
    }

    @Test
    void getTaxAmountReturnsLineTotalTimesTaxRateDividedByHundred() {
        LineItem item = new LineItem("Widget", 2, UNIT_PRICE, new BigDecimal("7.00"));
        Money tax = item.getTaxAmount();
        // (200.00 * 7) / 100 = 14.00
        assertEquals(new BigDecimal("14.00"), tax.amount());
    }

    @Test
    void getTotalWithTaxReturnsLineTotalPlusTaxAmount() {
        LineItem item = new LineItem("Widget", 2, UNIT_PRICE, new BigDecimal("7.00"));
        Money totalWithTax = item.getTotalWithTax();
        // 200.00 + 14.00 = 214.00
        assertEquals(new BigDecimal("214.00"), totalWithTax.amount());
    }

    @Test
    void getTaxAmountWithZeroTaxRateReturnsZero() {
        LineItem item = new LineItem("Widget", 5, UNIT_PRICE, BigDecimal.ZERO);
        Money tax = item.getTaxAmount();
        assertEquals(0, BigDecimal.ZERO.compareTo(tax.amount()));
    }

    @Test
    void recordEquality() {
        LineItem a = new LineItem("Widget", 2, UNIT_PRICE, new BigDecimal("7.00"));
        LineItem b = new LineItem("Widget", 2, UNIT_PRICE, new BigDecimal("7.00"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordInequality() {
        LineItem a = new LineItem("Widget", 2, UNIT_PRICE, new BigDecimal("7.00"));
        LineItem b = new LineItem("Gadget", 2, UNIT_PRICE, new BigDecimal("7.00"));
        assertNotEquals(a, b);
    }
}
