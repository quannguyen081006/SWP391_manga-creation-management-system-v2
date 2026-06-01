package manga.dto;

/**
 * DTO for adding page to manuscript requests.
 */
public class AddPageRequestDTO {
    private Long chapterImageId;
    private Integer displayOrder;
    private Integer pageNumber;

    // Getters and Setters
    public Long getChapterImageId() {
        return chapterImageId;
    }

    public void setChapterImageId(Long chapterImageId) {
        this.chapterImageId = chapterImageId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }
}
