package manga.dto;

import java.util.List;

/**
 * DTO for version comparison result.
 * Contains differences between two manuscript versions.
 */
public class VersionComparisonDTO {
    private Long version1Id;
    private Long version2Id;
    private Integer version1Number;
    private Integer version2Number;
    private List<PageComparisonDTO> addedPages;
    private List<PageComparisonDTO> removedPages;
    private List<PageComparisonDTO> changedPages;
    private List<PageComparisonDTO> reorderedPages;

    public Long getVersion1Id() {
        return version1Id;
    }

    public void setVersion1Id(Long version1Id) {
        this.version1Id = version1Id;
    }

    public Long getVersion2Id() {
        return version2Id;
    }

    public void setVersion2Id(Long version2Id) {
        this.version2Id = version2Id;
    }

    public Integer getVersion1Number() {
        return version1Number;
    }

    public void setVersion1Number(Integer version1Number) {
        this.version1Number = version1Number;
    }

    public Integer getVersion2Number() {
        return version2Number;
    }

    public void setVersion2Number(Integer version2Number) {
        this.version2Number = version2Number;
    }

    public List<PageComparisonDTO> getAddedPages() {
        return addedPages;
    }

    public void setAddedPages(List<PageComparisonDTO> addedPages) {
        this.addedPages = addedPages;
    }

    public List<PageComparisonDTO> getRemovedPages() {
        return removedPages;
    }

    public void setRemovedPages(List<PageComparisonDTO> removedPages) {
        this.removedPages = removedPages;
    }

    public List<PageComparisonDTO> getChangedPages() {
        return changedPages;
    }

    public void setChangedPages(List<PageComparisonDTO> changedPages) {
        this.changedPages = changedPages;
    }

    public List<PageComparisonDTO> getReorderedPages() {
        return reorderedPages;
    }

    public void setReorderedPages(List<PageComparisonDTO> reorderedPages) {
        this.reorderedPages = reorderedPages;
    }
}
