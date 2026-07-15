package manga.service;

import manga.common.exception.BusinessRuleException;
import manga.dto.RankingCsvRow;
import manga.model.AuthenticatedUser;
import manga.model.RankingCsvUpload;
import manga.repository.RankingCsvUploadRepository;
import manga.repository.RankingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RankingCsvImportService {

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private RankingCsvUploadRepository rankingCsvUploadRepository;

    @Transactional
    public int importCsv(long periodId, MultipartFile file, AuthenticatedUser user) {
        if (user == null || (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD"))) {
            throw new BusinessRuleException("Only ADMIN or EDITORIAL_BOARD can upload ranking CSV");
        }

        Map<String, Object> period = rankingRepository.findPeriodById(periodId);
        if (!"OPEN".equalsIgnoreCase((String) period.get("status"))) {
            throw new BusinessRuleException("CSV upload is only allowed for OPEN ranking periods");
        }
        // BR-RNK-01: Validate current date is within period date range
        java.sql.Date startDate = (java.sql.Date) period.get("startDate");
        java.sql.Date endDate = (java.sql.Date) period.get("endDate");

        long now = System.currentTimeMillis();

// 00:00:00.000 của ngày bắt đầu
        long startMillis = startDate.getTime();

// 23:59:59.999 của ngày kết thúc
        long endMillis = endDate.getTime() + (24 * 60 * 60 * 1000L) - 1;

        if (now < startMillis || now > endMillis) {
            throw new BusinessRuleException(
                    "CSV upload only allowed during active period dates (BR-RNK-01)"
            );
        }

        // BR-RNK-06: Reject if board member already submitted entries for this period
        if (rankingRepository.hasSubmittedEntries(periodId, user.getId())) {
            throw new BusinessRuleException("You have already submitted vote entries for this period. Duplicate submission is not allowed (BR-RNK-06)");
        }
        List<RankingCsvRow> rows = parseAndValidate(file);
        rankingRepository.replaceCsvEntries(periodId, user.getId(), rows);

        // Save CSV metadata for preview feature
        String csvContent = readFileContent(file);
        RankingCsvUpload csvUpload = new RankingCsvUpload(periodId, user.getId(), file.getOriginalFilename(), csvContent);
        rankingCsvUploadRepository.saveOrUpdate(csvUpload);

        return rows.size();
    }

    private String readFileContent(MultipartFile file) {
        try ( BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (Exception ex) {
            throw new BusinessRuleException("Failed to read CSV file content: " + ex.getMessage());
        }
    }

    private List<RankingCsvRow> parseAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("CSV file is required");
        }

        List<RankingCsvRow> rows = new ArrayList<RankingCsvRow>();
        Set<Long> seenSeriesIds = new HashSet<Long>();

        try ( BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String header = reader.readLine();
            if (header == null || !header.trim().replaceAll("\\s+", "").equalsIgnoreCase("series_id,series_name,mangaka_id,likes,reads,revenue")) {
                throw new BusinessRuleException("Invalid CSV header. Expected: series_id,series_name,mangaka_id,likes,reads,revenue");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                RankingCsvRow row = parseRow(line, lineNumber);
                if (!seenSeriesIds.add(row.getSeriesId())) {
                    throw new BusinessRuleException("Line " + lineNumber + ": duplicate series_id " + row.getSeriesId());
                }
                if (!rankingRepository.seriesExists(row.getSeriesId())) {
                    throw new BusinessRuleException("Line " + lineNumber + ": series_id " + row.getSeriesId() + " does not exist");
                }
                long dbMangakaId = rankingRepository.getSeriesMangakaId(row.getSeriesId());
                if (dbMangakaId != row.getMangakaId()) {
                    throw new BusinessRuleException("Line " + lineNumber + ": mangaka_id " + row.getMangakaId() + " does not match series owner in database (which is " + dbMangakaId + ")");
                }
                rows.add(row);
            }
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessRuleException("Failed to read ranking CSV: " + ex.getMessage());
        }

        if (rows.isEmpty()) {
            throw new BusinessRuleException("CSV must contain at least one ranking row");
        }
        return rows;
    }

    private RankingCsvRow parseRow(String line, int lineNumber) {
        String[] parts = line.split(",");
        if (parts.length != 6) {
            throw new BusinessRuleException("Line " + lineNumber + ": expected 6 columns (series_id, series_name, mangaka_id, likes, reads, revenue)");
        }

        RankingCsvRow row = new RankingCsvRow();
        try {
            row.setSeriesId(Long.parseLong(parts[0].trim()));
            row.setSeriesTitle(parts[1].trim());
            row.setMangakaId(Long.parseLong(parts[2].trim()));
            row.setVoteCount(Integer.parseInt(parts[3].trim()));
            row.setReaderCount(Integer.parseInt(parts[4].trim()));
            row.setRevenue(new java.math.BigDecimal(parts[5].trim()));
        } catch (NumberFormatException ex) {
            throw new BusinessRuleException("Line " + lineNumber + ": invalid numeric value");
        }

        if (row.getSeriesId() < 0) {
            throw new BusinessRuleException("Line " + lineNumber + ": series_id cannot be negative");
        }
        if (row.getSeriesTitle().length() == 0) {
            throw new BusinessRuleException("Line " + lineNumber + ": series_name is required");
        }
        if (row.getMangakaId() < 0) {
            throw new BusinessRuleException("Line " + lineNumber + ": mangaka_id cannot be negative");
        }
        if (row.getVoteCount() < 0) {
            throw new BusinessRuleException("Line " + lineNumber + ": likes cannot be negative");
        }
        if (row.getReaderCount() <= 0) {
            throw new BusinessRuleException("Line " + lineNumber + ": reads must be greater than 0");
        }
        if (row.getVoteCount() > row.getReaderCount()) {
            throw new BusinessRuleException("Line " + lineNumber + ": likes cannot exceed reads");
        }
        if (row.getRevenue() == null || row.getRevenue().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Line " + lineNumber + ": revenue cannot be negative");
        }
        return row;
    }
}
