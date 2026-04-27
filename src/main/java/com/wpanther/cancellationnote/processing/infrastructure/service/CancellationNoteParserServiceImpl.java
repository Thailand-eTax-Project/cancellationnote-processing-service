package com.wpanther.cancellationnote.processing.infrastructure.service;

import com.wpanther.cancellationnote.processing.domain.model.*;
import com.wpanther.cancellationnote.processing.domain.service.CancellationNoteParserService;
import com.wpanther.etax.generated.cancellationnote.ram.*;
import com.wpanther.etax.generated.cancellationnote.rsm.CancellationNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.cancellationnote.rsm.impl.CancellationNote_CrossIndustryInvoiceTypeImpl;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CancellationNoteParserServiceImpl implements CancellationNoteParserService {

    private final JAXBContext jaxbContext;

    public CancellationNoteParserServiceImpl() throws CancellationNoteParsingException {
        try {
            String contextPath = "com.wpanther.etax.generated.cancellationnote.rsm.impl" +
                               ":com.wpanther.etax.generated.cancellationnote.ram.impl" +
                               ":com.wpanther.etax.generated.common.qdt.impl" +
                               ":com.wpanther.etax.generated.common.udt.impl";
            this.jaxbContext = JAXBContext.newInstance(contextPath);
            log.info("JAXB context initialized successfully for Cancellation Note parsing");
        } catch (JAXBException e) {
            log.error("Failed to initialize JAXB context", e);
            throw new CancellationNoteParsingException("Failed to initialize XML parser", e);
        }
    }

    @Override
    public ProcessedCancellationNote parseCancellationNote(String xmlContent, String sourceNoteId)
            throws CancellationNoteParsingException {

        log.debug("Starting XML parsing for source note ID: {}", sourceNoteId);

        try {
            CancellationNote_CrossIndustryInvoiceType jaxbNote = unmarshalXml(xmlContent);

            ExchangedDocumentType document = jaxbNote.getExchangedDocument();
            if (document == null) {
                throw new CancellationNoteParsingException("Cancellation note XML missing required ExchangedDocument element");
            }

            SupplyChainTradeTransactionType transaction = jaxbNote.getSupplyChainTradeTransaction();
            if (transaction == null) {
                throw new CancellationNoteParsingException("Cancellation note XML missing required SupplyChainTradeTransaction element");
            }

            LocalDate issueDate = extractIssueDate(document);
            String cancelledInvoiceNumber = extractCancelledInvoiceNumber(document);

            ProcessedCancellationNote note = ProcessedCancellationNote.builder()
                .id(CancellationNoteId.generate())
                .sourceNoteId(sourceNoteId)
                .cancellationNoteNumber(extractCancellationNoteNumber(document))
                .issueDate(issueDate)
                .cancellationDate(issueDate)
                .seller(extractSeller(transaction))
                .buyer(extractBuyer(transaction))
                .items(extractLineItems(transaction))
                .currency(extractCurrency(transaction))
                .cancelledInvoiceNumber(cancelledInvoiceNumber)
                .originalXml(xmlContent)
                .build();

            log.info("Successfully parsed cancellation note {} with {} line items",
                note.cancellationNoteNumber(), note.items().size());

            return note;

        } catch (CancellationNoteParsingException e) {
            log.error("Failed to parse cancellation note XML for source ID {}: {}",
                sourceNoteId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error parsing cancellation note XML for source ID " + sourceNoteId, e);
            throw new CancellationNoteParsingException("Unexpected error during cancellation note parsing", e);
        }
    }

    private CancellationNote_CrossIndustryInvoiceType unmarshalXml(String xmlContent)
            throws CancellationNoteParsingException {

        if (xmlContent == null || xmlContent.isBlank()) {
            throw new CancellationNoteParsingException("XML content is null or empty");
        }

        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            StringReader reader = new StringReader(xmlContent);

            Object result = unmarshaller.unmarshal(reader);

            if (result instanceof jakarta.xml.bind.JAXBElement) {
                jakarta.xml.bind.JAXBElement<?> jaxbElement = (jakarta.xml.bind.JAXBElement<?>) result;
                result = jaxbElement.getValue();
            }

            if (!(result instanceof CancellationNote_CrossIndustryInvoiceType)) {
                throw new CancellationNoteParsingException(
                    "Unexpected root element: " + result.getClass().getName()
                );
            }

            return (CancellationNote_CrossIndustryInvoiceType) result;

        } catch (JAXBException e) {
            log.error("JAXB unmarshalling failed", e);
            throw new CancellationNoteParsingException("Failed to parse XML: " + e.getMessage(), e);
        }
    }

    private String extractCancellationNoteNumber(ExchangedDocumentType document)
            throws CancellationNoteParsingException {

        if (document.getID() == null || document.getID().getValue() == null) {
            throw new CancellationNoteParsingException("Cancellation note number (ID) is missing");
        }

        return document.getID().getValue();
    }

    private String extractCancelledInvoiceNumber(ExchangedDocumentType document)
            throws CancellationNoteParsingException {

        List<ReferencedDocumentType> referencedDocs = document.getIncludedReferencedDocument();
        if (referencedDocs != null && !referencedDocs.isEmpty()) {
            ReferencedDocumentType refDoc = referencedDocs.get(0);
            if (refDoc.getIssuerAssignedID() != null && refDoc.getIssuerAssignedID().getValue() != null) {
                return refDoc.getIssuerAssignedID().getValue();
            }
        }

        throw new CancellationNoteParsingException("Cancelled invoice number is missing");
    }

    private LocalDate extractIssueDate(ExchangedDocumentType document)
            throws CancellationNoteParsingException {

        XMLGregorianCalendar issueDateTime = document.getIssueDateTime();
        if (issueDateTime == null) {
            throw new CancellationNoteParsingException("Issue date/time is missing");
        }

        return convertXMLGregorianCalendarToLocalDate(issueDateTime);
    }

    private Party extractSeller(SupplyChainTradeTransactionType transaction)
            throws CancellationNoteParsingException {

        HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
        if (agreement == null || agreement.getSellerTradeParty() == null) {
            throw new CancellationNoteParsingException("Seller information is missing");
        }

        return mapParty(agreement.getSellerTradeParty(), "Seller");
    }

    private Party extractBuyer(SupplyChainTradeTransactionType transaction)
            throws CancellationNoteParsingException {

        HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
        if (agreement == null || agreement.getBuyerTradeParty() == null) {
            throw new CancellationNoteParsingException("Buyer information is missing");
        }

        return mapParty(agreement.getBuyerTradeParty(), "Buyer");
    }

    private Party mapParty(TradePartyType jaxbParty, String partyType)
            throws CancellationNoteParsingException {

        String name = Optional.ofNullable(jaxbParty.getName())
            .map(n -> n.getValue())
            .orElseThrow(() -> new CancellationNoteParsingException(partyType + " name is missing"));

        TaxIdentifier taxIdentifier = extractTaxIdentifier(jaxbParty, partyType);

        Address address = extractAddress(jaxbParty, partyType);

        String email = null;
        List<TradeContactType> contacts = jaxbParty.getDefinedTradeContact();
        if (contacts != null && !contacts.isEmpty()) {
            TradeContactType contact = contacts.get(0);
            if (contact.getEmailURIUniversalCommunication() != null &&
                contact.getEmailURIUniversalCommunication().getURIID() != null) {
                email = contact.getEmailURIUniversalCommunication().getURIID().getValue();
            }
        }

        return Party.of(name, taxIdentifier, address, email);
    }

    private TaxIdentifier extractTaxIdentifier(TradePartyType jaxbParty, String partyType)
            throws CancellationNoteParsingException {

        TaxRegistrationType taxReg = jaxbParty.getSpecifiedTaxRegistration();
        if (taxReg == null) {
            throw new CancellationNoteParsingException(partyType + " tax registration is missing");
        }

        if (taxReg.getID() == null || taxReg.getID().getValue() == null) {
            throw new CancellationNoteParsingException(partyType + " tax ID is missing");
        }

        String taxId = taxReg.getID().getValue();
        String scheme = Optional.ofNullable(taxReg.getID().getSchemeID())
            .orElse("VAT");

        return TaxIdentifier.of(taxId, scheme);
    }

    private Address extractAddress(TradePartyType jaxbParty, String partyType)
            throws CancellationNoteParsingException {

        TradeAddressType jaxbAddress = jaxbParty.getPostalTradeAddress();
        if (jaxbAddress == null) {
            throw new CancellationNoteParsingException(partyType + " address is missing");
        }

        String streetAddress = Optional.ofNullable(jaxbAddress.getLineOne())
            .map(line -> line.getValue())
            .orElse(null);

        String city = Optional.ofNullable(jaxbAddress.getCityName())
            .map(name -> name.getValue())
            .orElse(null);

        String postalCode = Optional.ofNullable(jaxbAddress.getPostcodeCode())
            .map(code -> code.getValue())
            .orElse(null);

        String country = null;
        if (jaxbAddress.getCountryID() != null && jaxbAddress.getCountryID().getValue() != null) {
            country = jaxbAddress.getCountryID().getValue().value();
        }
        if (country == null) {
            throw new CancellationNoteParsingException(partyType + " country is missing");
        }

        return Address.of(streetAddress, city, postalCode, country);
    }

    private List<LineItem> extractLineItems(SupplyChainTradeTransactionType transaction)
            throws CancellationNoteParsingException {

        List<SupplyChainTradeLineItemType> jaxbItems =
            transaction.getIncludedSupplyChainTradeLineItem();

        if (jaxbItems == null || jaxbItems.isEmpty()) {
            throw new CancellationNoteParsingException("Cancellation note must have at least one line item");
        }

        List<LineItem> items = new ArrayList<>();
        String currency = extractCurrency(transaction);

        for (int i = 0; i < jaxbItems.size(); i++) {
            try {
                LineItem item = mapLineItem(jaxbItems.get(i), currency);
                items.add(item);
            } catch (Exception e) {
                throw new CancellationNoteParsingException(
                    "Failed to parse line item " + (i + 1) + ": " + e.getMessage(), e
                );
            }
        }

        return items;
    }

    private LineItem mapLineItem(SupplyChainTradeLineItemType jaxbItem, String currency)
            throws CancellationNoteParsingException {

        TradeProductType product = jaxbItem.getSpecifiedTradeProduct();
        if (product == null || product.getName() == null || product.getName().isEmpty()) {
            throw new CancellationNoteParsingException("Line item product name is missing");
        }
        String description = product.getName().get(0).getValue();

        LineTradeDeliveryType delivery = jaxbItem.getSpecifiedLineTradeDelivery();
        if (delivery == null || delivery.getBilledQuantity() == null) {
            throw new CancellationNoteParsingException("Line item quantity is missing");
        }
        BigDecimal quantityDecimal = delivery.getBilledQuantity().getValue();
        int quantity = quantityDecimal.intValue();

        LineTradeAgreementType agreement = jaxbItem.getSpecifiedLineTradeAgreement();
        if (agreement == null || agreement.getGrossPriceProductTradePrice() == null) {
            throw new CancellationNoteParsingException("Line item unit price is missing");
        }
        TradePriceType priceType = agreement.getGrossPriceProductTradePrice();
        if (priceType.getChargeAmount() == null || priceType.getChargeAmount().isEmpty()) {
            throw new CancellationNoteParsingException("Line item price amount is missing");
        }
        BigDecimal unitPriceAmount = priceType.getChargeAmount().get(0).getValue();
        Money unitPrice = Money.of(unitPriceAmount, currency);

        LineTradeSettlementType settlement = jaxbItem.getSpecifiedLineTradeSettlement();
        BigDecimal taxRate = BigDecimal.ZERO;

        if (settlement != null && settlement.getApplicableTradeTax() != null
            && !settlement.getApplicableTradeTax().isEmpty()) {

            TradeTaxType tax = settlement.getApplicableTradeTax().get(0);
            if (tax.getCalculatedRate() != null) {
                taxRate = tax.getCalculatedRate();
            }
        }

        return new LineItem(description, quantity, unitPrice, taxRate);
    }

    private String extractCurrency(SupplyChainTradeTransactionType transaction)
            throws CancellationNoteParsingException {

        HeaderTradeSettlementType settlement = transaction.getApplicableHeaderTradeSettlement();
        if (settlement == null || settlement.getInvoiceCurrencyCode() == null) {
            throw new CancellationNoteParsingException("Cancellation note currency is missing");
        }

        String currency = null;
        if (settlement.getInvoiceCurrencyCode().getValue() != null) {
            currency = settlement.getInvoiceCurrencyCode().getValue().value();
        }

        if (currency == null || currency.length() != 3) {
            throw new CancellationNoteParsingException("Invalid currency code: " + currency);
        }

        return currency;
    }

    private LocalDate convertXMLGregorianCalendarToLocalDate(XMLGregorianCalendar calendar) {
        if (calendar == null) {
            return null;
        }
        return LocalDate.of(
            calendar.getYear(),
            calendar.getMonth(),
            calendar.getDay()
        );
    }
}
