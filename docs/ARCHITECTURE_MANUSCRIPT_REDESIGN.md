# Manuscript + Annotation Module Architecture Redesign

## Executive Summary

This document presents a comprehensive architectural redesign of the Manuscript and Annotation modules to transform them from a simple file-upload system into a professional collaborative manga editorial review platform with visual inline annotation, immutable manuscript versioning, and a fully traceable editorial approval pipeline.

---

## 1. Current System Analysis

### 1.1 Current Architecture

**Manuscript Table Structure:**
- File-based storage (PDF, ZIP, RAR, CBZ)
- Versioning support (DRAFT → SUBMITTED → UNDER_REVIEW → REVISION_REQUIRED → APPROVED/REJECTED)
- Simple metadata (fileUrl, fileSize, fileExtension, notes, genre)
- Circular dependency: requires Chapter COMPLETE status

**Annotation Table Structure:**
- Simple annotations linked to manuscriptId
- Basic fields: pageNumber, category, status, content
- No coordinate anchoring
- No thread/reply support
- No severity levels

**Current Workflow Issues:**
1. **Circular Dependency**: Manuscript requires Chapter COMPLETE, but Chapter lifecycle ends at EDITORIAL_REVIEW
2. **File-Based**: Manuscript is a single file upload, not a visual workspace
3. **Mutable Assets**: Direct references to task assets, no immutable snapshots
4. **Limited Annotations**: No coordinate anchoring, threads, or severity
5. **No Production Lock**: Tasks can be modified during manuscript review

### 1.2 Current Business Rules (Preserved)

- BR-23: Only COMPLETE chapters can submit manuscript
- BR-38: Cannot enter editorial review if chapter has unapproved pages
- BR-40: Reject/revision requires feedback
- BR-41: Annotations bind to manuscript version
- Version validation (only current version can be edited/submitted)
- Role-based access (Mangaka submits, Tantou reviews)

---

## 2. New Domain Model & Aggregate Boundaries

### 2.1 Core Aggregates

```
┌─────────────────────────────────────────────────────────────────┐
│                    Chapter Aggregate                             │
│  (Immutable - Production Workflow)                               │
│  - Chapter entity with status lifecycle                           │
│  - PageTask entities (production tasks)                          │
│  - ChapterImage entities (production assets)                     │
│  Status: DRAFT → IN_PROGRESS → EDITORIAL_REVIEW → COMPLETE      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ BR-1: Only EDITORIAL_REVIEW can create manuscript
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              Manuscript Aggregate (Root)                          │
│  (Versioned - Editorial Review Workspace)                         │
│  - ManuscriptVersion entity (root)                                │
│  - ManuscriptPage entities (immutable snapshots)                 │
│  - Annotation entities (bound to version)                        │
│  Status: DRAFT → UNDER_REVIEW → APPROVED/REJECTED                │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Entity Definitions

#### ManuscriptVersion (Aggregate Root)
```java
@Entity
@Table(name = "ManuscriptVersion")
public class ManuscriptVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long chapterId;
    
    @Column(nullable = false)
    private Integer version;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManuscriptStatus status;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime submittedAt;
    
    @Column
    private LocalDateTime approvedAt;
    
    @Column
    private LocalDateTime rejectedAt;
    
    @Column
    private String feedback;
    
    @Column
    private String revisionNotes;
    
    @Column
    private Integer totalPageCount;
    
    @OneToMany(mappedBy = "manuscriptVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ManuscriptPage> pages = new ArrayList<>();
    
    @OneToMany(mappedBy = "manuscriptVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Annotation> annotations = new ArrayList<>();
    
    // BR-2: Only one UNDER_REVIEW per chapter
    // BR-3: REJECTED cannot be mutated
    // BR-4: APPROVED cannot be mutated
}
```

#### ManuscriptPage (Immutable Snapshot)
```java
@Entity
@Table(name = "ManuscriptPage")
public class ManuscriptPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long manuscriptVersionId;
    
    @Column(nullable = false)
    private Integer displayOrder;
    
    @Column(nullable = false)
    private String snapshotFileUrl;
    
    @Column(nullable = false)
    private String originalFileUrl;
    
    @Column
    private Long sourceChapterImageId;
    
    @Column
    private Long sourcePageTaskId;
    
    @Column(nullable = false)
    private Integer pageNumber;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime snapshotCreatedAt;
    
    @Column(nullable = false, updatable = false)
    private String snapshotChecksum;
    
    // BR-7: Immutable snapshot of production asset
    // BR-9: Production assets locked during review
}
```

#### Annotation (Rich Editorial Feedback)
```java
@Entity
@Table(name = "Annotation")
public class Annotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long manuscriptVersionId;
    
    @Column(nullable = false)
    private Long editorId;
    
    @Column(nullable = false)
    private Integer pageNumber;
    
    // Coordinate anchoring (BR-8: responsive scaling)
    @Column(nullable = false)
    private Double xPercent;
    
    @Column(nullable = false)
    private Double yPercent;
    
    @Column(nullable = false)
    private Double widthPercent;
    
    @Column(nullable = false)
    private Double heightPercent;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnnotationCategory category;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnnotationSeverity severity;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnnotationStatus status;
    
    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String content;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime resolvedAt;
    
    @Column
    private Long resolvedBy;
    
    @Column
    private Long parentAnnotationId;
    
    @OneToMany(mappedBy = "parentAnnotation", cascade = CascadeType.ALL)
    private List<Annotation> replies = new ArrayList<>();
    
    // BR-5: Annotations belong to specific manuscript version
    // BR-8: Coordinates scale responsively
}
```

### 2.3 Enums

```java
public enum ManuscriptStatus {
    DRAFT,           // Mangaka creating workspace
    UNDER_REVIEW,    // Tantou reviewing (BR-2: only one per chapter)
    APPROVED,        // Ready for publish
    REJECTED,        // Requires revision (BR-3: immutable)
    ARCHIVED         // Historical record
}

public enum AnnotationCategory {
    ART, STORY, PACING, DIALOGUE, PANELING, TYPOGRAPHY, OTHER
}

public enum AnnotationSeverity {
    CRITICAL, HIGH, MEDIUM, LOW, SUGGESTION
}

public enum AnnotationStatus {
    OPEN, IN_PROGRESS, RESOLVED, DISMISSED
}
```

---

## 3. New Database Schema

### 3.1 ManuscriptVersion Table

```sql
CREATE TABLE [dbo].[ManuscriptVersion](
    [id] [bigint] IDENTITY(1,1) NOT NULL,
    [chapterId] [bigint] NOT NULL,
    [version] [int] NOT NULL,
    [status] [varchar](20) NOT NULL,
    [createdAt] [datetime] NOT NULL DEFAULT GETDATE(),
    [submittedAt] [datetime] NULL,
    [approvedAt] [datetime] NULL,
    [rejectedAt] [datetime] NULL,
    [feedback] [nvarchar](max) NULL,
    [revisionNotes] [nvarchar](max) NULL,
    [totalPageCount] [int] NULL,
    CONSTRAINT [PK_ManuscriptVersion] PRIMARY KEY CLUSTERED ([id] ASC),
    CONSTRAINT [UQ_ManuscriptVersion_Chapter_Version] UNIQUE ([chapterId], [version])
)
GO

CREATE INDEX [IX_ManuscriptVersion_Chapter_Status] 
ON [dbo].[ManuscriptVersion]([chapterId], [status])
GO

CREATE INDEX [IX_ManuscriptVersion_Chapter_Version] 
ON [dbo].[ManuscriptVersion]([chapterId], [version] DESC)
GO
```

### 3.2 ManuscriptPage Table (Immutable Snapshots)

```sql
CREATE TABLE [dbo].[ManuscriptPage](
    [id] [bigint] IDENTITY(1,1) NOT NULL,
    [manuscriptVersionId] [bigint] NOT NULL,
    [displayOrder] [int] NOT NULL,
    [snapshotFileUrl] [varchar](512) NOT NULL,
    [originalFileUrl] [varchar](512) NOT NULL,
    [sourceChapterImageId] [bigint] NULL,
    [sourcePageTaskId] [bigint] NULL,
    [pageNumber] [int] NOT NULL,
    [snapshotCreatedAt] [datetime] NOT NULL DEFAULT GETDATE(),
    [snapshotChecksum] [varchar](64) NOT NULL,
    CONSTRAINT [PK_ManuscriptPage] PRIMARY KEY CLUSTERED ([id] ASC),
    CONSTRAINT [FK_ManuscriptPage_ManuscriptVersion] 
        FOREIGN KEY ([manuscriptVersionId]) REFERENCES [ManuscriptVersion]([id]),
    CONSTRAINT [CK_ManuscriptPage_Immutable] 
        CHECK (snapshotCreatedAt = snapshotCreatedAt) -- Logical immutability
)
GO

CREATE INDEX [IX_ManuscriptPage_ManuscriptVersion_DisplayOrder] 
ON [dbo].[ManuscriptPage]([manuscriptVersionId], [displayOrder] ASC)
GO
```

### 3.3 Annotation Table (Rich Coordinates)

```sql
CREATE TABLE [dbo].[Annotation](
    [id] [bigint] IDENTITY(1,1) NOT NULL,
    [manuscriptVersionId] [bigint] NOT NULL,
    [editorId] [bigint] NOT NULL,
    [pageNumber] [int] NOT NULL,
    [xPercent] [decimal](5,2) NOT NULL,
    [yPercent] [decimal](5,2) NOT NULL,
    [widthPercent] [decimal](5,2) NOT NULL,
    [heightPercent] [decimal](5,2) NOT NULL,
    [category] [varchar](30) NOT NULL,
    [severity] [varchar](20) NOT NULL,
    [status] [varchar](20) NOT NULL,
    [content] [nvarchar](max) NOT NULL,
    [createdAt] [datetime] NOT NULL DEFAULT GETDATE(),
    [resolvedAt] [datetime] NULL,
    [resolvedBy] [bigint] NULL,
    [parentAnnotationId] [bigint] NULL,
    CONSTRAINT [PK_Annotation] PRIMARY KEY CLUSTERED ([id] ASC),
    CONSTRAINT [FK_Annotation_ManuscriptVersion] 
        FOREIGN KEY ([manuscriptVersionId]) REFERENCES [ManuscriptVersion]([id]),
    CONSTRAINT [FK_Annotation_Parent] 
        FOREIGN KEY ([parentAnnotationId]) REFERENCES [Annotation]([id]),
    CONSTRAINT [CK_Annotation_Coordinates] 
        CHECK (xPercent >= 0 AND xPercent <= 100 
               AND yPercent >= 0 AND yPercent <= 100
               AND widthPercent > 0 AND widthPercent <= 100
               AND heightPercent > 0 AND heightPercent <= 100)
)
GO

CREATE INDEX [IX_Annotation_ManuscriptVersion_Page] 
ON [dbo].[Annotation]([manuscriptVersionId], [pageNumber] ASC)
GO

CREATE INDEX [IX_Annotation_Editor] 
ON [dbo].[Annotation]([editorId])
GO
```

### 3.4 Production Lock Table (BR-9)

```sql
CREATE TABLE [dbo].[ManuscriptProductionLock](
    [id] [bigint] IDENTITY(1,1) NOT NULL,
    [chapterId] [bigint] NOT NULL,
    [manuscriptVersionId] [bigint] NOT NULL,
    [lockedAt] [datetime] NOT NULL DEFAULT GETDATE(),
    [lockedBy] [bigint] NOT NULL,
    [unlockedAt] [datetime] NULL,
    CONSTRAINT [PK_ManuscriptProductionLock] PRIMARY KEY CLUSTERED ([id] ASC),
    CONSTRAINT [UQ_ManuscriptProductionLock_Chapter] UNIQUE ([chapterId]),
    CONSTRAINT [FK_ManuscriptProductionLock_ManuscriptVersion] 
        FOREIGN KEY ([manuscriptVersionId]) REFERENCES [ManuscriptVersion]([id])
)
GO
```

---

## 4. State Machine Design

### 4.1 Manuscript Lifecycle State Machine

```
                    ┌─────────────┐
                    │   DRAFT     │
                    │ (Mangaka)   │
                    └──────┬──────┘
                           │
                           │ submitForReview()
                           │ (BR-1: Chapter must be EDITORIAL_REVIEW)
                           │ (BR-2: No other UNDER_REVIEW exists)
                           ▼
                    ┌─────────────┐
                    │UNDER_REVIEW │
                    │ (Tantou)    │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           │ approve()     │ reject()      │ requestRevision()
           │               │               │
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │  APPROVED   │ │  REJECTED   │ │  REJECTED   │
    │(Immutable)  │ │(Immutable)  │ │(Immutable)  │
    └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
           │               │               │
           │               │               │ createNewVersion()
           │               │               │ (BR-3: Cannot mutate)
           │               │               ▼
           │               │         ┌─────────────┐
           │               │         │   DRAFT     │
           │               │         │ (v2, v3...)  │
           │               │         └─────────────┘
           │               │
           │               └──→ Mangaka fixes → createNewVersion()
           │
           ▼
    ┌─────────────┐
    │   PUBLISH   │
    │ (Chapter)   │
    └─────────────┘
```

### 4.2 Annotation State Machine

```
┌─────────┐
│  OPEN   │
└────┬────┘
     │
     │ resolve()
     ▼
┌─────────┐
│RESOLVED │
└─────────┘
```

### 4.3 Chapter Lifecycle (Reinterpreted)

```
DRAFT → IN_PROGRESS → EDITORIAL_REVIEW → COMPLETE → PUBLISHED
                         │
                         │ (Production Complete)
                         │ (All tasks approved)
                         │
                         ▼
                    Can create Manuscript
```

---

## 5. Annotation Coordinate Model

### 5.1 Coordinate System

**Percentage-Based Coordinates (BR-8):**
- All coordinates stored as percentages (0-100)
- Scales responsively across different screen sizes
- Independent of image resolution

```java
public class AnnotationCoordinates {
    private Double xPercent;      // Left position (0-100)
    private Double yPercent;      // Top position (0-100)
    private Double widthPercent;  // Width (0-100)
    private Double heightPercent; // Height (0-100)
    
    // Convert to pixel coordinates for rendering
    public PixelCoordinates toPixels(int imageWidth, int imageHeight) {
        return new PixelCoordinates(
            (int) (xPercent / 100 * imageWidth),
            (int) (yPercent / 100 * imageHeight),
            (int) (widthPercent / 100 * imageWidth),
            (int) (heightPercent / 100 * imageHeight)
        );
    }
}
```

### 5.2 Annotation Types

**1. Rectangle Highlight**
- Standard rectangular selection
- Used for paneling, art issues

**2. Point Marker**
- Single point annotation
- Used for typos, small details

**3. Freeform Drawing**
- Path-based annotation
- Used for art corrections

**4. Text Comment**
- General feedback without coordinates
- Used for overall chapter feedback

### 5.3 Annotation Thread Model

```
Annotation (Root)
├── content: "Panel composition is weak"
├── severity: HIGH
├── status: OPEN
└── replies:
    ├── Reply 1: "I'll fix the perspective"
    │   └── status: IN_PROGRESS
    └── Reply 2: "Fixed, please review"
        └── status: RESOLVED
```

---

## 6. Immutable Snapshot Strategy

### 6.1 Snapshot Creation Process

```
1. Mangaka selects approved ChapterImage assets
2. System creates immutable snapshots:
   - Copy file to immutable storage path
   - Generate checksum (SHA-256)
   - Store original URL for audit trail
   - Record snapshot timestamp
3. Mangaka can reorder/replace before submission
4. Upon submission: snapshots become immutable
```

### 6.2 Storage Strategy

**Immutable Storage Path:**
```
/manuscripts/{manuscriptVersionId}/pages/{displayOrder}.{ext}
```

**Audit Trail:**
- Original ChapterImage URL preserved
- Source PageTask ID preserved
- Snapshot checksum for integrity verification
- Snapshot timestamp for audit

### 6.3 Immutability Enforcement

**Database Constraints:**
- `snapshotCreatedAt` marked as `updatable = false`
- Check constraint to prevent updates (logical immutability)

**Application-Level:**
- Repository methods throw exception if update attempted on immutable fields
- Service layer validates status before allowing modifications

---

## 7. Spring Boot Architecture

### 7.1 Entity Layer

```java
// Domain Entities
@Entity
public class ManuscriptVersion { /* ... */ }

@Entity
public class ManuscriptPage { /* ... */ }

@Entity
public class Annotation { /* ... */ }

@Entity
public class ManuscriptProductionLock { /* ... */ }
```

### 7.2 Repository Layer

```java
@Repository
public interface ManuscriptVersionRepository extends JpaRepository<ManuscriptVersion, Long> {
    @Query("SELECT m FROM ManuscriptVersion m WHERE m.chapterId = :chapterId ORDER BY m.version DESC")
    List<ManuscriptVersion> findByChapterIdOrderByVersionDesc(@Param("chapterId") Long chapterId);
    
    @Query("SELECT m FROM ManuscriptVersion m WHERE m.chapterId = :chapterId AND m.status = 'UNDER_REVIEW'")
    Optional<ManuscriptVersion> findUnderReviewByChapterId(@Param("chapterId") Long chapterId);
    
    @Query("SELECT m FROM ManuscriptVersion m WHERE m.chapterId = :chapterId AND m.version = :version")
    Optional<ManuscriptVersion> findByChapterIdAndVersion(@Param("chapterId") Long chapterId, @Param("version") Integer version);
}

@Repository
public interface ManuscriptPageRepository extends JpaRepository<ManuscriptPage, Long> {
    List<ManuscriptPage> findByManuscriptVersionIdOrderByDisplayOrder(Long manuscriptVersionId);
}

@Repository
public interface AnnotationRepository extends JpaRepository<Annotation, Long> {
    List<Annotation> findByManuscriptVersionIdAndPageNumberOrderByCreatedAt(Long manuscriptVersionId, Integer pageNumber);
    
    List<Annotation> findByParentAnnotationIdOrderByCreatedAt(Long parentAnnotationId);
    
    @Query("SELECT a FROM Annotation a WHERE a.manuscriptVersionId = :manuscriptVersionId AND a.parentAnnotationId IS NULL")
    List<Annotation> findRootAnnotationsByManuscriptVersionId(@Param("manuscriptVersionId") Long manuscriptVersionId);
}

@Repository
public interface ManuscriptProductionLockRepository extends JpaRepository<ManuscriptProductionLock, Long> {
    Optional<ManuscriptProductionLock> findByChapterId(Long chapterId);
}
```

### 7.3 Service Layer

```java
@Service
@Transactional
public class ManuscriptVersionService {
    
    @Autowired
    private ManuscriptVersionRepository manuscriptVersionRepository;
    
    @Autowired
    private ManuscriptPageRepository manuscriptPageRepository;
    
    @Autowired
    private AnnotationRepository annotationRepository;
    
    @Autowired
    private ManuscriptProductionLockRepository lockRepository;
    
    @Autowired
    private ChapterRepository chapterRepository;
    
    @Autowired
    private PageTaskRepository pageTaskRepository;
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * Create new manuscript version workspace
     * BR-1: Only chapters in EDITORIAL_REVIEW can create manuscripts
     * BR-2: Only one UNDER_REVIEW per chapter
     */
    public ManuscriptVersion createWorkspace(Long chapterId, AuthenticatedUser user) {
        // Validate chapter status
        Chapter chapter = chapterRepository.findById(chapterId)
            .orElseThrow(() -> new BusinessRuleException("Chapter not found"));
        
        if (!"EDITORIAL_REVIEW".equals(chapter.getStatus())) {
            throw new BusinessRuleException("Chapter must be in EDITORIAL_REVIEW to create manuscript (BR-1)");
        }
        
        // Validate no UNDER_REVIEW exists (BR-2)
        Optional<ManuscriptVersion> underReview = manuscriptVersionRepository
            .findUnderReviewByChapterId(chapterId);
        if (underReview.isPresent()) {
            throw new BusinessRuleException("Only one manuscript can be UNDER_REVIEW per chapter (BR-2)");
        }
        
        // Get next version number
        Integer nextVersion = manuscriptVersionRepository
            .findByChapterIdOrderByVersionDesc(chapterId)
            .stream()
            .map(ManuscriptVersion::getVersion)
            .findFirst()
            .orElse(0) + 1;
        
        // Create manuscript version
        ManuscriptVersion version = new ManuscriptVersion();
        version.setChapterId(chapterId);
        version.setVersion(nextVersion);
        version.setStatus(ManuscriptStatus.DRAFT);
        version.setCreatedAt(LocalDateTime.now());
        version = manuscriptVersionRepository.save(version);
        
        return version;
    }
    
    /**
     * Add page snapshot to manuscript
     * Creates immutable copy of production asset
     */
    public ManuscriptPage addPageSnapshot(Long manuscriptVersionId, Long chapterImageId, 
                                           Integer displayOrder, AuthenticatedUser user) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId)
            .orElseThrow(() -> new BusinessRuleException("Manuscript version not found"));
        
        if (version.getStatus() != ManuscriptStatus.DRAFT) {
            throw new BusinessRuleException("Can only add pages to DRAFT manuscripts");
        }
        
        ChapterImage sourceImage = chapterRepository.findChapterImageById(chapterImageId)
            .orElseThrow(() -> new BusinessRuleException("Source image not found"));
        
        // Create immutable snapshot
        String snapshotUrl = createImmutableSnapshot(sourceImage.getFileUrl());
        String checksum = calculateChecksum(snapshotUrl);
        
        ManuscriptPage page = new ManuscriptPage();
        page.setManuscriptVersionId(manuscriptVersionId);
        page.setDisplayOrder(displayOrder);
        page.setSnapshotFileUrl(snapshotUrl);
        page.setOriginalFileUrl(sourceImage.getFileUrl());
        page.setSourceChapterImageId(chapterImageId);
        page.setSourcePageTaskId(sourceImage.getPageTaskId());
        page.setPageNumber(displayOrder);
        page.setSnapshotCreatedAt(LocalDateTime.now());
        page.setSnapshotChecksum(checksum);
        
        return manuscriptPageRepository.save(page);
    }
    
    /**
     * Submit manuscript for review
     * Locks production assets (BR-9)
     */
    public void submitForReview(Long manuscriptVersionId, AuthenticatedUser user) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId)
            .orElseThrow(() -> new BusinessRuleException("Manuscript version not found"));
        
        if (version.getStatus() != ManuscriptStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT manuscripts can be submitted");
        }
        
        if (version.getPages().isEmpty()) {
            throw new BusinessRuleException("Manuscript must have at least one page");
        }
        
        // Validate no other UNDER_REVIEW exists (BR-2)
        Optional<ManuscriptVersion> underReview = manuscriptVersionRepository
            .findUnderReviewByChapterId(version.getChapterId());
        if (underReview.isPresent() && !underReview.get().getId().equals(manuscriptVersionId)) {
            throw new BusinessRuleException("Only one manuscript can be UNDER_REVIEW per chapter (BR-2)");
        }
        
        // Lock production (BR-9)
        lockProduction(version.getChapterId(), manuscriptVersionId, user.getId());
        
        // Update status
        version.setStatus(ManuscriptStatus.UNDER_REVIEW);
        version.setSubmittedAt(LocalDateTime.now());
        manuscriptVersionRepository.save(version);
        
        // Notify Tantou
        Long tantouId = chapterRepository.findTantouByChapterId(version.getChapterId());
        notificationService.notifyUser(tantouId, "MANUSCRIPT_SUBMITTED", 
            "Manuscript v" + version.getVersion() + " submitted for review", 
            manuscriptVersionId, "MANUSCRIPT");
    }
    
    /**
     * Approve manuscript
     * BR-4: APPROVED manuscripts cannot be mutated
     * BR-6: Publishing requires APPROVED status
     */
    public void approve(Long manuscriptVersionId, AuthenticatedUser user) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId)
            .orElseThrow(() -> new BusinessRuleException("Manuscript version not found"));
        
        if (version.getStatus() != ManuscriptStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("Only UNDER_REVIEW manuscripts can be approved");
        }
        
        // Update status
        version.setStatus(ManuscriptStatus.APPROVED);
        version.setApprovedAt(LocalDateTime.now());
        manuscriptVersionRepository.save(version);
        
        // Unlock production
        unlockProduction(version.getChapterId());
        
        // Notify Mangaka
        Long mangakaId = chapterRepository.findMangakaByChapterId(version.getChapterId());
        notificationService.notifyUser(mangakaId, "MANUSCRIPT_APPROVED",
            "Manuscript approved for chapter #" + version.getChapterId(),
            manuscriptVersionId, "MANUSCRIPT");
    }
    
    /**
     * Reject manuscript
     * BR-3: REJECTED manuscripts cannot be mutated
     */
    public void reject(Long manuscriptVersionId, String feedback, AuthenticatedUser user) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId)
            .orElseThrow(() -> new BusinessRuleException("Manuscript version not found"));
        
        if (version.getStatus() != ManuscriptStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("Only UNDER_REVIEW manuscripts can be rejected");
        }
        
        if (feedback == null || feedback.trim().isEmpty()) {
            throw new BusinessRuleException("Feedback is required when rejecting manuscript");
        }
        
        // Update status
        version.setStatus(ManuscriptStatus.REJECTED);
        version.setRejectedAt(LocalDateTime.now());
        version.setFeedback(feedback);
        manuscriptVersionRepository.save(version);
        
        // Unlock production
        unlockProduction(version.getChapterId());
        
        // Notify Mangaka
        Long mangakaId = chapterRepository.findMangakaByChapterId(version.getChapterId());
        notificationService.notifyUser(mangakaId, "MANUSCRIPT_REJECTED",
            "Manuscript rejected. Feedback: " + feedback,
            manuscriptVersionId, "MANUSCRIPT");
    }
    
    /**
     * Create new version after rejection
     * BR-3: Previous REJECTED version remains immutable
     */
    public ManuscriptVersion createNewVersion(Long chapterId, AuthenticatedUser user) {
        // Validate latest version is REJECTED
        ManuscriptVersion latest = manuscriptVersionRepository
            .findByChapterIdOrderByVersionDesc(chapterId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessRuleException("No previous manuscript version found"));
        
        if (latest.getStatus() != ManuscriptStatus.REJECTED) {
            throw new BusinessRuleException("New version can only be created after REJECTED status");
        }
        
        // Create new DRAFT version
        return createWorkspace(chapterId, user);
    }
    
    private void lockProduction(Long chapterId, Long manuscriptVersionId, Long lockedBy) {
        ManuscriptProductionLock lock = new ManuscriptProductionLock();
        lock.setChapterId(chapterId);
        lock.setManuscriptVersionId(manuscriptVersionId);
        lock.setLockedAt(LocalDateTime.now());
        lock.setLockedBy(lockedBy);
        lockRepository.save(lock);
    }
    
    private void unlockProduction(Long chapterId) {
        lockRepository.findByChapterId(chapterId).ifPresent(lock -> {
            lock.setUnlockedAt(LocalDateTime.now());
            lockRepository.save(lock);
        });
    }
    
    private String createImmutableSnapshot(String originalUrl) {
        // Implementation: Copy file to immutable storage
        // Generate new URL with checksum
        // Return immutable URL
        return originalUrl; // Placeholder
    }
    
    private String calculateChecksum(String fileUrl) {
        // Implementation: Calculate SHA-256 checksum
        return "placeholder-checksum";
    }
}
```

```java
@Service
@Transactional
public class AnnotationService {
    
    @Autowired
    private AnnotationRepository annotationRepository;
    
    @Autowired
    private ManuscriptVersionRepository manuscriptVersionRepository;
    
    /**
     * Add annotation to manuscript
     * BR-5: Annotations belong to specific manuscript version
     * BR-8: Coordinates must be valid percentages
     */
    public Annotation addAnnotation(Long manuscriptVersionId, AddAnnotationRequest request, 
                                    AuthenticatedUser user) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId)
            .orElseThrow(() -> new BusinessRuleException("Manuscript version not found"));
        
        if (version.getStatus() != ManuscriptStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("Can only annotate UNDER_REVIEW manuscripts");
        }
        
        // Validate coordinates
        validateCoordinates(request.getXPercent(), request.getYPercent(), 
                         request.getWidthPercent(), request.getHeightPercent());
        
        Annotation annotation = new Annotation();
        annotation.setManuscriptVersionId(manuscriptVersionId);
        annotation.setEditorId(user.getId());
        annotation.setPageNumber(request.getPageNumber());
        annotation.setXPercent(request.getXPercent());
        annotation.setYPercent(request.getYPercent());
        annotation.setWidthPercent(request.getWidthPercent());
        annotation.setHeightPercent(request.getHeightPercent());
        annotation.setCategory(request.getCategory());
        annotation.setSeverity(request.getSeverity());
        annotation.setStatus(AnnotationStatus.OPEN);
        annotation.setContent(request.getContent());
        annotation.setCreatedAt(LocalDateTime.now());
        
        return annotationRepository.save(annotation);
    }
    
    /**
     * Add reply to annotation
     */
    public Annotation addReply(Long parentAnnotationId, String content, AuthenticatedUser user) {
        Annotation parent = annotationRepository.findById(parentAnnotationId)
            .orElseThrow(() -> new BusinessRuleException("Parent annotation not found"));
        
        Annotation reply = new Annotation();
        reply.setManuscriptVersionId(parent.getManuscriptVersionId());
        reply.setEditorId(user.getId());
        reply.setPageNumber(parent.getPageNumber());
        reply.setParentAnnotationId(parentAnnotationId);
        reply.setCategory(AnnotationCategory.OTHER);
        reply.setSeverity(AnnotationSeverity.LOW);
        reply.setStatus(AnnotationStatus.OPEN);
        reply.setContent(content);
        reply.setCreatedAt(LocalDateTime.now());
        
        return annotationRepository.save(reply);
    }
    
    /**
     * Resolve annotation
     */
    public void resolveAnnotation(Long annotationId, AuthenticatedUser user) {
        Annotation annotation = annotationRepository.findById(annotationId)
            .orElseThrow(() -> new BusinessRuleException("Annotation not found"));
        
        annotation.setStatus(AnnotationStatus.RESOLVED);
        annotation.setResolvedAt(LocalDateTime.now());
        annotation.setResolvedBy(user.getId());
        
        annotationRepository.save(annotation);
    }
    
    private void validateCoordinates(Double x, Double y, Double w, Double h) {
        if (x < 0 || x > 100 || y < 0 || y > 100) {
            throw new BusinessRuleException("Coordinates must be between 0 and 100");
        }
        if (w <= 0 || w > 100 || h <= 0 || h > 100) {
            throw new BusinessRuleException("Width and height must be between 0 and 100");
        }
    }
}
```

---

## 8. API Endpoint Redesign

### 8.1 Manuscript Version Endpoints

```java
@RestController
@RequestMapping("/api/v1/manuscripts")
public class ManuscriptApiController {
    
    @Autowired
    private ManuscriptVersionService manuscriptVersionService;
    
    /**
     * Create new manuscript workspace
     * POST /api/v1/manuscripts/chapters/{chapterId}/workspace
     */
    @PostMapping("/chapters/{chapterId}/workspace")
    public ResponseEntity<ApiResponse> createWorkspace(
            @PathVariable Long chapterId,
            @RequestBody CreateWorkspaceRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ManuscriptVersion version = manuscriptVersionService.createWorkspace(chapterId, user);
        return ResponseEntity.ok(ApiResponse.success(version));
    }
    
    /**
     * Add page snapshot
     * POST /api/v1/manuscripts/{versionId}/pages
     */
    @PostMapping("/{versionId}/pages")
    public ResponseEntity<ApiResponse> addPageSnapshot(
            @PathVariable Long versionId,
            @RequestBody AddPageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ManuscriptPage page = manuscriptVersionService.addPageSnapshot(
            versionId, request.getChapterImageId(), request.getDisplayOrder(), user);
        return ResponseEntity.ok(ApiResponse.success(page));
    }
    
    /**
     * Reorder pages
     * PUT /api/v1/manuscripts/{versionId}/pages/reorder
     */
    @PutMapping("/{versionId}/pages/reorder")
    public ResponseEntity<ApiResponse> reorderPages(
            @PathVariable Long versionId,
            @RequestBody ReorderPagesRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        manuscriptVersionService.reorderPages(versionId, request.getPageOrders(), user);
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * Submit for review
     * POST /api/v1/manuscripts/{versionId}/submit
     */
    @PostMapping("/{versionId}/submit")
    public ResponseEntity<ApiResponse> submitForReview(
            @PathVariable Long versionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        manuscriptVersionService.submitForReview(versionId, user);
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * Approve manuscript
     * POST /api/v1/manuscripts/{versionId}/approve
     */
    @PostMapping("/{versionId}/approve")
    public ResponseEntity<ApiResponse> approve(
            @PathVariable Long versionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        manuscriptVersionService.approve(versionId, user);
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * Reject manuscript
     * POST /api/v1/manuscripts/{versionId}/reject
     */
    @PostMapping("/{versionId}/reject")
    public ResponseEntity<ApiResponse> reject(
            @PathVariable Long versionId,
            @RequestBody RejectRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        manuscriptVersionService.reject(versionId, request.getFeedback(), user);
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * Create new version
     * POST /api/v1/manuscripts/chapters/{chapterId}/new-version
     */
    @PostMapping("/chapters/{chapterId}/new-version")
    public ResponseEntity<ApiResponse> createNewVersion(
            @PathVariable Long chapterId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ManuscriptVersion version = manuscriptVersionService.createNewVersion(chapterId, user);
        return ResponseEntity.ok(ApiResponse.success(version));
    }
    
    /**
     * Get manuscript version details
     * GET /api/v1/manuscripts/{versionId}
     */
    @GetMapping("/{versionId}")
    public ResponseEntity<ApiResponse> getVersion(@PathVariable Long versionId) {
        ManuscriptVersion version = manuscriptVersionService.getVersion(versionId);
        return ResponseEntity.ok(ApiResponse.success(version));
    }
    
    /**
     * List versions for chapter
     * GET /api/v1/manuscripts/chapters/{chapterId}/versions
     */
    @GetMapping("/chapters/{chapterId}/versions")
    public ResponseEntity<ApiResponse> listVersions(@PathVariable Long chapterId) {
        List<ManuscriptVersion> versions = manuscriptVersionService.listVersions(chapterId);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }
}
```

### 8.2 Annotation Endpoints

```java
@RestController
@RequestMapping("/api/v1/annotations")
public class AnnotationApiController {
    
    @Autowired
    private AnnotationService annotationService;
    
    /**
     * Add annotation
     * POST /api/v1/annotations
     */
    @PostMapping
    public ResponseEntity<ApiResponse> addAnnotation(
            @RequestBody AddAnnotationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Annotation annotation = annotationService.addAnnotation(
            request.getManuscriptVersionId(), request, user);
        return ResponseEntity.ok(ApiResponse.success(annotation));
    }
    
    /**
     * Add reply
     * POST /api/v1/annotations/{annotationId}/replies
     */
    @PostMapping("/{annotationId}/replies")
    public ResponseEntity<ApiResponse> addReply(
            @PathVariable Long annotationId,
            @RequestBody AddReplyRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Annotation reply = annotationService.addReply(annotationId, request.getContent(), user);
        return ResponseEntity.ok(ApiResponse.success(reply));
    }
    
    /**
     * Resolve annotation
     * POST /api/v1/annotations/{annotationId}/resolve
     */
    @PostMapping("/{annotationId}/resolve")
    public ResponseEntity<ApiResponse> resolve(
            @PathVariable Long annotationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        annotationService.resolveAnnotation(annotationId, user);
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * List annotations for manuscript version
     * GET /api/v1/annotations/manuscripts/{versionId}
     */
    @GetMapping("/manuscripts/{versionId}")
    public ResponseEntity<ApiResponse> listByManuscript(@PathVariable Long versionId) {
        List<Annotation> annotations = annotationService.listByManuscriptVersion(versionId);
        return ResponseEntity.ok(ApiResponse.success(annotations));
    }
    
    /**
     * List annotations for page
     * GET /api/v1/annotations/manuscripts/{versionId}/pages/{pageNumber}
     */
    @GetMapping("/manuscripts/{versionId}/pages/{pageNumber}")
    public ResponseEntity<ApiResponse> listByPage(
            @PathVariable Long versionId,
            @PathVariable Integer pageNumber) {
        List<Annotation> annotations = annotationService.listByPage(versionId, pageNumber);
        return ResponseEntity.ok(ApiResponse.success(annotations));
    }
}
```

---

## 9. Migration Strategy

### 9.1 Phase 1: Schema Migration

```sql
-- Step 1: Create new tables
CREATE TABLE [dbo].[ManuscriptVersion] (/* ... */);
CREATE TABLE [dbo].[ManuscriptPage] (/* ... */);
CREATE TABLE [dbo].[ManuscriptProductionLock] (/* ... */);

-- Step 2: Migrate existing Manuscript data
INSERT INTO [dbo].[ManuscriptVersion] (chapterId, version, status, createdAt, submittedAt, feedback)
SELECT chapterId, version, status, submittedAt, feedback
FROM [dbo].[Manuscript];

-- Step 3: Update Annotation table
ALTER TABLE [dbo].[Annotation] ADD manuscriptVersionId bigint NULL;
ALTER TABLE [dbo].[Annotation] ADD xPercent decimal(5,2) NULL;
ALTER TABLE [dbo].[Annotation] ADD yPercent decimal(5,2) NULL;
ALTER TABLE [dbo].[Annotation] ADD widthPercent decimal(5,2) NULL;
ALTER TABLE [dbo].[Annotation] ADD heightPercent decimal(5,2) NULL;
ALTER TABLE [dbo].[Annotation] ADD severity varchar(20) NULL;
ALTER TABLE [dbo].[Annotation] ADD parentAnnotationId bigint NULL;

-- Step 4: Backfill annotation data
UPDATE [dbo].[Annotation] SET manuscriptVersionId = manuscriptId;

-- Step 5: Drop old Manuscript table (after verification)
DROP TABLE [dbo].[Manuscript];
```

### 9.2 Phase 2: Code Migration

1. **Update Enums**: Change ManuscriptStatus to match new workflow
2. **Update Services**: Refactor ManuscriptService to use new aggregate
3. **Update Controllers**: Refactor API endpoints
4. **Update JSPs**: Update frontend to use new workspace model
5. **Data Validation**: Verify migrated data integrity

### 9.3 Phase 3: Feature Rollout

1. **Workspace Creation**: Enable new workspace creation UI
2. **Page Snapshots**: Enable page snapshot functionality
3. **Annotation Coordinates**: Enable coordinate-based annotations
4. **Production Locks**: Enable production locking during review
5. **Versioning**: Enable v1 → v2 → v3 workflow

---

## 10. Frontend Workflow Architecture

### 10.1 Mangaka Workspace UI

```
┌─────────────────────────────────────────────────────────────┐
│  Manuscript Workspace - Chapter #X - Version v1 (DRAFT)    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [Page Gallery]                                             │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐              │
│  │ P1  │ │ P2  │ │ P3  │ │ P4  │ │ +   │              │
│  │     │ │     │ │     │ │     │ │     │              │
│  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘              │
│                                                             │
│  [Drag to reorder] [Replace page] [Delete]                 │
│                                                             │
│  [Preview Mode] [Reading Order] [Page Numbers]             │
│                                                             │
│  [Submit for Review] [Save Draft]                          │
└─────────────────────────────────────────────────────────────┘
```

### 10.2 Tantou Review UI

```
┌─────────────────────────────────────────────────────────────┐
│  Editorial Review - Chapter #X - Version v1 (UNDER_REVIEW) │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [Page Viewer]                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │  [Annotation Overlay]                               │   │
│  │  ┌──────────┐                                       │   │
│  │  │ Highlight │ ← Critical: Panel composition         │   │
│  │  └──────────┘                                       │   │
│  │                                                     │   │
│  │  [Reply Thread]                                    │   │
│  │  - I'll fix perspective                             │   │
│  │  - Fixed, please review ✓                           │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  [Add Annotation] [Navigate Pages] [Filter by Severity]     │
│                                                             │
│  [Approve] [Reject with Feedback] [Request Revision]        │
└─────────────────────────────────────────────────────────────┘
```

### 10.3 Frontend State Management

```javascript
// Manuscript Workspace State
const manuscriptState = {
    versionId: 123,
    status: 'DRAFT',
    pages: [
        { id: 1, displayOrder: 1, snapshotUrl: '...', pageNumber: 1 },
        { id: 2, displayOrder: 2, snapshotUrl: '...', pageNumber: 2 }
    ],
    annotations: [],
    isProductionLocked: false
};

// Annotation State
const annotationState = {
    selectedAnnotation: null,
    tool: 'highlight', // highlight, point, freeform
    severity: 'MEDIUM',
    category: 'ART'
};
```

---

## 11. Implementation Order

### Phase 1: Foundation (Week 1-2)
1. Create new database schema
2. Implement entity classes
3. Implement repository interfaces
4. Implement basic service methods
5. Write unit tests for domain logic

### Phase 2: Workspace Creation (Week 3-4)
1. Implement workspace creation service
2. Implement page snapshot service
3. Implement immutable snapshot strategy
4. Create workspace UI components
5. Integrate with existing chapter workflow

### Phase 3: Annotation Engine (Week 5-6)
1. Implement annotation service
2. Implement coordinate model
3. Create annotation UI components
4. Implement thread/reply functionality
5. Add severity and category support

### Phase 4: Review Workflow (Week 7-8)
1. Implement submit for review
2. Implement production locking
3. Implement approve/reject logic
4. Create review UI components
5. Add notification integration

### Phase 5: Versioning (Week 9-10)
1. Implement version chain logic
2. Implement new version creation
3. Add version comparison UI
4. Implement migration from old system
5. Data validation and cleanup

### Phase 6: Integration & Testing (Week 11-12)
1. End-to-end workflow testing
2. Performance testing
3. Security testing
4. User acceptance testing
5. Documentation and training

---

## 12. Risks and Edge Cases

### 12.1 Technical Risks

**Risk 1: Storage Bloat**
- Immutable snapshots may duplicate storage
- **Mitigation**: Implement deduplication by checksum, use CDN caching

**Risk 2: Coordinate Scaling**
- Percentage-based coordinates may not work for all image aspect ratios
- **Mitigation**: Add aspect ratio validation, provide fallback to pixel coordinates

**Risk 3: Production Lock Deadlocks**
- Lock may not be released if system crashes
- **Mitigation**: Implement lock timeout, admin override mechanism

**Risk 4: Migration Data Loss**
- Existing manuscript data may not migrate cleanly
- **Mitigation**: Comprehensive backup, rollback plan, data validation scripts

### 12.2 Business Risks

**Risk 1: User Adoption**
- New workflow may confuse existing users
- **Mitigation**: Gradual rollout, training materials, UI guidance

**Risk 2: Workflow Disruption**
- Chapter status reinterpretation may break existing processes
- **Mitigation**: Maintain backward compatibility, phased rollout

**Risk 3: Performance Impact**
- Complex annotation queries may slow down system
- **Mitigation**: Database indexing, caching, pagination

### 12.3 Edge Cases

**Edge Case 1: Concurrent Workspace Creation**
- Multiple users try to create workspace simultaneously
- **Solution**: Database-level unique constraint, optimistic locking

**Edge Case 2: Page Snapshot Failure**
- File copy fails during snapshot creation
- **Solution**: Transactional rollback, retry mechanism, error logging

**Edge Case 3: Annotation Coordinate Overflow**
- Coordinates exceed 100% due to rounding errors
- **Solution**: Database constraints, input validation, clamping

**Edge Case 4: Version Chain Too Long**
- Excessive revision cycles create many versions
- **Solution: Soft limit (e.g., 10 versions), archival policy

---

## 13. Business-Rule Validation Map

| Business Rule | Validation Point | Implementation |
|---------------|------------------|----------------|
| BR-1: Only EDITORIAL_REVIEW chapters can create manuscripts | Service.createWorkspace() | Check chapter.status == 'EDITORIAL_REVIEW' |
| BR-2: Only one UNDER_REVIEW per chapter | Service.submitForReview() | Query for existing UNDER_REVIEW, throw if exists |
| BR-3: Rejected manuscripts cannot be mutated | Service.update*() | Check status != REJECTED before any update |
| BR-4: Approved manuscripts cannot be mutated | Service.update*() | Check status != APPROVED before any update |
| BR-5: Annotations belong to specific manuscript version | AnnotationService.addAnnotation() | Bind to manuscriptVersionId, cascade delete |
| BR-6: Publishing requires APPROVED status | Chapter.publish() | Check latest manuscript version is APPROVED |
| BR-7: Page assets must be immutable snapshots | Service.addPageSnapshot() | Create copy with checksum, mark immutable |
| BR-8: Coordinates must scale responsively | AnnotationService.addAnnotation() | Validate 0-100 range, store as percentages |
| BR-9: Production locked during review | Service.submitForReview() | Create ManuscriptProductionLock record |

---

## 14. Future Extensions

### 14.1 Short-term (6 months)

1. **Annotation Templates**: Pre-defined annotation categories for common issues
2. **Batch Operations**: Bulk resolve annotations, bulk severity updates
3. **Annotation Export**: Export annotations as PDF overlay or JSON
4. **Version Comparison**: Side-by-side diff between manuscript versions
5. **Analytics Dashboard**: Annotation statistics, review time metrics

### 14.2 Medium-term (12 months)

1. **AI-Assisted Review**: ML model to detect common art issues
2. **Collaborative Review**: Multiple editors can review simultaneously
3. **Real-time Collaboration**: WebSocket-based live annotation
4. **Mobile Review**: Tablet-optimized review interface
5. **Integration with Drawing Tools**: Direct integration with manga creation software

### 14.3 Long-term (18+ months)

1. **3D Annotation**: Support for 3D manga formats
2. **Voice Annotations**: Audio feedback for annotations
3. **Automated Quality Checks**: Pre-review validation using AI
4. **Cross-Platform Sync**: Sync annotations across devices
5. **Advanced Analytics**: Sentiment analysis, trend detection

---

## 15. Conclusion

This architectural redesign transforms the Manuscript + Annotation modules from a simple file-upload system into a professional collaborative manga editorial review platform. The key improvements include:

1. **Visual Workspace**: Manuscript becomes a visual editorial review workspace with immutable page snapshots
2. **Immutable Versioning**: Proper v1 → v2 → v3 chains with enforced immutability
3. **Rich Annotations**: Coordinate-based annotations with threads, severity, and categories
4. **Production Locking**: Assets locked during review to prevent conflicts
5. **Clean Workflow**: Eliminates circular dependency between chapter and manuscript lifecycles
6. **Enterprise-Grade**: Proper aggregate boundaries, business rule validation, and auditability

The design preserves all existing business rules while enabling the new editorial review workflow. The implementation is phased to minimize disruption and ensure data integrity during migration.
