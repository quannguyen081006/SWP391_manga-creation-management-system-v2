package manga.service;

import manga.model.AuthenticatedUser;
import manga.model.SeriesSummary;
import manga.model.chaptertask.ChapterSummary;
import manga.model.chaptertask.TaskSummary;
import manga.model.ManuscriptSummary;
import manga.repository.ProductionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@Service
public class ProductionService {

    @Autowired
    private ProductionRepository productionRepository;

    public List<SeriesSummary> listSeries(AuthenticatedUser user) {
        return productionRepository.listSeries(user);
    }

    public List<SeriesSummary> listSeries() {
        return productionRepository.listSeries();
    }

    public List<TaskSummary> listTasks() {
        return productionRepository.listTasks();
    }

    public List<ManuscriptSummary> listManuscripts(AuthenticatedUser user, Long seriesId) {
        return productionRepository.listManuscripts(user, seriesId);
    }

    public List<ManuscriptSummary> listManuscripts() {
        return productionRepository.listManuscripts();
    }

    public List<ChapterSummary> listChapters() {
        return productionRepository.listChapters();
    }

    public long findSeriesOwnerMangaka(long seriesId) {
        return productionRepository.findSeriesOwnerMangaka(seriesId);
    }

    public long findSeriesTantou(long seriesId) {
        return productionRepository.findSeriesTantou(seriesId);
    }

    public List<Map<String, Object>> listMangakaAssistantsBySeries(long seriesId) {
        return productionRepository.listMangakaAssistantsBySeries(seriesId);
    }

    public void updateSeriesDeadline(long seriesId, long tantouEditorId, Date publicationDate) {
        productionRepository.updateSeriesDeadline(seriesId, tantouEditorId, publicationDate);
    }

    public void enrollAssistant(long seriesId, long assistantId) {
        productionRepository.enrollAssistant(seriesId, assistantId);
    }

    public void removeAssistant(long seriesId, long assistantId) {
        productionRepository.removeAssistant(seriesId, assistantId);
    }

    public Map<String, Object> getSeriesTeam(long seriesId) {
        return productionRepository.getSeriesTeam(seriesId);
    }
}
