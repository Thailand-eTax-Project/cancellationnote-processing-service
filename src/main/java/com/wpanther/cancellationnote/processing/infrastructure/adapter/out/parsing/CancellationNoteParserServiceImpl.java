package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.parsing;

import com.wpanther.cancellationnote.processing.domain.model.*;
import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort;
import com.wpanther.etax.generated.cancellationnote.ram.*;
import com.wpanther.etax.generated.cancellationnote.rsm.CancellationNote_CrossIndustryInvoiceType;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CancellationNoteParserServiceImpl implements CancellationNoteParserPort {

    private static final String DEFAULT_CURRENCY = "THB";

    private final JAXBContext jaxbContext;

    public CancellationNoteParserServiceImpl() throws CancellationNoteParsingException {
        try {
            String contextPath = "com.wpanther.etax.generated.cancellationnote.rsm.impl"
                               + ":com.wpanther.etax.generated.cancellationnote.ram.impl"
                               + ":com.wpanther.etax.generated.common.qdt.impl"
                               + ":com.wpanther.etax.generated.common.udt.impl";
            this.jaxbContext = JAXBContext.newInstance(contextPath);
        } catch (JAXBException e) {
            throw new CancellationNoteParsingException("Failed to initialize XML parser", e);
        }
    }

    @Override
    public ProcessedCancellationNote parse(String xmlContent, String sourceNoteId)
            throws CancellationNoteParsingException {
        log.debug("Starting XML parsing for source note ID: {}", sourceNoteId);
        try {
            CancellationNote_CrossIndustryInvoiceType jaxbNote = unmarshalXml(xmlContent);

            var document = jaxbNote.getExchangedDocument();
            if (document == null) {
                throw new CancellationNoteParsingException(
                    "Cancellation note XML missing required ExchangedDocument element");
            }

            var transaction = jaxbNote.getSupplyChainTradeTransaction();
            if (transaction == null) {
                throw new CancellationNoteParsingException(
                    "Cancellation note XML missing required SupplyChainTradeTransaction element");
            }

            LocalDate issueDate = extractIssueDate(document);
            HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
            if (agreement == null) {
                throw new CancellationNoteParsingException(
                    "Cancellation note XML missing required ApplicableHeaderTradeAgreement element");
            }

            Party seller = extractSeller(agreement);
            Party buyer = extractBuyer(transaction);
            String cancelledInvoiceNumber = extractCancelledInvoiceNumber(agreement);

            ProcessedCancellationNote note = ProcessedCancellationNote.builder()
                .id(CancellationNoteId.generate())
                .sourceNoteId(sourceNoteId)
                .cancellationNoteNumber(extractCancellationNoteNumber(document))
                .issueDate(issueDate)
                .cancellationDate(issueDate)
                .seller(seller)
                .buyer(buyer)
                .items(createDefaultLineItem())
                .currency(DEFAULT_CURRENCY)
                .cancelledInvoiceNumber(cancelledInvoiceNumber)
                .originalXml(xmlContent)
                .build();

            log.info("Successfully parsed cancellation note {}", note.cancellationNoteNumber());
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
            Object result = unmarshaller.unmarshal(new StringReader(xmlContent));
            if (result instanceof jakarta.xml.bind.JAXBElement<?> jaxbElement) {
                result = jaxbElement.getValue();
            }
            if (!(result instanceof CancellationNote_CrossIndustryInvoiceType)) {
                throw new CancellationNoteParsingException(
                    "Unexpected root element: " + result.getClass().getName());
            }
            return (CancellationNote_CrossIndustryInvoiceType) result;
        } catch (JAXBException e) {
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

    private String extractCancelledInvoiceNumber(HeaderTradeAgreementType agreement)
            throws CancellationNoteParsingException {
        List<ReferencedDocumentType> refs = agreement.getAdditionalReferencedDocument();
        if (refs != null && !refs.isEmpty()) {
            ReferencedDocumentType ref = refs.get(0);
            if (ref.getIssuerAssignedID() != null && ref.getIssuerAssignedID().getValue() != null) {
                return ref.getIssuerAssignedID().getValue();
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
        return LocalDate.of(issueDateTime.getYear(), issueDateTime.getMonth(), issueDateTime.getDay());
    }

    private Party extractSeller(HeaderTradeAgreementType agreement)
            throws CancellationNoteParsingException {
        if (agreement.getSellerTradeParty() == null) {
            throw new CancellationNoteParsingException("Seller information is missing");
        }
        return mapParty(agreement.getSellerTradeParty(), "Seller");
    }

    private Party extractBuyer(SupplyChainTradeTransactionType transaction)
            throws CancellationNoteParsingException {
        HeaderTradeSettlementType settlement = transaction.getApplicableHeaderTradeSettlement();
        if (settlement == null || settlement.getInvoicerTradeParty() == null) {
            throw new CancellationNoteParsingException("Buyer (Invoicer) information is missing");
        }
        return mapParty(settlement.getInvoicerTradeParty(), "Buyer");
    }

    private Party mapParty(TradePartyType jaxbParty, String partyType)
            throws CancellationNoteParsingException {
        String name = jaxbParty.getName();
        if (name == null || name.isBlank()) {
            throw new CancellationNoteParsingException(partyType + " name is missing");
        }

        TaxIdentifier taxId = extractTaxIdentifier(jaxbParty, partyType);
        Address address = extractAddress(jaxbParty, partyType);

        String email = null;
        List<TradeContactType> contacts = jaxbParty.getDefinedTradeContact();
        if (contacts != null && !contacts.isEmpty()) {
            TradeContactType contact = contacts.get(0);
            if (contact.getEmailURICIUniversalCommunication() != null
                && contact.getEmailURICIUniversalCommunication().getURIID() != null) {
                email = contact.getEmailURICIUniversalCommunication().getURIID().getValue();
            }
        }

        return Party.of(name, taxId, address, email);
    }

    private TaxIdentifier extractTaxIdentifier(TradePartyType jaxbParty, String partyType)
            throws CancellationNoteParsingException {
        SpecifiedTaxRegistrationType taxReg = jaxbParty.getSpecifiedTaxRegistration();
        if (taxReg == null || taxReg.getID() == null || taxReg.getID().getValue() == null) {
            throw new CancellationNoteParsingException(partyType + " tax ID is missing");
        }
        return TaxIdentifier.of(taxReg.getID().getValue(),
            Optional.ofNullable(taxReg.getID().getSchemeID()).orElse("VAT"));
    }

    private Address extractAddress(TradePartyType jaxbParty, String partyType)
            throws CancellationNoteParsingException {
        TradeAddressType addr = jaxbParty.getPostalTradeAddress();
        if (addr == null) {
            throw new CancellationNoteParsingException(partyType + " address is missing");
        }
        String street = Optional.ofNullable(addr.getLineOne())
            .map(l -> l.getValue()).orElse(null);
        String city = Optional.ofNullable(addr.getCityName())
            .map(c -> c.getValue()).orElse(null);
        String postal = Optional.ofNullable(addr.getPostcodeCode())
            .map(p -> p.getValue()).orElse(null);
        if (addr.getCountryID() == null || addr.getCountryID().getValue() == null) {
            throw new CancellationNoteParsingException(partyType + " country is missing");
        }
        return Address.of(street, city, postal, addr.getCountryID().getValue().value());
    }

    private List<LineItem> createDefaultLineItem() {
        // Cancellation Note XSD does not contain line items.
        // Provide a placeholder to satisfy domain model invariant.
        Money zeroPrice = Money.of(BigDecimal.ZERO, DEFAULT_CURRENCY);
        return List.of(new LineItem("Cancellation note", 1, zeroPrice, BigDecimal.ZERO));
    }
}
