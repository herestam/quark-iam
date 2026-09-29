package com.example.emailapp.recipient.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.recipient.dto.BulkImportRequest;
import com.example.emailapp.recipient.dto.BulkImportResult;
import com.example.emailapp.recipient.dto.RecipientRequest;
import com.example.emailapp.recipient.dto.RecipientResponse;
import com.example.emailapp.recipient.entity.Recipient;

import jakarta.validation.Validation;
import com.example.emailapp.recipient.mapper.RecipientMapper;
import com.example.emailapp.recipient.repository.RecipientRepository;

/**
 * The bulk import is the riskiest write path in the application: it parses
 * untrusted text, dedupes against the database and writes in one transaction.
 * The repository is mocked so nothing touches PostgreSQL.
 */
class RecipientServiceImplTest {

    private RecipientRepository repository;
    private RecipientServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(RecipientRepository.class);
        service = new RecipientServiceImpl();
        service.validator = Validation.buildDefaultValidatorFactory().getValidator();
        service.repository = repository;
        service.mapper = new RecipientMapper();
    }

    @Test
    @DisplayName("creates one row per new address in a single pass")
    void createsNewRecipients() {
        when(repository.findAllByEmails(anyList())).thenReturn(List.of());
        when(repository.search(anyString(), any(Integer.class), any(Integer.class))).thenReturn(List.of());

        BulkImportResult result = service.importBulk(new BulkImportRequest("""
                jane@example.com,Jane Doe,Acme
                john@example.com,John Roe,Globex
                """, true, true));

        assertEquals(2, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.skippedDuplicates());
        assertTrue(result.errors().isEmpty(), result.errors().toString());

        ArgumentCaptor<Recipient> captor = ArgumentCaptor.forClass(Recipient.class);
        verify(repository, times(2)).persist(captor.capture());
        assertEquals(List.of("jane@example.com", "john@example.com"),
                captor.getAllValues().stream().map(r -> r.email).toList());
    }

    @Test
    @DisplayName("skips a header row instead of importing it as an address")
    void skipsHeaderRow() {
        when(repository.findAllByEmails(anyList())).thenReturn(List.of());
        when(repository.search(anyString(), any(Integer.class), any(Integer.class))).thenReturn(List.of());

        BulkImportResult result = service.importBulk(
                new BulkImportRequest("email,name,company\njane@example.com,Jane,Acme", true, true));

        assertEquals(1, result.created());
    }

    @Test
    @DisplayName("enriches an existing address instead of creating a duplicate")
    void updatesExistingRecipient() {
        Recipient stored = new Recipient();
        stored.id = 7L;
        stored.email = "jane@example.com";
        when(repository.findAllByEmails(anyList())).thenReturn(List.of(stored));
        when(repository.search(anyString(), any(Integer.class), any(Integer.class))).thenReturn(List.of(stored));

        BulkImportResult result = service.importBulk(
                new BulkImportRequest("JANE@example.com,Jane Doe,Acme", false, true));

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals("Jane Doe", stored.name);
        assertEquals("Acme", stored.company);
        verify(repository, never()).persist(any(Recipient.class));
    }

    @Test
    @DisplayName("counts a repeated address in the same file as a duplicate")
    void countsInFileDuplicates() {
        when(repository.findAllByEmails(anyList())).thenReturn(List.of());
        when(repository.search(anyString(), any(Integer.class), any(Integer.class))).thenReturn(List.of());

        BulkImportResult result = service.importBulk(
                new BulkImportRequest("jane@example.com\njane@example.com", false, true));

        assertEquals(1, result.created());
        assertEquals(1, result.skippedDuplicates());
    }

    @Test
    @DisplayName("reports the offending line number for an invalid address")
    void reportsInvalidRows() {
        when(repository.findAllByEmails(anyList())).thenReturn(List.of());
        when(repository.search(anyString(), any(Integer.class), any(Integer.class))).thenReturn(List.of());

        BulkImportResult result = service.importBulk(
                new BulkImportRequest("jane@example.com\nnot-an-email", false, true));

        assertEquals(1, result.created());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).startsWith("2:"), result.errors().toString());
    }

    @Test
    @DisplayName("fails the whole import when skipInvalid is false")
    void strictImportRejectsEverything() {
        InputValidationException thrown = assertThrows(InputValidationException.class,
                () -> service.importBulk(new BulkImportRequest("jane@example.com\nnot-an-email", false, false)));

        assertTrue(thrown.getFieldErrors().size() >= 1);
        verify(repository, never()).persist(any(Recipient.class));
        verify(repository, never()).flush();
    }

    @Test
    @DisplayName("rejects an empty paste instead of importing nothing silently")
    void rejectsEmptyContent() {
        assertThrows(InputValidationException.class,
                () -> service.importBulk(new BulkImportRequest("   ", false, true)));
        assertThrows(InputValidationException.class,
                () -> service.importBulk(new BulkImportRequest(null, false, true)));
    }

    @Test
    @DisplayName("rejects a file that contains no usable address at all")
    void rejectsFileWithoutAddresses() {
        assertThrows(InputValidationException.class,
                () -> service.importBulk(new BulkImportRequest("# just a comment\n\n", false, true)));
    }

    @Test
    @DisplayName("normalises the address to lower case on a single create")
    void normalisesEmailOnCreate() {
        when(repository.findByEmail("jane@example.com")).thenReturn(java.util.Optional.empty());
        doAnswer(invocation -> {
            invocation.<Recipient>getArgument(0).id = 1L;
            return null;
        }).when(repository).persist(any(Recipient.class));

        RecipientResponse created = service.create(new RecipientRequest(" Jane@Example.COM ", "Jane", "Acme"));

        assertEquals("jane@example.com", created.email());
        assertEquals("Jane", created.name());
    }

    @Test
    @DisplayName("refuses to delete a recipient that is still attached to a campaign")
    void refusesToDeleteReferencedRecipient() {
        Recipient stored = new Recipient();
        stored.id = 3L;
        stored.email = "jane@example.com";
        when(repository.findByIdOptional(3L)).thenReturn(java.util.Optional.of(stored));
        when(repository.isReferencedByAnyCampaign(3L)).thenReturn(true);

        assertThrows(InputValidationException.class, () -> service.delete(3L));

        verify(repository, never()).delete(any(Recipient.class));
    }

    @Test
    @DisplayName("deletes an unreferenced recipient")
    void deletesUnreferencedRecipient() {
        Recipient stored = new Recipient();
        stored.id = 3L;
        stored.email = "jane@example.com";
        when(repository.findByIdOptional(3L)).thenReturn(java.util.Optional.of(stored));
        when(repository.isReferencedByAnyCampaign(3L)).thenReturn(false);

        service.delete(3L);

        verify(repository).delete(stored);
    }
}
