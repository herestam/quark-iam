package com.example.emailapp.recipient.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import com.example.emailapp.common.exception.InputValidationException;
import com.example.emailapp.common.exception.ResourceNotFoundException;
import com.example.emailapp.common.response.PageResponse;
import com.example.emailapp.common.validation.EmailFormatValidator;
import com.example.emailapp.recipient.dto.BulkImportRequest;
import com.example.emailapp.recipient.dto.BulkImportResult;
import com.example.emailapp.recipient.dto.RecipientRequest;
import com.example.emailapp.recipient.dto.RecipientResponse;
import com.example.emailapp.recipient.entity.Recipient;
import com.example.emailapp.recipient.mapper.RecipientMapper;
import com.example.emailapp.recipient.repository.RecipientRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import java.util.regex.Pattern;

/**
 * Recipient CRUD and bulk import.
 *
 * <p>Import is intentionally forgiving: a bad line never aborts the batch, it is
 * collected into {@link BulkImportResult#errors()} so the administrator can fix
 * and resubmit. The unique index on {@code recipient.email} is the final
 * authority on duplicates; the in-memory set is only there to report them
 * nicely inside a single request.</p>
 */
@ApplicationScoped
public class RecipientServiceImpl implements RecipientService {

    private static final Logger LOG = Logger.getLogger(RecipientServiceImpl.class);
    private static final int MAX_IMPORT_ROWS = 20_000;
    private static final int MAX_REPORTED_ERRORS = 100;

    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]+$");
    private static final EmailFormatValidator EMAIL_VALIDATOR = new EmailFormatValidator();

    @Inject
    RecipientRepository repository;

    @Inject
    RecipientMapper mapper;

    @Inject
    Validator validator;

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public PageResponse<RecipientResponse> list(String search, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 500);
        return PageResponse.of(
                mapper.toResponses(repository.search(search, safePage, safeSize)),
                safePage,
                safeSize,
                repository.countMatching(search));
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public PageResponse<RecipientResponse> listForCampaign(Long campaignId, String search, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 500);
        return PageResponse.of(
                mapper.toResponses(repository.searchForCampaign(campaignId, search, safePage, safeSize)),
                safePage,
                safeSize,
                repository.countForCampaign(campaignId, search));
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public RecipientResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    // ----------------------------------------------------------------- writes

    @Override
    @Transactional
    public RecipientResponse create(RecipientRequest request) {
        validate(request);
        String email = normalise(request.email());
        if (repository.findByEmail(email).isPresent()) {
            throw new InputValidationException("Recipient " + email + " already exists",
                    List.of("email: " + email + " is already in the list"));
        }
        Recipient entity = new Recipient();
        mapper.apply(request, entity);
        entity.email = email;
        repository.persist(entity);
        repository.flush();
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional
    public RecipientResponse update(Long id, RecipientRequest request) {
        validate(request);
        Recipient entity = require(id);
        String email = normalise(request.email());
        repository.findByEmail(email)
                .filter(existing -> !existing.id.equals(id))
                .ifPresent(existing -> {
                    throw new InputValidationException("Recipient " + email + " already exists",
                            List.of("email: " + email + " is already in the list"));
                });
        mapper.apply(request, entity);
        entity.email = email;
        repository.flush();
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Recipient entity = require(id);
        if (repository.isReferencedByAnyCampaign(entity.id)) {
            throw new InputValidationException(
                    "Recipient " + entity.email + " is attached to a campaign and cannot be deleted",
                    List.of("recipient: remove it from the campaign first"));
        }
        repository.delete(entity);
        LOG.infof("Recipient %d (%s) deleted", id, entity.email);
    }

    // ----------------------------------------------------------------- import

    @Override
    @Transactional
    public BulkImportResult importBulk(BulkImportRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new InputValidationException("Nothing to import", List.of("content: must not be blank"));
        }

        List<ParsedRow> rows = parse(request);
        if (rows.isEmpty()) {
            throw new InputValidationException("No recipient could be parsed from the input",
                    List.of("content: no email address found"));
        }

        List<String> errors = new ArrayList<>();
        // Deduplicate inside the request while keeping the first occurrence.
        Map<String, ParsedRow> unique = new LinkedHashMap<>();
        int duplicatesInRequest = 0;
        for (ParsedRow row : rows) {
            if (row.invalid()) {
                addError(errors, row.line() + ": " + row.error());
                continue;
            }
            if (unique.putIfAbsent(row.email(), row) != null) {
                duplicatesInRequest++;
            }
        }

        // Nothing has been written yet, so a strict import can still fail as a whole.
        if (!errors.isEmpty() && !request.skipInvalid()) {
            throw new InputValidationException(
                    "Import rejected: " + errors.size() + " invalid row(s)",
                    errors.size() > MAX_REPORTED_ERRORS ? errors.subList(0, MAX_REPORTED_ERRORS) : errors);
        }

        // One query for the whole batch instead of one per address.
        Map<String, Recipient> existing = new LinkedHashMap<>();
        for (Recipient stored : repository.findAllByEmails(List.copyOf(unique.keySet()))) {
            existing.put(stored.email.toLowerCase(), stored);
        }

        int created = 0;
        int updated = 0;
        List<Recipient> touched = new ArrayList<>();
        for (Map.Entry<String, ParsedRow> entry : unique.entrySet()) {
            ParsedRow row = entry.getValue();
            Recipient stored = existing.get(entry.getKey());
            if (stored == null) {
                Recipient entity = new Recipient();
                entity.email = row.email();
                entity.name = row.name();
                entity.company = row.company();
                repository.persist(entity);
                touched.add(entity);
                created++;
            } else if (row.name() != null || row.company() != null) {
                // Enrich an existing address rather than creating a duplicate.
                if (row.name() != null) {
                    stored.name = row.name();
                }
                if (row.company() != null) {
                    stored.company = row.company();
                }
                touched.add(stored);
                updated++;
            }
        }
        repository.flush();

        LOG.infof("Bulk import: %d submitted, %d created, %d updated, %d duplicates, %d errors",
                rows.size(), created, updated, duplicatesInRequest, errors.size());

        // Only the rows from this request are reported back, never the whole table.
        return new BulkImportResult(
                rows.size(),
                created,
                updated,
                duplicatesInRequest,
                mapper.toResponses(touched),
                errors.size() > MAX_REPORTED_ERRORS ? errors.subList(0, MAX_REPORTED_ERRORS) : errors);
    }

    // --------------------------------------------------------------- internals

    private record ParsedRow(int line, String email, String name, String company, String error) {
        boolean invalid() {
            return error != null;
        }
    }

    private List<ParsedRow> parse(BulkImportRequest request) {
        List<ParsedRow> rows = new ArrayList<>();
        String[] lines = request.content().split("\\r?\\n");
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cells = splitCsvLine(line);
            if (cells.length == 0) {
                continue;
            }
            if (lineNumber == 1 && request.hasHeader() && !EMAIL_SHAPE.matcher(cells[0]).matches()) {
                continue;
            }
            if (lineNumber == 1 && !request.hasHeader() && cells[0].equalsIgnoreCase("email")) {
                continue;
            }
            if (rows.size() >= MAX_IMPORT_ROWS) {
                break;
            }
            String email = normalise(cells[0]);
            if (email.isEmpty()) {
                rows.add(new ParsedRow(lineNumber, null, null, null, "empty address"));
                continue;
            }
            if (!EMAIL_VALIDATOR.isValid(email, null)) {
                rows.add(new ParsedRow(lineNumber, email, null, null, "not a valid email address"));
                continue;
            }
            rows.add(new ParsedRow(lineNumber, email,
                    clean(cells.length > 1 ? cells[1] : null),
                    clean(cells.length > 2 ? cells[2] : null), null));
        }
        return rows;
    }

    /** Minimal RFC 4180 split: handles quoted fields and escaped quotes. */
    static String[] splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',' || c == ';' || c == '\t') {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells.stream().filter(cell -> !cell.isEmpty()).toArray(String[]::new);
    }

    private void validate(RecipientRequest request) {
        if (request == null) {
            throw new InputValidationException("Request body is required");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new InputValidationException("Recipient is not valid",
                    violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().toList());
        }
    }

    private Recipient require(Long id) {
        if (id == null) {
            throw new InputValidationException("Recipient id is required");
        }
        return repository.findByIdOptional(id).orElseThrow(() -> new ResourceNotFoundException("Recipient", id));
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    private static void addError(List<String> errors, String error) {
        if (errors.size() < MAX_REPORTED_ERRORS) {
            errors.add(error);
        }
    }
}
