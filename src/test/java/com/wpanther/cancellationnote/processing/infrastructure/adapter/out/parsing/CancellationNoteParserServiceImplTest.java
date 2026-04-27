package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.parsing;

import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort.CancellationNoteParsingException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.Reader;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CancellationNoteParserServiceImplTest {

    private CancellationNoteParserServiceImpl parserService;

    @BeforeEach
    void setUp() throws CancellationNoteParsingException {
        parserService = new CancellationNoteParserServiceImpl();
    }

    @Test
    void parse_withNullXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(null, "source-1"));
    }

    @Test
    void parse_withBlankXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse("   ", "source-1"));
    }

    @Test
    void parse_withInvalidXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse("<invalid>not-a-cancellation-note</invalid>", "source-1"));
    }

    @Test
    void constructor_initializesJaxbContext_withoutException() {
        assertDoesNotThrow(() -> new CancellationNoteParserServiceImpl());
    }

    @Test
    @DisplayName("parse valid cancellation note XML produces correct domain object")
    void parse_validCancellationNoteXml_producesCorrectDomainObject() throws CancellationNoteParsingException {
        String xmlContent = getSampleCancellationNoteXml();
        String sourceNoteId = "intake-cn-12345";

        ProcessedCancellationNote note = parserService.parse(xmlContent, sourceNoteId);

        assertNotNull(note);
        assertEquals(sourceNoteId, note.sourceNoteId());
        assertEquals("CN2025-00001", note.cancellationNoteNumber());
        assertEquals(LocalDate.of(2025, 1, 15), note.issueDate());
        assertEquals(LocalDate.of(2025, 1, 15), note.cancellationDate());
        assertEquals("THB", note.currency());
        assertEquals("TV2025-00001", note.cancelledInvoiceNumber());
        assertNotNull(note.id());
        assertNotNull(note.seller());
        assertEquals("Acme Corporation Ltd.", note.seller().name());
        assertNotNull(note.buyer());
        assertEquals("Customer Company Ltd.", note.buyer().name());
        assertEquals(xmlContent, note.originalXml());
    }

    @Test
    @DisplayName("parse XML with cancellation note number containing email contact")
    void parse_xmlWithEmailContact_extractsEmail() throws CancellationNoteParsingException {
        String xmlContent = getSampleCancellationNoteXmlWithEmail();
        ProcessedCancellationNote note = parserService.parse(xmlContent, "source-email");

        assertNotNull(note.seller());
        assertEquals("seller@example.com", note.seller().email());
    }

    @Test
    @DisplayName("parse XML missing ExchangedDocument throws exception")
    void parse_missingExchangedDocument_throwsException() {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-doc"));

        assertThat(ex.getMessage()).contains("ExchangedDocument");
    }

    @Test
    @DisplayName("parse XML missing SupplyChainTradeTransaction throws exception")
    void parse_missingTransaction_throwsException() {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:TypeCode>381</ram:TypeCode>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-tx"));

        assertThat(ex.getMessage()).contains("SupplyChainTradeTransaction");
    }

    @Test
    @DisplayName("parse XML missing cancellation note number throws exception")
    void parse_missingCancellationNoteNumber_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlWithoutId();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-id"));

        assertThat(ex.getMessage()).contains("ID");
    }

    @Test
    @DisplayName("parse XML missing seller throws exception")
    void parse_missingSeller_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlWithoutSeller();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-seller"));

        assertThat(ex.getMessage()).contains("Seller");
    }

    @Test
    @DisplayName("parse XML missing issue date throws exception")
    void parse_missingIssueDate_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlWithoutIssueDate();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-date"));

        assertThat(ex.getMessage()).contains("Issue date");
    }

    @Test
    @DisplayName("parse XML missing cancelled invoice number throws exception")
    void parse_missingCancelledInvoiceNumber_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlWithoutCancelledInvoice();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-cancinv"));

        assertThat(ex.getMessage()).contains("Cancelled invoice number");
    }

    @Test
    @DisplayName("parse XML missing buyer (Invoicer) throws exception")
    void parse_missingBuyer_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlWithoutBuyer();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-buyer"));

        assertThat(ex.getMessage()).contains("Buyer");
    }

    @Test
    @DisplayName("parse XML with seller missing tax ID throws exception")
    void parse_sellerMissingTaxId_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlSellerNoTaxId();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-taxid"));

        assertThat(ex.getMessage()).contains("Seller tax ID");
    }

    @Test
    @DisplayName("parse XML with seller missing address throws exception")
    void parse_sellerMissingAddress_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlSellerNoAddress();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-addr"));

        assertThat(ex.getMessage()).contains("Seller address");
    }

    @Test
    @DisplayName("parse XML with seller missing country throws exception")
    void parse_sellerMissingCountry_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlSellerNoCountry();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-country"));

        assertThat(ex.getMessage()).contains("Seller country");
    }

    @Test
    @DisplayName("parse XML with seller missing name throws exception")
    void parse_sellerMissingName_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlSellerNoName();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-no-name"));

        assertThat(ex.getMessage()).contains("Seller name");
    }

    @Test
    @DisplayName("parse XML with buyer missing name throws exception")
    void parse_buyerMissingName_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlBuyerNoName();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-buyer-no-name"));

        assertThat(ex.getMessage()).contains("Buyer name");
    }

    @Test
    @DisplayName("parse XML with buyer missing tax ID throws exception")
    void parse_buyerMissingTaxId_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlBuyerNoTaxId();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-buyer-no-taxid"));

        assertThat(ex.getMessage()).contains("Buyer tax ID");
    }

    @Test
    @DisplayName("parse XML with buyer missing address throws exception")
    void parse_buyerMissingAddress_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlBuyerNoAddress();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-buyer-no-addr"));

        assertThat(ex.getMessage()).contains("Buyer address");
    }

    @Test
    @DisplayName("parse XML with buyer missing country throws exception")
    void parse_buyerMissingCountry_throwsException() {
        String xmlContent = getSampleCancellationNoteXmlBuyerNoCountry();

        CancellationNoteParsingException ex = assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(xmlContent, "source-buyer-no-country"));

        assertThat(ex.getMessage()).contains("Buyer country");
    }

    @Test
    @DisplayName("parse when unmarshal returns unexpected type throws exception")
    void parse_whenUnmarshalReturnsUnexpectedType_throwsException() throws Exception {
        try (MockedStatic<JAXBContext> mockedJaxb = mockStatic(JAXBContext.class)) {
            JAXBContext mockContext = mock(JAXBContext.class);
            Unmarshaller mockUnmarshaller = mock(Unmarshaller.class);
            mockedJaxb.when(() -> JAXBContext.newInstance(anyString())).thenReturn(mockContext);
            when(mockContext.createUnmarshaller()).thenReturn(mockUnmarshaller);
            when(mockUnmarshaller.unmarshal(any(Reader.class))).thenReturn("unexpected-type");

            CancellationNoteParserServiceImpl service = new CancellationNoteParserServiceImpl();
            CancellationNoteParsingException ex = assertThrows(
                CancellationNoteParsingException.class,
                () -> service.parse("<test/>", "test-id")
            );
            assertThat(ex.getMessage()).contains("Unexpected root element");
        }
    }

    // --- Helper methods for XML samples ---

    private String getSampleCancellationNoteXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16"
                xmlns:qdt="urn:etda:uncefact:data:standard:QualifiedDataType:1">

              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>

              <rsm:ExchangedDocument>
                <ram:ID>CN2025-00001</ram:ID>
                <ram:TypeCode>381</ram:TypeCode>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>

              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Acme Corporation Ltd.</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:LineOne>123 Business Street</ram:LineOne>
                      <ram:CityName>Bangkok</ram:CityName>
                      <ram:PostcodeCode>10110</ram:PostcodeCode>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID schemeID="VAT">1234567890123</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument>
                    <ram:IssuerAssignedID>TV2025-00001</ram:IssuerAssignedID>
                  </ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>

                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Customer Company Ltd.</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:LineOne>456 Customer Road</ram:LineOne>
                      <ram:CityName>Chiang Mai</ram:CityName>
                      <ram:PostcodeCode>50000</ram:PostcodeCode>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID schemeID="VAT">9876543210987</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlWithEmail() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:16"
                xmlns:qdt="urn:etda:uncefact:data:standard:QualifiedDataType:1">

              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>

              <rsm:ExchangedDocument>
                <ram:ID>CN2025-00002</ram:ID>
                <ram:TypeCode>381</ram:TypeCode>
                <ram:IssueDateTime>2025-03-01T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>

              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller With Email</ram:Name>
                    <ram:DefinedTradeContact>
                      <ram:EmailURICIUniversalCommunication>
                        <ram:URIID>seller@example.com</ram:URIID>
                      </ram:EmailURICIUniversalCommunication>
                    </ram:DefinedTradeContact>
                    <ram:PostalTradeAddress>
                      <ram:LineOne>100 Email St</ram:LineOne>
                      <ram:CityName>Bangkok</ram:CityName>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID schemeID="VAT">1111111111111</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument>
                    <ram:IssuerAssignedID>TV2025-00002</ram:IssuerAssignedID>
                  </ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>

                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer Co</ram:Name>
                    <ram:PostalTradeAddress>
                      <ram:CountryID>TH</ram:CountryID>
                    </ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration>
                      <ram:ID schemeID="VAT">2222222222222</ram:ID>
                    </ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlWithoutId() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:TypeCode>381</ram:TypeCode>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlWithoutSeller() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlWithoutIssueDate() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlWithoutCancelledInvoice() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlWithoutBuyer() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlSellerNoTaxId() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlSellerNoAddress() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlSellerNoCountry() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CityName>Bangkok</ram:CityName></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlSellerNoName() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlBuyerNoName() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlBuyerNoTaxId() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlBuyerNoAddress() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }

    private String getSampleCancellationNoteXmlBuyerNoCountry() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CancellationNote_CrossIndustryInvoice
                xmlns:rsm="urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2"
                xmlns:ram="urn:etda:uncefact:data:standard:CancellationNote_ReusableAggregateBusinessInformationEntity:2">
              <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:etda.or.th:cancellationnote:2p1</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:ExchangedDocumentContext>
              <rsm:ExchangedDocument>
                <ram:ID>CN001</ram:ID>
                <ram:IssueDateTime>2025-01-15T00:00:00</ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Seller</ram:Name>
                    <ram:PostalTradeAddress><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">1234567890123</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:AdditionalReferencedDocument><ram:IssuerAssignedID>INV001</ram:IssuerAssignedID></ram:AdditionalReferencedDocument>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:InvoicerTradeParty>
                    <ram:Name>Buyer</ram:Name>
                    <ram:PostalTradeAddress><ram:CityName>Chiang Mai</ram:CityName></ram:PostalTradeAddress>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VAT">9876543210987</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:InvoicerTradeParty>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CancellationNote_CrossIndustryInvoice>
            """;
    }
}
