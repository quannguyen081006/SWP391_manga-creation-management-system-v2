package manga.dto;

/**
 * DTO for responsible people associated with a manuscript workspace.
 * Contains information about the mangaka, tantou editor, and editorial board.
 */
public class ResponsiblePeopleDTO {
    private String mangakaName;
    private String mangakaEmail;
    private String tantouEditorName;
    private String tantouEditorEmail;
    private String editorialBoardName;

    public String getMangakaName() {
        return mangakaName;
    }

    public void setMangakaName(String mangakaName) {
        this.mangakaName = mangakaName;
    }

    public String getMangakaEmail() {
        return mangakaEmail;
    }

    public void setMangakaEmail(String mangakaEmail) {
        this.mangakaEmail = mangakaEmail;
    }

    public String getTantouEditorName() {
        return tantouEditorName;
    }

    public void setTantouEditorName(String tantouEditorName) {
        this.tantouEditorName = tantouEditorName;
    }

    public String getTantouEditorEmail() {
        return tantouEditorEmail;
    }

    public void setTantouEditorEmail(String tantouEditorEmail) {
        this.tantouEditorEmail = tantouEditorEmail;
    }

    public String getEditorialBoardName() {
        return editorialBoardName;
    }

    public void setEditorialBoardName(String editorialBoardName) {
        this.editorialBoardName = editorialBoardName;
    }
}
