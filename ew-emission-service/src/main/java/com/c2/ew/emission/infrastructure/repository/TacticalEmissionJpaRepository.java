package com.c2.ew.emission.infrastructure.repository;

import com.c2.ew.emission.domain.enums.*;
import com.c2.ew.emission.infrastructure.entity.TacticalEmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TacticalEmissionJpaRepository extends JpaRepository<TacticalEmissionEntity, String>, JpaSpecificationExecutor<TacticalEmissionEntity> {

    long countByTacizTrue();

    long countByEtUygulamaDurumu(EtUygulamaDurumu etUygulamaDurumu);

    long countByTeshisKimlik(TeshisKimlik teshisKimlik);

    long countByVeriKaynagi(VeriKaynagi veriKaynagi);

    long countByKayitTipi(KayitTipi kayitTipi);

    long countByKayitTipiAndKestirimIdIsNotNull(KayitTipi kayitTipi);

    long countByKayitTipiAndKestirimIdIsNull(KayitTipi kayitTipi);

    List<TacticalEmissionEntity> findByKestirimId(String kestirimId);

    @Query("SELECT MAX(e.sonTespitZamani) FROM TacticalEmissionEntity e")
    Instant findMaxSonTespitZamani();

    @Query("SELECT e.kayitTipi, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.kayitTipi")
    List<Object[]> getKayitTipiDistribution();

    @Query("SELECT e.teshisKimlik, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.teshisKimlik")
    List<Object[]> getTeshisDistribution();

    @Query("SELECT e.veriKaynagi, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.veriKaynagi")
    List<Object[]> getVeriKaynagiDistribution();

    @Query("SELECT e.platformOrtami, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.platformOrtami")
    List<Object[]> getPlatformOrtamiDistribution();

    @Query("SELECT e.etUygulamaDurumu, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.etUygulamaDurumu")
    List<Object[]> getEtUygulamaDistribution();

    // ==========================================
    // HIYERARŞİK KESTİRİM VE LOB SORGULARI
    // ==========================================

    @Query(value = "SELECT " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'KESTIRIM_ELIPS') as total_elips, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'KESTIRIM_ELIPS' AND EXISTS (SELECT 1 FROM tactical_emissions l WHERE l.kestirim_id = e.emission_id)) as childed_elips, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'KESTIRIM_ELIPS' AND NOT EXISTS (SELECT 1 FROM tactical_emissions l WHERE l.kestirim_id = e.emission_id)) as childless_elips, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'HAM_LOB') as total_lob, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'HAM_LOB' AND kestirim_id IS NOT NULL) as bagli_lob, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'HAM_LOB' AND kestirim_id IS NULL) as basibos_lob " +
           "FROM tactical_emissions e " +
           "WHERE e.son_tespit_zamani >= :baslangic AND e.son_tespit_zamani <= :bitis", nativeQuery = true)
    Object[] getRadarHierarchyCountsWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis);

    @Query(value = "SELECT e.emission_id, e.radar_adi, e.teshis_kimlik, e.eh_unsuru, e.yon, e.yayin_enlem, e.yayin_boylam, e.yayin_semi_major_meters, e.yayin_semi_minor_meters, e.son_tespit_zamani, e.operator_notu, " +
           "       (SELECT COUNT(*) FROM tactical_emissions l WHERE l.kestirim_id = e.emission_id) as child_count " +
           "FROM tactical_emissions e " +
           "WHERE e.kayit_tipi = 'KESTIRIM_ELIPS' " +
           "  AND e.son_tespit_zamani >= :baslangic AND e.son_tespit_zamani <= :bitis " +
           "  AND EXISTS (SELECT 1 FROM tactical_emissions l WHERE l.kestirim_id = e.emission_id) " +
           "ORDER BY e.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getRadarChildedEllipsesWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT e.emission_id, e.radar_adi, e.teshis_kimlik, e.eh_unsuru, e.yon, e.yayin_enlem, e.yayin_boylam, e.yayin_semi_major_meters, e.yayin_semi_minor_meters, e.son_tespit_zamani, e.operator_notu, " +
           "       0 as child_count " +
           "FROM tactical_emissions e " +
           "WHERE e.kayit_tipi = 'KESTIRIM_ELIPS' " +
           "  AND e.son_tespit_zamani >= :baslangic AND e.son_tespit_zamani <= :bitis " +
           "  AND NOT EXISTS (SELECT 1 FROM tactical_emissions l WHERE l.kestirim_id = e.emission_id) " +
           "ORDER BY e.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getRadarChildlessEllipsesWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT e.emission_id, e.radar_adi, e.teshis_kimlik, e.eh_unsuru, e.yon, e.yayin_enlem, e.yayin_boylam, e.yayin_semi_major_meters, e.yayin_semi_minor_meters, e.son_tespit_zamani, e.operator_notu, " +
           "       (SELECT COUNT(*) FROM tactical_emissions l WHERE l.kestirim_id = e.emission_id) as child_count " +
           "FROM tactical_emissions e " +
           "WHERE e.kayit_tipi = 'KESTIRIM_ELIPS' " +
           "  AND e.son_tespit_zamani >= :baslangic AND e.son_tespit_zamani <= :bitis " +
           "ORDER BY e.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getRadarAllEllipsesWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT l.emission_id, l.radar_adi, l.teshis_kimlik, l.eh_unsuru, l.eh_unsuru_enlem, l.eh_unsuru_boylam, l.yon, l.min_frekans_mhz, l.son_tespit_zamani, l.operator_notu, " +
           "       l.kestirim_id " +
           "FROM tactical_emissions l " +
           "WHERE l.kayit_tipi = 'HAM_LOB' " +
           "  AND l.son_tespit_zamani >= :baslangic AND l.son_tespit_zamani <= :bitis " +
           "  AND l.kestirim_id IS NULL " +
           "ORDER BY l.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getRadarUnassociatedLobsWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT l.emission_id, l.radar_adi, l.teshis_kimlik, l.eh_unsuru, l.eh_unsuru_enlem, l.eh_unsuru_boylam, l.yon, l.min_frekans_mhz, l.son_tespit_zamani, l.operator_notu, " +
           "       l.kestirim_id " +
           "FROM tactical_emissions l " +
           "WHERE l.kayit_tipi = 'HAM_LOB' " +
           "  AND l.son_tespit_zamani >= :baslangic AND l.son_tespit_zamani <= :bitis " +
           "  AND l.kestirim_id IS NOT NULL " +
           "ORDER BY l.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getRadarAssociatedLobsWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT l.emission_id, l.radar_adi, l.teshis_kimlik, l.eh_unsuru, l.eh_unsuru_enlem, l.eh_unsuru_boylam, l.yon, l.min_frekans_mhz, l.son_tespit_zamani, l.operator_notu, " +
           "       l.kestirim_id " +
           "FROM tactical_emissions l " +
           "WHERE l.kayit_tipi = 'HAM_LOB' " +
           "  AND l.son_tespit_zamani >= :baslangic AND l.son_tespit_zamani <= :bitis " +
           "ORDER BY l.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getRadarAllLobsWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT e.emission_id, e.kayit_tipi, e.yayin_enlem, e.yayin_boylam, " +
           "       e.yayin_semi_major_meters, e.yayin_semi_minor_meters, e.yayin_orientation_degrees, " +
           "       e.eh_unsuru_enlem, e.eh_unsuru_boylam, e.yon, " +
           "       e.teshis_kimlik, e.radar_adi, e.eh_unsuru, " +
           "       e.son_tespit_zamani, e.kestirim_id " +
           "FROM tactical_emissions e " +
           "ORDER BY e.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findMapEmissionsNative(@Param("limit") int limit);
}

