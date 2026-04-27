package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void constructorWithValidAmountAndCurrency() {
        Money money = new Money(new BigDecimal("100.00"), "THB");
        assertEquals(new BigDecimal("100.00"), money.amount());
        assertEquals("THB", money.currency());
    }

    @Test
    void constructorScalesAmountToTwoDecimals() {
        Money money = new Money(new BigDecimal("100.567"), "THB");
        assertEquals(new BigDecimal("100.57"), money.amount());
    }

    @Test
    void constructorWithNullAmountThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new Money(null, "THB"));
        assertEquals("Amount cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithNullCurrencyThrowsException() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new Money(BigDecimal.ZERO, null));
        assertEquals("Currency cannot be null", ex.getMessage());
    }

    @Test
    void constructorWithInvalidCurrencyLengthThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Money(BigDecimal.ZERO, "US"));
        assertEquals("Currency must be 3-letter ISO code", ex.getMessage());
    }

    @Test
    void factoryMethodOfBigDecimal() {
        Money money = Money.of(new BigDecimal("250.50"), "THB");
        assertEquals(new BigDecimal("250.50"), money.amount());
        assertEquals("THB", money.currency());
    }

    @Test
    void factoryMethodOfDouble() {
        Money money = Money.of(99.9, "THB");
        assertEquals(new BigDecimal("99.90"), money.amount());
    }

    @Test
    void zeroFactoryMethod() {
        Money zero = Money.zero("USD");
        assertEquals(0, BigDecimal.ZERO.compareTo(zero.amount()));
        assertEquals("USD", zero.currency());
    }

    @Test
    void addSameCurrency() {
        Money a = Money.of(new BigDecimal("100.00"), "THB");
        Money b = Money.of(new BigDecimal("50.00"), "THB");
        Money result = a.add(b);
        assertEquals(new BigDecimal("150.00"), result.amount());
        assertEquals("THB", result.currency());
    }

    @Test
    void addDifferentCurrencyThrowsException() {
        Money thb = Money.of(BigDecimal.TEN, "THB");
        Money usd = Money.of(BigDecimal.TEN, "USD");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> thb.add(usd));
        assertTrue(ex.getMessage().contains("Currency mismatch"));
    }

    @Test
    void subtractSameCurrency() {
        Money a = Money.of(new BigDecimal("100.00"), "THB");
        Money b = Money.of(new BigDecimal("30.00"), "THB");
        Money result = a.subtract(b);
        assertEquals(new BigDecimal("70.00"), result.amount());
    }

    @Test
    void multiplyByBigDecimal() {
        Money money = Money.of(new BigDecimal("100.00"), "THB");
        Money result = money.multiply(new BigDecimal("0.07"));
        assertEquals(new BigDecimal("7.00"), result.amount());
    }

    @Test
    void multiplyByDouble() {
        Money money = Money.of(new BigDecimal("100.00"), "THB");
        Money result = money.multiply(1.1);
        assertEquals(new BigDecimal("110.00"), result.amount());
    }

    @Test
    void multiplyWithNullFactorThrowsException() {
        Money money = Money.of(BigDecimal.TEN, "THB");
        assertThrows(NullPointerException.class, () -> money.multiply((BigDecimal) null));
    }

    @Test
    void divideByBigDecimal() {
        Money money = Money.of(new BigDecimal("100.00"), "THB");
        Money result = money.divide(new BigDecimal("3"));
        assertEquals(new BigDecimal("33.33"), result.amount());
    }

    @Test
    void divideByZeroThrowsException() {
        Money money = Money.of(BigDecimal.TEN, "THB");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> money.divide(BigDecimal.ZERO));
        assertEquals("Cannot divide by zero", ex.getMessage());
    }

    @Test
    void divideWithNullDivisorThrowsException() {
        Money money = Money.of(BigDecimal.TEN, "THB");
        assertThrows(NullPointerException.class, () -> money.divide(null));
    }

    @Test
    void isPositive() {
        assertTrue(Money.of(new BigDecimal("0.01"), "THB").isPositive());
        assertFalse(Money.of(BigDecimal.ZERO, "THB").isPositive());
        assertFalse(Money.of(new BigDecimal("-1.00"), "THB").isPositive());
    }

    @Test
    void isNegative() {
        assertTrue(Money.of(new BigDecimal("-0.01"), "THB").isNegative());
        assertFalse(Money.of(BigDecimal.ZERO, "THB").isNegative());
        assertFalse(Money.of(BigDecimal.ONE, "THB").isNegative());
    }

    @Test
    void isZero() {
        assertTrue(Money.zero("THB").isZero());
        assertFalse(Money.of(BigDecimal.ONE, "THB").isZero());
        assertFalse(Money.of(new BigDecimal("-1.00"), "THB").isZero());
    }

    @Test
    void toStringReturnsFormattedString() {
        Money money = Money.of(new BigDecimal("1234.50"), "THB");
        assertEquals("1234.50 THB", money.toString());
    }

    @Test
    void recordEquality() {
        Money a = Money.of(new BigDecimal("100.00"), "THB");
        Money b = Money.of(new BigDecimal("100.00"), "THB");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
