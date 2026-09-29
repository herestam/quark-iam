package com.example.emailapp.recipient.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.emailapp.recipient.entity.Recipient;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RecipientRepository implements PanacheRepositoryBase<Recipient, Long> {

    private static final String SEARCH_QUERY = "lower(email) like :pattern"
            + " or lower(coalesce(name,'')) like :pattern"
            + " or lower(coalesce(company,'')) like :pattern";

    public Optional<Recipient> findByIdOptional(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return find("id", id).firstResultOptional();
    }

    public Optional<Recipient> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return find("lower(email) = ?1", email.trim().toLowerCase()).firstResultOptional();
    }

    /**
     * Bulk lookup used by the import paths: one query instead of N, so a 10k row
     * CSV import neither slows down nor triggers a lazy-loading storm.
     */
    public List<Recipient> findAllByEmails(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("select r from Recipient r where lower(r.email) in :emails", Recipient.class)
                .setParameter("emails", emails.stream().map(e -> e.toLowerCase()).toList())
                .getResultList();
    }

    public List<Recipient> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("select r from Recipient r where r.id in :ids order by r.id", Recipient.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    public List<Recipient> search(String term, int page, int size) {
        return find(SEARCH_QUERY, Sort.by("createdAt", Sort.Direction.Descending), pattern(term))
                .page(Page.of(page, size))
                .list();
    }

    public long countMatching(String term) {
        return count(SEARCH_QUERY, pattern(term));
    }

    public long countAll() {
        return count();
    }

    /** Full-text search restricted to one campaign's audience, for the picker. */
    public List<Recipient> searchForCampaign(Long campaignId, String term, int page, int size) {
        if (campaignId == null) {
            return search(term, page, size);
        }
        return getEntityManager()
                .createQuery("select r from Recipient r where r in (select c.recipient from Campaign c where c.id = :campaignId)"
                        + " and (lower(r.email) like :term or lower(coalesce(r.name,'')) like :term"
                        + " or lower(coalesce(r.company,'')) like :term) order by r.email", Recipient.class)
                .setParameter("campaignId", campaignId)
                .setParameter("term", likePattern(term))
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public long countForCampaign(Long campaignId, String term) {
        if (campaignId == null) {
            return countMatching(term);
        }
        return getEntityManager()
                .createQuery("select count(r) from Recipient r where r in (select c.recipient from Campaign c where c.id = :campaignId)"
                        + " and (lower(r.email) like :term or lower(coalesce(r.name,'')) like :term"
                        + " or lower(coalesce(r.company,'')) like :term)", Long.class)
                .setParameter("campaignId", campaignId)
                .setParameter("term", likePattern(term))
                .getSingleResult();
    }

    public boolean isReferencedByAnyCampaign(Long recipientId) {
        return getEntityManager()
                .createQuery("select count(c) from Campaign c join c.recipients r where r.id = :id", Long.class)
                .setParameter("id", recipientId)
                .getSingleResult() > 0;
    }

    public List<Recipient> findByCampaign(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("select c.recipient from Campaign c where c.id = :id order by c.recipient.email", Recipient.class)
                .setParameter("id", campaignId)
                .getResultList();
    }

    private static Map<String, Object> pattern(String term) {
        return Map.of("pattern", likePattern(term));
    }

    private static String likePattern(String term) {
        return "%" + (term == null ? "" : term.trim().toLowerCase()) + "%";
    }
}
