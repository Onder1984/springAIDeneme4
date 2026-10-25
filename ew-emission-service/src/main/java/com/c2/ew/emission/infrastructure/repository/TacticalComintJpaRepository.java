package com.c2.ew.emission.infrastructure.repository;

import com.c2.ew.emission.domain.enums.*;
import com.c2.ew.emission.infrastructure.entity.TacticalComintEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TacticalComintJpaRepository extends JpaRepository<TacticalComintEntity, String>, JpaSpecificationExecutor<TacticalComintEntity> {

    long countByTeshisKimlik(TeshisKimlik teshisKimlik);

    long countByHaberlesmeSekli(HaberlesmeSekli haberlesmeSekli);

    long countByTip(MuhabereBantTipi tip);

    long countByVeriKaynagi(VeriKaynagi veriKaynagi);

    long countByKayitTipi(KayitTipi kayitTipi);

    long countByKayitTipiAndKestirimIdIsNotNull(KayitTipi kayitTipi);

    long countByKayitTipiAndKestirimIdIsNull(KayitTipi kayitTipi);

    List<TacticalComintEntity> findByKestirimId(String kestirimId);

    @Query("SELECT MAX(c.sonTespitZamani) FROM TacticalComintEntity c")
    Instant findMaxSonTespitZamani();

    @Query("SELECT c.kayitTipi, COUNT(c) FROM TacticalComintEntity c GROUP BY c.kayitTipi")
    List<Object[]> getKayitTipiDistribution();

    List<TacticalComintEntity> findByCagriAdiContainingIgnoreCaseOrKarsiCagriAdiContainingIgnoreCase(String cagriAdi, String karsiCagriAdi);

    List<TacticalComintEntity> findByCagriAdiContainingIgnoreCaseOrKarsiCagriAdiContainingIgnoreCase(String cagriAdi, String karsiCagriAdi, Pageable pageable);

    long countByCagriAdiContainingIgnoreCaseOrKarsiCagriAdiContainingIgnoreCase(String cagriAdi, String karsiCagriAdi);

    @Query("SELECT c.cagriAdi, COUNT(c) FROM TacticalComintEntity c " +
           "WHERE c.cagriAdi IS NOT NULL AND TRIM(c.cagriAdi) <> '' " +
           "GROUP BY c.cagriAdi ORDER BY COUNT(c) DESC")
    List<Object[]> getTopCallsignsDistribution(Pageable pageable);

    default List<Object[]> getTopCallsignsDistribution() {
        return getTopCallsignsDistribution(Pageable.unpaged());
    }

    @Query("SELECT c.cagriAdi, COUNT(c) FROM TacticalComintEntity c " +
           "WHERE c.sonTespitZamani >= :baslangic AND c.sonTespitZamani <= :bitis " +
           "  AND c.cagriAdi IS NOT NULL AND TRIM(c.cagriAdi) <> '' " +
           "GROUP BY c.cagriAdi ORDER BY COUNT(c) DESC")
    List<Object[]> getTopCallsignsDistributionWithTimeRange(
        @Param("baslangic") Instant baslangic,
        @Param("bitis") Instant bitis,
        Pageable pageable
    );

    @Query(value = "SELECT c.cagri_adi, c.karsi_cagri_adi, COUNT(*) as total, COALESCE(SUM(c.sure_sn), 0.0) as top_sn, " +
           "       COUNT(*) FILTER (WHERE c.tip = 'VHF') as vhf_adet, " +
           "       COUNT(*) FILTER (WHERE c.tip = 'HF') as hf_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'KRIPTO') as kripto_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'SES') as ses_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'VERI') as veri_adet, " +
           "       COALESCE(MODE() WITHIN GROUP (ORDER BY c.lisan), '-') as baskin_lisan " +
           "FROM tactical_comint c " +
           "WHERE (:cagri IS NULL OR LOWER(c.cagri_adi) LIKE LOWER(CONCAT('%', :cagri, '%'))) " +
           "  AND (:karsi IS NULL OR LOWER(c.karsi_cagri_adi) LIKE LOWER(CONCAT('%', :karsi, '%'))) " +
           "  AND c.cagri_adi IS NOT NULL AND TRIM(c.cagri_adi) <> '' " +
           "  AND c.karsi_cagri_adi IS NOT NULL AND TRIM(c.karsi_cagri_adi) <> '' " +
           "GROUP BY c.cagri_adi, c.karsi_cagri_adi ORDER BY COUNT(*) DESC", nativeQuery = true)
    List<Object[]> getFilteredCallsignInteractionMatrix(
        @Param("cagri") String cagri,
        @Param("karsi") String karsi,
        Pageable pageable
    );

    @Query(value = "SELECT c.cagri_adi, c.karsi_cagri_adi, COUNT(*) as total, COALESCE(SUM(c.sure_sn), 0.0) as top_sn, " +
           "       COUNT(*) FILTER (WHERE c.tip = 'VHF') as vhf_adet, " +
           "       COUNT(*) FILTER (WHERE c.tip = 'HF') as hf_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'KRIPTO') as kripto_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'SES') as ses_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'VERI') as veri_adet, " +
           "       COALESCE(MODE() WITHIN GROUP (ORDER BY c.lisan), '-') as baskin_lisan " +
           "FROM tactical_comint c " +
           "WHERE (:cagri IS NULL OR LOWER(c.cagri_adi) LIKE LOWER(CONCAT('%', :cagri, '%'))) " +
           "  AND (:karsi IS NULL OR LOWER(c.karsi_cagri_adi) LIKE LOWER(CONCAT('%', :karsi, '%'))) " +
           "  AND c.son_tespit_zamani >= :baslangic AND c.son_tespit_zamani <= :bitis " +
           "  AND c.cagri_adi IS NOT NULL AND TRIM(c.cagri_adi) <> '' " +
           "  AND c.karsi_cagri_adi IS NOT NULL AND TRIM(c.karsi_cagri_adi) <> '' " +
           "GROUP BY c.cagri_adi, c.karsi_cagri_adi ORDER BY COUNT(*) DESC", nativeQuery = true)
    List<Object[]> getFilteredCallsignInteractionMatrixWithTimeRange(
        @Param("cagri") String cagri,
        @Param("karsi") String karsi,
        @Param("baslangic") Instant baslangic,
        @Param("bitis") Instant bitis,
        Pageable pageable
    );

    @Query(value = "SELECT c.cagri_adi, c.karsi_cagri_adi, COUNT(*) as total, COALESCE(SUM(c.sure_sn), 0.0) as top_sn, " +
           "       COUNT(*) FILTER (WHERE c.tip = 'VHF') as vhf_adet, " +
           "       COUNT(*) FILTER (WHERE c.tip = 'HF') as hf_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'KRIPTO') as kripto_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'SES') as ses_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'VERI') as veri_adet, " +
           "       COALESCE(MODE() WITHIN GROUP (ORDER BY c.lisan), '-') as baskin_lisan " +
           "FROM tactical_comint c " +
           "WHERE c.cagri_adi IS NOT NULL AND TRIM(c.cagri_adi) <> '' " +
           "  AND c.karsi_cagri_adi IS NOT NULL AND TRIM(c.karsi_cagri_adi) <> '' " +
           "GROUP BY c.cagri_adi, c.karsi_cagri_adi ORDER BY COUNT(*) DESC", nativeQuery = true)
    List<Object[]> getCallsignInteractionMatrix(Pageable pageable);

    default List<Object[]> getCallsignInteractionMatrix() {
        return getCallsignInteractionMatrix(Pageable.unpaged());
    }

    @Query(value = "SELECT c.cagri_adi, c.karsi_cagri_adi, COUNT(*) as total, COALESCE(SUM(c.sure_sn), 0.0) as top_sn, " +
           "       COUNT(*) FILTER (WHERE c.tip = 'VHF') as vhf_adet, " +
           "       COUNT(*) FILTER (WHERE c.tip = 'HF') as hf_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'KRIPTO') as kripto_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'SES') as ses_adet, " +
           "       COUNT(*) FILTER (WHERE c.haberlesme_sekli = 'VERI') as veri_adet, " +
           "       COALESCE(MODE() WITHIN GROUP (ORDER BY c.lisan), '-') as baskin_lisan " +
           "FROM tactical_comint c " +
           "WHERE c.son_tespit_zamani >= :baslangic AND c.son_tespit_zamani <= :bitis " +
           "  AND c.cagri_adi IS NOT NULL AND TRIM(c.cagri_adi) <> '' " +
           "  AND c.karsi_cagri_adi IS NOT NULL AND TRIM(c.karsi_cagri_adi) <> '' " +
           "GROUP BY c.cagri_adi, c.karsi_cagri_adi ORDER BY COUNT(*) DESC", nativeQuery = true)
    List<Object[]> getCallsignInteractionMatrixWithTimeRange(
        @Param("baslangic") Instant baslangic,
        @Param("bitis") Instant bitis,
        Pageable pageable
    );

    @Query(value = "SELECT to_char(date_trunc('hour', son_tespit_zamani), 'YYYY-MM-DD HH24:00') as saat_dilimi, count(*) as adet " +
           "FROM tactical_comint " +
           "WHERE (:cagri IS NULL OR LOWER(cagri_adi) LIKE LOWER(CONCAT('%', :cagri, '%')) OR LOWER(karsi_cagri_adi) LIKE LOWER(CONCAT('%', :cagri, '%'))) " +
           "  AND son_tespit_zamani >= :baslangic AND son_tespit_zamani <= :bitis " +
           "GROUP BY date_trunc('hour', son_tespit_zamani) " +
           "ORDER BY date_trunc('hour', son_tespit_zamani) ASC", nativeQuery = true)
    List<Object[]> getHourlyTrafficTrend(
        @Param("cagri") String cagri,
        @Param("baslangic") Instant baslangic,
        @Param("bitis") Instant bitis
    );

    @Query("SELECT c.teshisKimlik, COUNT(c) FROM TacticalComintEntity c GROUP BY c.teshisKimlik")
    List<Object[]> getTeshisDistribution();

    @Query("SELECT c.haberlesmeSekli, COUNT(c) FROM TacticalComintEntity c GROUP BY c.haberlesmeSekli")
    List<Object[]> getHaberlesmeSekliDistribution();

    @Query("SELECT c.tip, COUNT(c) FROM TacticalComintEntity c GROUP BY c.tip")
    List<Object[]> getTipDistribution();

    @Query("SELECT c.lisan, COUNT(c) FROM TacticalComintEntity c GROUP BY c.lisan")
    List<Object[]> getLisanDistribution();

    @Query("SELECT c.veriKaynagi, COUNT(c) FROM TacticalComintEntity c GROUP BY c.veriKaynagi")
    List<Object[]> getVeriKaynagiDistribution();
    // ==========================================
    // HIYERARŞİK KESTİRİM VE LOB SORGULARI
    // ==========================================

    @Query(value = "SELECT " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'KESTIRIM_ELIPS') as total_elips, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'KESTIRIM_ELIPS' AND EXISTS (SELECT 1 FROM tactical_comint l WHERE l.kestirim_id = c.comint_id)) as childed_elips, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'KESTIRIM_ELIPS' AND NOT EXISTS (SELECT 1 FROM tactical_comint l WHERE l.kestirim_id = c.comint_id)) as childless_elips, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'HAM_LOB') as total_lob, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'HAM_LOB' AND kestirim_id IS NOT NULL) as bagli_lob, " +
           "  COUNT(*) FILTER (WHERE kayit_tipi = 'HAM_LOB' AND kestirim_id IS NULL) as basibos_lob " +
           "FROM tactical_comint c " +
           "WHERE c.son_tespit_zamani >= :baslangic AND c.son_tespit_zamani <= :bitis", nativeQuery = true)
    Object[] getComintHierarchyCountsWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis);

    @Query(value = "SELECT c.comint_id, c.cagri_adi, c.karsi_cagri_adi, c.teshis_kimlik, c.eh_unsuru, c.yon, c.yayin_enlem, c.yayin_boylam, c.yayin_semi_major_meters, c.yayin_semi_minor_meters, c.son_tespit_zamani, c.lisan, c.haberlesme_sekli, c.operator_notu, " +
           "       (SELECT COUNT(*) FROM tactical_comint l WHERE l.kestirim_id = c.comint_id) as child_count " +
           "FROM tactical_comint c " +
           "WHERE c.kayit_tipi = 'KESTIRIM_ELIPS' " +
           "  AND c.son_tespit_zamani >= :baslangic AND c.son_tespit_zamani <= :bitis " +
           "  AND EXISTS (SELECT 1 FROM tactical_comint l WHERE l.kestirim_id = c.comint_id) " +
           "ORDER BY c.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getComintChildedEllipsesWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT c.comint_id, c.cagri_adi, c.karsi_cagri_adi, c.teshis_kimlik, c.eh_unsuru, c.yon, c.yayin_enlem, c.yayin_boylam, c.yayin_semi_major_meters, c.yayin_semi_minor_meters, c.son_tespit_zamani, c.lisan, c.haberlesme_sekli, c.operator_notu, " +
           "       0 as child_count " +
           "FROM tactical_comint c " +
           "WHERE c.kayit_tipi = 'KESTIRIM_ELIPS' " +
           "  AND c.son_tespit_zamani >= :baslangic AND c.son_tespit_zamani <= :bitis " +
           "  AND NOT EXISTS (SELECT 1 FROM tactical_comint l WHERE l.kestirim_id = c.comint_id) " +
           "ORDER BY c.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getComintChildlessEllipsesWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT c.comint_id, c.cagri_adi, c.karsi_cagri_adi, c.teshis_kimlik, c.eh_unsuru, c.yon, c.yayin_enlem, c.yayin_boylam, c.yayin_semi_major_meters, c.yayin_semi_minor_meters, c.son_tespit_zamani, c.lisan, c.haberlesme_sekli, c.operator_notu, " +
           "       (SELECT COUNT(*) FROM tactical_comint l WHERE l.kestirim_id = c.comint_id) as child_count " +
           "FROM tactical_comint c " +
           "WHERE c.kayit_tipi = 'KESTIRIM_ELIPS' " +
           "  AND c.son_tespit_zamani >= :baslangic AND c.son_tespit_zamani <= :bitis " +
           "ORDER BY c.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getComintAllEllipsesWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT l.comint_id, l.cagri_adi, l.karsi_cagri_adi, l.teshis_kimlik, l.eh_unsuru, l.eh_unsuru_enlem, l.eh_unsuru_boylam, l.yon, l.min_frekans_mhz, l.son_tespit_zamani, l.lisan, l.haberlesme_sekli, l.operator_notu, " +
           "       l.kestirim_id " +
           "FROM tactical_comint l " +
           "WHERE l.kayit_tipi = 'HAM_LOB' " +
           "  AND l.son_tespit_zamani >= :baslangic AND l.son_tespit_zamani <= :bitis " +
           "  AND l.kestirim_id IS NULL " +
           "ORDER BY l.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getComintUnassociatedLobsWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT l.comint_id, l.cagri_adi, l.karsi_cagri_adi, l.teshis_kimlik, l.eh_unsuru, l.eh_unsuru_enlem, l.eh_unsuru_boylam, l.yon, l.min_frekans_mhz, l.son_tespit_zamani, l.lisan, l.haberlesme_sekli, l.operator_notu, " +
           "       l.kestirim_id " +
           "FROM tactical_comint l " +
           "WHERE l.kayit_tipi = 'HAM_LOB' " +
           "  AND l.son_tespit_zamani >= :baslangic AND l.son_tespit_zamani <= :bitis " +
           "  AND l.kestirim_id IS NOT NULL " +
           "ORDER BY l.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getComintAssociatedLobsWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT l.comint_id, l.cagri_adi, l.karsi_cagri_adi, l.teshis_kimlik, l.eh_unsuru, l.eh_unsuru_enlem, l.eh_unsuru_boylam, l.yon, l.min_frekans_mhz, l.son_tespit_zamani, l.lisan, l.haberlesme_sekli, l.operator_notu, " +
           "       l.kestirim_id " +
           "FROM tactical_comint l " +
           "WHERE l.kayit_tipi = 'HAM_LOB' " +
           "  AND l.son_tespit_zamani >= :baslangic AND l.son_tespit_zamani <= :bitis " +
           "ORDER BY l.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getComintAllLobsWithTimeRange(@Param("baslangic") Instant baslangic, @Param("bitis") Instant bitis, @Param("limit") int limit);

    @Query(value = "SELECT c.comint_id, c.kayit_tipi, c.yayin_enlem, c.yayin_boylam, " +
           "       c.yayin_semi_major_meters, c.yayin_semi_minor_meters, c.yayin_orientation_degrees, " +
           "       c.eh_unsuru_enlem, c.eh_unsuru_boylam, c.yon, " +
           "       c.teshis_kimlik, c.cagri_adi, c.eh_unsuru, " +
           "       c.son_tespit_zamani, c.kestirim_id, " +
           "       c.min_frekans_mhz, c.max_frekans_mhz " +
           "FROM tactical_comint c " +
           "ORDER BY c.son_tespit_zamani DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findMapComintsNative(@Param("limit") int limit);
}

