package com.example.emailapp.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.example.emailapp.campaign.entity.Campaign;
import com.example.emailapp.campaign.entity.CampaignStatus;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CampaignRepository implements PanacheRepositoryBase<Campaign, Long> {

    /** Eagerly fetches template and audience: the list and detail views need both. */
    public static final String WITH_DETAILS =
            "select distinct c from Campaign c"
                    + " left join fetch c.template"
                    + " left join fetch c.recipients"
                    + " where c.id = :id";

    public Optional<Campaign> findByIdOptional(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return find("id", id).firstResultOptional();
    }

    public Optional<Campaign> findByIdWithDetails(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return getEntityManager()
                .createQuery(WITH_DETAILS, Campaign.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    public List<Campaign> listWithDetails(int page, int size) {
        return getEntityManager()
                .createQuery("select distinct c from Campaign c left join fetch c.template order by c.createdAt desc",
                        Campaign.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public long countAll() {
        return count();
    }

    public long countByStatus(CampaignStatus status) {
        return count("status", status);
    }

    /** Number of jobs created from a campaign. */
    public long countByCampaign(Long campaignId) {
        if (campaignId == null) {
            return 0L;
        }
        return count("campaign.id = ?1", campaignId);
    }

    /** Number of campaigns pointing at a template, used before a delete. */
    public long countByTemplate(Long templateId) {
        if (templateId == null) {
            return 0L;
        }
        return count("template.id = ?1", templateId);
    }

    public List<Campaign> findByStatus(CampaignStatus status) {
        return list("status = ?1 order by updatedAt desc", status);
    }

    public List<Campaign> listOrdered() {
        return listAll(Sort.by("createdAt", Sort.Direction.Descending));
    }

    public boolean isNameTaken(String name, Long excludeId) {
        if (excludeId == null) {
            return count("lower(name) = ?1", name.trim().toLowerCase()) > 0;
        }
        return count("lower(name) = ?1 and id <> ?2", name.trim().toLowerCase(), excludeId) > 0;
    }
}
