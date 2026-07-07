package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    // Die neue, simple Methode: Finde alle Services, die einem bestimmten Handwerker (User) gehören.
    // Das brauchen wir später für das "Provider Dashboard" (S2).
    List<ServiceOffering> findByProviderId(UUID providerId);

    @Query(
            value = """
                    select s
                    from ServiceOffering s
                    where s.status = 'ACTIVE'
                      and (:q = ''
                           or lower(s.title) like lower(concat('%', :q, '%'))
                           or lower(s.description) like lower(concat('%', :q, '%'))
                           or lower(s.provider.firstName) like lower(concat('%', :q, '%'))
                           or lower(s.provider.lastName) like lower(concat('%', :q, '%')))
                      and (:category = '' or s.category = :category)
                      and (:location = '' or lower(s.location) like lower(concat('%', :location, '%')))
                      and (:maxPrice is null or s.price <= :maxPrice)
                      and (select coalesce(avg(r.rating), 0)
                           from Review r
                           where r.booking.serviceOffering = s) >= :minRating
                    """,
            countQuery = """
                    select count(distinct s)
                    from ServiceOffering s
                    where s.status = 'ACTIVE'
                      and (:q = ''
                           or lower(s.title) like lower(concat('%', :q, '%'))
                           or lower(s.description) like lower(concat('%', :q, '%'))
                           or lower(s.provider.firstName) like lower(concat('%', :q, '%'))
                           or lower(s.provider.lastName) like lower(concat('%', :q, '%')))
                      and (:category = '' or s.category = :category)
                      and (:location = '' or lower(s.location) like lower(concat('%', :location, '%')))
                      and (:maxPrice is null or s.price <= :maxPrice)
                      and (select coalesce(avg(r.rating), 0)
                           from Review r
                           where r.booking.serviceOffering = s) >= :minRating
                    """
    )
    Page<ServiceOffering> searchActive(
            @Param("q") String q,
            @Param("category") String category,
            @Param("location") String location,
            @Param("maxPrice") Double maxPrice,
            @Param("minRating") Double minRating,
            Pageable pageable
    );
}
