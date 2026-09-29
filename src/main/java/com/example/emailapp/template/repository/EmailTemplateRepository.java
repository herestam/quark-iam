package com.example.emailapp.template.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.emailapp.template.entity.EmailTemplate;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailTemplateRepository implements PanacheRepositoryBase<EmailTemplate, Long> {

    public Optional<EmailTemplate> findByIdOptional(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return find("id", id).firstResultOptional();
    }

    public List<EmailTemplate> findAllOrdered() {
        return listAll(Sort.by("name"));
    }

    public List<EmailTemplate> search(String term, int page, int size) {
        return find(SEARCH_QUERY, Sort.by("updatedAt", Sort.Direction.Descending), pattern(term))
                .page(Page.of(page, size))
                .list();
    }

    public long countMatching(String term) {
        return count(SEARCH_QUERY, pattern(term));
    }

    public long countAll() {
        return count();
    }

    public boolean existsByName(String name, Long excludeId) {
        String pattern = likePattern(name);
        if (excludeId == null) {
            return count("lower(name) like :pattern", Map.of("pattern", pattern)) > 0;
        }
        return count("lower(name) like :pattern and id <> :excludeId",
                Map.of("pattern", pattern, "excludeId", excludeId)) > 0;
    }

    private static final String SEARCH_QUERY = "lower(name) like :pattern"
            + " or lower(subject) like :pattern"
            + " or lower(htmlContent) like :pattern";

    private static Map<String, Object> pattern(String term) {
        return Map.of("pattern", likePattern(term));
    }

    private static String likePattern(String term) {
        return "%" + (term == null ? "" : term.trim().toLowerCase()) + "%";
    }
}
