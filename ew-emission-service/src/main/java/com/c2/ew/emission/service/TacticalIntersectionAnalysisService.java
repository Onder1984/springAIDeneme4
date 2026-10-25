package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.TacticalIntersectionReportDto;
import com.c2.ew.emission.dto.TacticalIntersectionReportDto.FrequencyGroupDto;
import com.c2.ew.emission.dto.TacticalIntersectionReportDto.ZoneDetailDto;
import com.c2.ew.emission.dto.TacticalMapItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TacticalIntersectionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(TacticalIntersectionAnalysisService.class);
    private static final double METERS_PER_DEGREE_LAT = 111132.92;

    private final TacticalEmissionQueryService emissionQueryService;
    private final TacticalComintQueryService comintQueryService;

    public TacticalIntersectionAnalysisService(
            TacticalEmissionQueryService emissionQueryService,
            TacticalComintQueryService comintQueryService) {
        this.emissionQueryService = emissionQueryService;
        this.comintQueryService = comintQueryService;
    }

    public TacticalIntersectionReportDto analyzeIntersections(Integer minOverlaps, Integer zoneNumber) {
        int threshold = (minOverlaps != null && minOverlaps >= 2) ? minOverlaps : 3;
        List<TacticalMapItemDto> allEmissions = new ArrayList<>(emissionQueryService.getMapEmissions(1500));
        try {
            List<TacticalMapItemDto> comints = comintQueryService.getMapComints(1500);
            if (comints != null) {
                allEmissions.addAll(comints);
            }
        } catch (Exception ex) {
            log.warn("COMINT kestirimleri yüklenirken hata: {}", ex.getMessage());
        }

        // 1. Sadece geçerli kestirim elipslerini filtrele
        List<TacticalMapItemDto> ellipses = allEmissions.stream()
            .filter(e -> "KESTIRIM_ELIPS".equals(e.kayitTipi()) || (e.enlem() != null && e.sensorEnlem() == null))
            .filter(e -> e.enlem() != null && e.boylam() != null && e.semiMajor() != null && e.semiMinor() != null)
            .collect(Collectors.toList());

        // 2. Uzamsal yakınlık bileşenlerine ayır
        List<List<TacticalMapItemDto>> clusters = buildSpatialClusters(ellipses);

        // 3. Her kümede kesişim analizi yap ve eşiği aşan bölgeleri topla
        List<ZoneCandidate> candidates = new ArrayList<>();

        for (List<TacticalMapItemDto> cl : clusters) {
            ZoneCandidate candidate = evaluateClusterIntersection(cl, threshold);
            if (candidate != null && candidate.overlapCount >= threshold) {
                candidates.add(candidate);
            }
        }

        // 4. Dinamik Numaralandırma: Kesişen elips sayısına göre (ve ardından boylama göre) azalan sırala
        candidates.sort((a, b) -> {
            int cmp = Integer.compare(b.overlapCount, a.overlapCount);
            return cmp != 0 ? cmp : Double.compare(a.centerLon, b.centerLon);
        });

        List<ZoneDetailDto> zoneDtos = new ArrayList<>();
        int currentNum = 1;

        for (ZoneCandidate cand : candidates) {
            cand.zoneNumber = currentNum++;
            cand.zoneLabel = "BÖLGE #" + cand.zoneNumber;

            // Frekans aralıklarına göre grupla ve benzerlik puanla
            List<FrequencyGroupDto> freqGroups = groupAndScoreFrequencies(cand.coveredEmitters);

            zoneDtos.add(new ZoneDetailDto(
                cand.zoneNumber,
                cand.zoneLabel,
                cand.overlapCount,
                cand.centerLat,
                cand.centerLon,
                cand.coveredEmitters.size(),
                freqGroups
            ));
        }

        // 5. Belirli bir bölge numarası sorgulanmışsa sadece onu filtrele
        List<ZoneDetailDto> finalZones = zoneDtos;
        if (zoneNumber != null && zoneNumber > 0) {
            finalZones = zoneDtos.stream()
                .filter(z -> z.zoneNumber() == zoneNumber)
                .collect(Collectors.toList());
        }

        return new TacticalIntersectionReportDto(
            zoneDtos.size(),
            threshold,
            zoneNumber,
            finalZones
        );
    }

    private List<List<TacticalMapItemDto>> buildSpatialClusters(List<TacticalMapItemDto> ellipses) {
        List<List<TacticalMapItemDto>> clusters = new ArrayList<>();
        boolean[] visited = new boolean[ellipses.size()];

        for (int i = 0; i < ellipses.size(); i++) {
            if (visited[i]) continue;
            List<TacticalMapItemDto> cluster = new ArrayList<>();
            Queue<TacticalMapItemDto> queue = new LinkedList<>();

            visited[i] = true;
            cluster.add(ellipses.get(i));
            queue.add(ellipses.get(i));

            while (!queue.isEmpty()) {
                TacticalMapItemDto cur = queue.poll();
                double curMetersLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(cur.enlem()));

                for (int j = 0; j < ellipses.size(); j++) {
                    if (visited[j]) continue;
                    TacticalMapItemDto other = ellipses.get(j);
                    double dN = (cur.enlem() - other.enlem()) * METERS_PER_DEGREE_LAT;
                    double dE = (cur.boylam() - other.boylam()) * curMetersLon;
                    double dist = Math.sqrt(dN * dN + dE * dE);

                    if (dist <= (cur.semiMajor() + other.semiMajor())) {
                        visited[j] = true;
                        cluster.add(other);
                        queue.add(other);
                    }
                }
            }

            if (cluster.size() >= 2) {
                clusters.add(cluster);
            }
        }
        return clusters;
    }

    private ZoneCandidate evaluateClusterIntersection(List<TacticalMapItemDto> cluster, int minThreshold) {
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        for (TacticalMapItemDto el : cluster) {
            double metersLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(el.enlem()));
            double dLat = (el.semiMajor() * 1.1) / METERS_PER_DEGREE_LAT;
            double dLon = (el.semiMajor() * 1.1) / metersLon;
            minLat = Math.min(minLat, el.enlem() - dLat);
            maxLat = Math.max(maxLat, el.enlem() + dLat);
            minLon = Math.min(minLon, el.boylam() - dLon);
            maxLon = Math.max(maxLon, el.boylam() + dLon);
        }

        int res = 40;
        double latStep = (maxLat - minLat) / res;
        double lonStep = (maxLon - minLon) / res;

        int maxOverlap = 0;
        double sumLat = 0, sumLon = 0;
        int hitPointCount = 0;
        Set<String> hitEmitterIds = new HashSet<>();

        for (int i = 0; i <= res; i++) {
            double pLat = minLat + i * latStep;
            for (int j = 0; j <= res; j++) {
                double pLon = minLon + j * lonStep;
                int count = 0;
                List<String> currentHits = new ArrayList<>();

                for (TacticalMapItemDto el : cluster) {
                    double theta = Math.toRadians(el.azimuth() != null ? el.azimuth() : 0.0);
                    double cosT = Math.cos(theta);
                    double sinT = Math.sin(theta);
                    double metersLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(el.enlem()));

                    double dN = (pLat - el.enlem()) * METERS_PER_DEGREE_LAT;
                    double dE = (pLon - el.boylam()) * metersLon;
                    double locN = dN * cosT + dE * sinT;
                    double locE = -dN * sinT + dE * cosT;

                    double normDistSq = Math.pow(locN / Math.max(el.semiMajor(), 20.0), 2)
                                      + Math.pow(locE / Math.max(el.semiMinor(), 10.0), 2);
                    if (normDistSq <= 1.0) {
                        count++;
                        currentHits.add(el.id());
                    }
                }

                if (count >= minThreshold) {
                    sumLat += pLat;
                    sumLon += pLon;
                    hitPointCount++;
                    hitEmitterIds.addAll(currentHits);
                    if (count > maxOverlap) {
                        maxOverlap = count;
                    }
                }
            }
        }

        if (maxOverlap < minThreshold || hitPointCount == 0) {
            return null;
        }

        ZoneCandidate cand = new ZoneCandidate();
        cand.overlapCount = maxOverlap;
        cand.centerLat = sumLat / hitPointCount;
        cand.centerLon = sumLon / hitPointCount;
        cand.coveredEmitters = cluster.stream()
            .filter(e -> hitEmitterIds.contains(e.id()))
            .collect(Collectors.toList());

        return cand;
    }

    /**
     * Kesişim bölgesindeki radarları frekans aralıklarına göre gruplar ve grup içi benzerlik puanlar
     */
    /**
     * Kesişim bölgesindeki radarları ve muhabere yayınlarını frekans aralıklarına göre gruplar,
     * her bir yayın için yayın bazında küme uyum/benzerlik puanını hesaplar ve grup detaylarını üretir.
     */
    private List<FrequencyGroupDto> groupAndScoreFrequencies(List<TacticalMapItemDto> emitters) {
        Map<String, List<TacticalMapItemDto>> bandMap = new LinkedHashMap<>();

        for (TacticalMapItemDto em : emitters) {
            double freq = em.minFrekansMhz() != null ? em.minFrekansMhz() : 3000.0;
            boolean isComint = "MUHABERE".equalsIgnoreCase(em.disiplin());
            String bandName;

            if (isComint) {
                if (freq < 30.0) {
                    bandName = "HF Taktik Muhabere Telsiz Ağı (1.5 - 30.0 MHz)";
                } else if (freq < 300.0) {
                    bandName = "VHF Taktik Komuta Telsiz Ağı (30 - 300 MHz)";
                } else if (freq < 1000.0) {
                    bandName = "UHF Taktik Telsiz & Link Ağı (300 - 1000 MHz)";
                } else {
                    bandName = "SHF Uydu & Mikrodalga Muhabere Linki (> 1.0 GHz)";
                }
            } else {
                if (freq < 1000.0) {
                    bandName = "VHF/UHF Erken İhbar Radarları (< 1000 MHz)";
                } else if (freq < 2000.0) {
                    bandName = "L-Bant Gözetleme & Arama Radarları (1.0 - 2.0 GHz)";
                } else if (freq < 4000.0) {
                    bandName = "S-Bant Orta/Uzun Menzil Arama Radarları (2.0 - 4.0 GHz)";
                } else if (freq < 8000.0) {
                    bandName = "C-Bant Çok Fonksiyonlu Arama Radarları (4.0 - 8.0 GHz)";
                } else if (freq < 12000.0) {
                    bandName = "X-Bant Atış Kontrol & Aydınlatma Radarları (8.0 - 12.0 GHz)";
                } else {
                    bandName = "Ku/K-Bant Hassas Güdüm & Yakın Savunma Radarları (12.0+ GHz)";
                }
            }

            bandMap.computeIfAbsent(bandName, k -> new ArrayList<>()).add(em);
        }

        List<FrequencyGroupDto> result = new ArrayList<>();

        for (Map.Entry<String, List<TacticalMapItemDto>> entry : bandMap.entrySet()) {
            String bandName = entry.getKey();
            List<TacticalMapItemDto> groupEmitters = entry.getValue();

            double minF = groupEmitters.stream().mapToDouble(e -> e.minFrekansMhz() != null ? e.minFrekansMhz() : 0.0).min().orElse(0.0);
            double maxF = groupEmitters.stream().mapToDouble(e -> e.maxFrekansMhz() != null ? e.maxFrekansMhz() : minF).max().orElse(minF);
            String freqRangeStr = String.format(Locale.ROOT, "%.0f - %.0f MHz", minF, maxF);

            List<String> names = groupEmitters.stream()
                .map(e -> (e.hedefAdi() != null ? e.hedefAdi() : e.id()) + (e.hss() != null ? " (" + e.hss() + ")" : ""))
                .distinct()
                .collect(Collectors.toList());

            boolean isComint = "MUHABERE".equalsIgnoreCase(groupEmitters.get(0).disiplin());

            // 1. KÜME REFERANS DEĞERLERİNİ (CENTROID & HÂKİM PROFİL) HESAPLA
            double avgMinF = groupEmitters.stream().mapToDouble(e -> e.minFrekansMhz() != null ? e.minFrekansMhz() : minF).average().orElse(minF);
            double avgMaxF = groupEmitters.stream().mapToDouble(e -> e.maxFrekansMhz() != null ? e.maxFrekansMhz() : maxF).average().orElse(maxF);
            double refFreqMid = Math.round(((avgMinF + avgMaxF) / 2.0) * 10.0) / 10.0;
            String refFreqRange = String.format(Locale.ROOT, "%.0f - %.0f MHz", avgMinF, avgMaxF);

            // PRI Zarfı
            List<TacticalMapItemDto> priEmitters = groupEmitters.stream()
                .filter(e -> e.minPriMicroSec() != null || e.priMicroSec() != null).toList();
            Double avgMinPri = null;
            Double avgMaxPri = null;
            String refPriRange = "-";
            Double refPriMid = null;
            if (!priEmitters.isEmpty()) {
                avgMinPri = priEmitters.stream().mapToDouble(e -> e.minPriMicroSec() != null ? e.minPriMicroSec() : e.priMicroSec()).average().orElse(0.0);
                avgMaxPri = priEmitters.stream().mapToDouble(e -> e.maxPriMicroSec() != null ? e.maxPriMicroSec() : (e.minPriMicroSec() != null ? e.minPriMicroSec() : e.priMicroSec())).average().orElse(avgMinPri);
                refPriMid = Math.round(((avgMinPri + avgMaxPri) / 2.0) * 10.0) / 10.0;
                refPriRange = String.format(Locale.ROOT, "%.0f - %.0f µs", avgMinPri, avgMaxPri);
            }

            // PW Zarfı
            List<TacticalMapItemDto> pwEmitters = groupEmitters.stream()
                .filter(e -> e.minPwMicroSec() != null || e.pwMicroSec() != null).toList();
            Double avgMinPw = null;
            Double avgMaxPw = null;
            String refPwRange = "-";
            Double refPwMid = null;
            if (!pwEmitters.isEmpty()) {
                avgMinPw = pwEmitters.stream().mapToDouble(e -> e.minPwMicroSec() != null ? e.minPwMicroSec() : e.pwMicroSec()).average().orElse(0.0);
                avgMaxPw = pwEmitters.stream().mapToDouble(e -> e.maxPwMicroSec() != null ? e.maxPwMicroSec() : (e.minPwMicroSec() != null ? e.minPwMicroSec() : e.pwMicroSec())).average().orElse(avgMinPw);
                refPwMid = Math.round(((avgMinPw + avgMaxPw) / 2.0) * 100.0) / 100.0;
                refPwRange = String.format(Locale.ROOT, "%.2f - %.2f µs", avgMinPw, avgMaxPw);
            }

            // Hâkim Modülasyon, Platform ve Teşhis
            String refMod = findMode(groupEmitters.stream().map(TacticalMapItemDto::modulasyon).toList(), "PULSE_DOPPLER");
            String refPulse = findMode(groupEmitters.stream().map(TacticalMapItemDto::pulseCw).toList(), "PULSE");
            String refPol = findMode(groupEmitters.stream().map(TacticalMapItemDto::polarizasyon).toList(), "YATAY");
            String refPlat = findMode(groupEmitters.stream().map(TacticalMapItemDto::platformOrtami).toList(), "KARA");
            String refTeshis = findMode(groupEmitters.stream().map(TacticalMapItemDto::teshisKimlik).toList(), "DUSMAN");
            String refGorev = findMode(groupEmitters.stream().map(TacticalMapItemDto::radarGorevi).toList(), "-");

            // Ortalama Zaman Penceresi
            long avgEpoch = (long) groupEmitters.stream()
                .filter(e -> e.sonTespitZamani() != null)
                .mapToLong(e -> e.sonTespitZamani().toEpochMilli())
                .average().orElse(System.currentTimeMillis());

            // 2. HER YAYIN İÇİN 100 PUANLIK ÇOK KRİTERLİ MATRİS HESABI
            List<TacticalIntersectionReportDto.EmitterDetailDto> emitterDetails = new ArrayList<>();
            double totalGroupSim = 0.0;

            for (TacticalMapItemDto cur : groupEmitters) {
                double eMinF = cur.minFrekansMhz() != null ? cur.minFrekansMhz() : minF;
                double eMaxF = cur.maxFrekansMhz() != null ? cur.maxFrekansMhz() : eMinF;
                String eFreqStr = String.format(Locale.ROOT, "%.1f - %.1f MHz", eMinF, eMaxF);

                Double eMinP = cur.minPriMicroSec() != null ? cur.minPriMicroSec() : cur.priMicroSec();
                Double eMaxP = cur.maxPriMicroSec() != null ? cur.maxPriMicroSec() : eMinP;
                String ePriStr = (eMinP != null) ? String.format(Locale.ROOT, "%.1f - %.1f µs", eMinP, eMaxP) : "-";

                Double eMinW = cur.minPwMicroSec() != null ? cur.minPwMicroSec() : cur.pwMicroSec();
                Double eMaxW = cur.maxPwMicroSec() != null ? cur.maxPwMicroSec() : eMinW;
                String ePwStr = (eMinW != null) ? String.format(Locale.ROOT, "%.2f - %.2f µs", eMinW, eMaxW) : "-";

                double freqScore;
                Double priScore = null;
                Double pwScore = null;
                double waveformScore;
                double timeScore;
                double tacticalScore;

                if (!isComint) {
                    // --- RADAR 100 PUANLIK MATRİSİ ---
                    // 1. Frekans Zarfı IoU (25 Puan)
                    double iouF = calculateIntervalIoU(eMinF, eMaxF, avgMinF, avgMaxF);
                    freqScore = Math.round(iouF * 250.0) / 10.0;

                    // 2. PRI Zarfı IoU (15 Puan)
                    if (eMinP != null && avgMinPri != null) {
                        double iouP = calculateIntervalIoU(eMinP, eMaxP, avgMinPri, avgMaxPri);
                        priScore = Math.round(iouP * 150.0) / 10.0;
                    }

                    // 3. PW Zarfı IoU (10 Puan)
                    if (eMinW != null && avgMinPw != null) {
                        double iouW = calculateIntervalIoU(eMinW, eMaxW, avgMinPw, avgMaxPw);
                        pwScore = Math.round(iouW * 100.0) / 10.0;
                    }

                    // 4. Dalga Biçimi & Modülasyon (15 Puan: MOP 7P, Pulse/CW 5P, Pol 3P)
                    double sMop = matchStrings(cur.modulasyon(), refMod) ? 7.0 : 0.0;
                    double sPulse = matchStrings(cur.pulseCw(), refPulse) ? 5.0 : 0.0;
                    double sPol = matchStrings(cur.polarizasyon(), refPol) ? 3.0 : 0.0;
                    waveformScore = Math.round((sMop + sPulse + sPol) * 10.0) / 10.0;

                    // 5. Zaman Yakınlığı (15 Puan)
                    timeScore = calculateTimeScore(cur.sonTespitZamani(), avgEpoch, 15.0);

                    // 6. Taktiksel & Platform Özellikleri (20 Puan: Teşhis 8P, Platform 7P, Görev 5P)
                    double sTeshis = matchStrings(cur.teshisKimlik(), refTeshis) ? 8.0 : 0.0;
                    double sPlat = matchStrings(cur.platformOrtami(), refPlat) ? 7.0 : 0.0;
                    double sGorev = matchStrings(cur.radarGorevi(), refGorev) ? 5.0 : 0.0;
                    tacticalScore = Math.round((sTeshis + sPlat + sGorev) * 10.0) / 10.0;

                } else {
                    // --- COMINT (MUHABERE) 100 PUANLIK MATRİSİ ---
                    // 1. Frekans Zarfı IoU (40 Puan)
                    double iouF = calculateIntervalIoU(eMinF, eMaxF, avgMinF, avgMaxF);
                    freqScore = Math.round(iouF * 400.0) / 10.0;

                    // 2. Modülasyon Uyumu (20 Puan)
                    waveformScore = matchStrings(cur.modulasyon(), refMod) ? 20.0 : 5.0;

                    // 3. Zaman Yakınlığı (20 Puan)
                    timeScore = calculateTimeScore(cur.sonTespitZamani(), avgEpoch, 20.0);

                    // 4. Teşhis ve Platform (20 Puan: Teşhis 10P, Platform 10P)
                    double sTeshis = matchStrings(cur.teshisKimlik(), refTeshis) ? 10.0 : 0.0;
                    double sPlat = matchStrings(cur.platformOrtami(), refPlat) ? 10.0 : 0.0;
                    tacticalScore = Math.round((sTeshis + sPlat) * 10.0) / 10.0;
                }

                double totalScore = freqScore
                    + (priScore != null ? priScore : 0.0)
                    + (pwScore != null ? pwScore : 0.0)
                    + waveformScore
                    + timeScore
                    + tacticalScore;
                totalScore = Math.round(Math.min(100.0, Math.max(10.0, totalScore)) * 10.0) / 10.0;
                totalGroupSim += totalScore;

                emitterDetails.add(new TacticalIntersectionReportDto.EmitterDetailDto(
                    cur.id(),
                    cur.hedefAdi() != null ? cur.hedefAdi() : cur.id(),
                    cur.disiplin() != null ? cur.disiplin() : "RADAR",
                    eFreqStr,
                    ePriStr,
                    ePwStr,
                    cur.minFrekansMhz(),
                    cur.maxFrekansMhz(),
                    eMinP,
                    eMaxP,
                    eMinW,
                    eMaxW,
                    cur.modulasyon() != null ? cur.modulasyon() : "-",
                    cur.platformOrtami() != null ? cur.platformOrtami() : "-",
                    cur.teshisKimlik() != null ? cur.teshisKimlik() : "-",
                    cur.sonTespitZamaniFormatted() != null ? cur.sonTespitZamaniFormatted() : "-",
                    freqScore,
                    priScore,
                    pwScore,
                    waveformScore,
                    timeScore,
                    tacticalScore,
                    totalScore,
                    cur.hss()
                ));
            }

            emitterDetails.sort((a, b) -> Double.compare(b.similarityScore(), a.similarityScore()));

            double avgSimilarity = groupEmitters.size() > 0 ? (totalGroupSim / groupEmitters.size()) : 100.0;
            avgSimilarity = Math.round(avgSimilarity * 10.0) / 10.0;

            result.add(new FrequencyGroupDto(
                bandName,
                freqRangeStr,
                groupEmitters.size(),
                refFreqMid,
                refPriMid,
                refPwMid,
                refFreqRange,
                refPriRange,
                refPwRange,
                refMod,
                refPlat,
                refTeshis,
                names,
                emitterDetails,
                avgSimilarity
            ));
        }

        return result;
    }

    private double calculateIntervalIoU(Double min1, Double max1, Double min2, Double max2) {
        if (min1 == null || max1 == null || min2 == null || max2 == null) return 0.5;
        double aMin = Math.min(min1, max1);
        double aMax = Math.max(min1, max1);
        double bMin = Math.min(min2, max2);
        double bMax = Math.max(min2, max2);

        double interMin = Math.max(aMin, bMin);
        double interMax = Math.min(aMax, bMax);
        double intersection = Math.max(0.0, interMax - interMin);
        double union = Math.max(1e-6, Math.max(aMax, bMax) - Math.min(aMin, bMin));

        if (intersection > 0.0) {
            return Math.min(1.0, intersection / union);
        } else {
            double aMid = (aMin + aMax) / 2.0;
            double bMid = (bMin + bMax) / 2.0;
            double gap = Math.abs(aMid - bMid);
            double spread = Math.max(union, 1.0);
            return Math.max(0.15, 1.0 - (gap / (spread * 2.0)));
        }
    }

    private double calculateTimeScore(Instant t, long refEpoch, double maxPoints) {
        if (t == null) return maxPoints * 0.5;
        double diffSec = Math.abs(t.toEpochMilli() - refEpoch) / 1000.0;
        if (diffSec <= 300.0) return maxPoints;
        if (diffSec <= 1800.0) return maxPoints * 0.85;
        if (diffSec <= 7200.0) return maxPoints * 0.65;
        if (diffSec <= 21600.0) return maxPoints * 0.45;
        if (diffSec <= 86400.0) return maxPoints * 0.25;
        return maxPoints * 0.10;
    }

    private String findMode(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) return fallback;
        Map<String, Long> counts = values.stream()
            .filter(v -> v != null && !v.isBlank() && !"-".equals(v.trim()))
            .collect(Collectors.groupingBy(v -> v.toUpperCase(Locale.ROOT).trim(), Collectors.counting()));
        if (counts.isEmpty()) return fallback;
        return counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(fallback);
    }

    private boolean matchStrings(String s1, String s2) {
        if (s1 == null || s2 == null) return false;
        return s1.trim().equalsIgnoreCase(s2.trim());
    }

    private static class ZoneCandidate {
        int zoneNumber;
        String zoneLabel;
        int overlapCount;
        double centerLat;
        double centerLon;
        List<TacticalMapItemDto> coveredEmitters = new ArrayList<>();
    }
}
