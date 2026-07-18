/* ==== Auto-generated cleanup block: drop all existing tables/FKs before recreating ==== */
DECLARE @sql NVARCHAR(MAX) = N'';

SELECT @sql += 'ALTER TABLE ' + QUOTENAME(s.name) + '.' + QUOTENAME(t.name) +
    ' DROP CONSTRAINT ' + QUOTENAME(fk.name) + ';' + CHAR(10)
FROM sys.foreign_keys fk
JOIN sys.tables t ON fk.parent_object_id = t.object_id
JOIN sys.schemas s ON t.schema_id = s.schema_id;

EXEC sp_executesql @sql;

SET @sql = N'';
SELECT @sql += 'DROP TABLE ' + QUOTENAME(s.name) + '.' + QUOTENAME(t.name) + ';' + CHAR(10)
FROM sys.tables t
JOIN sys.schemas s ON t.schema_id = s.schema_id;

EXEC sp_executesql @sql;
GO
/* ==== End cleanup block ==== */

/****** Object:  Database [MangaEditorialDB]    Script Date: 6/5/2026 4:22:06 AM ******/
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
GO
/****** Object:  Table [dbo].[Annotation]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Annotation](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[editorId] [bigint] NOT NULL,
	[pageNumber] [int] NOT NULL,
	[category] [varchar](30) NOT NULL,
	[status] [varchar](30) NOT NULL,
	[content] [nvarchar](max) NOT NULL,
	[createdAt] [datetime] NOT NULL,
	[manuscriptVersionId] [bigint] NULL,
	[xPercent] [decimal](5, 2) NULL,
	[yPercent] [decimal](5, 2) NULL,
	[widthPercent] [decimal](5, 2) NULL,
	[heightPercent] [decimal](5, 2) NULL,
	[severity] [varchar](20) NULL,
	[parentAnnotationId] [bigint] NULL,
	[resolvedAt] [datetime] NULL,
	[resolvedBy] [bigint] NULL,
	[manuscriptPageId] [bigint] NULL,
 CONSTRAINT [PK_Annotation] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[AuditLog]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[AuditLog](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[actorId] [bigint] NULL,
	[action] [varchar](255) NOT NULL,
	[entityType] [varchar](100) NOT NULL,
	[entityId] [bigint] NOT NULL,
	[detail] [nvarchar](max) NULL,
	[performedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_AuditLog] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Chapter]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Chapter](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[seriesId] [bigint] NOT NULL,
	[chapterNumber] [int] NOT NULL,
	[title] [varchar](255) NOT NULL,
	[status] [varchar](20) NOT NULL,
	[submissionDeadline] [date] NOT NULL,
	[publicationDate] [date] NOT NULL,
	[completionPct] [decimal](5, 2) NOT NULL,
	[atRisk] [bit] NOT NULL,
	[totalPages] [int] NULL,
	[createdAt] [datetime] NOT NULL,
 CONSTRAINT [PK_Chapter] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ChapterImage]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ChapterImage](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[chapterId] [bigint] NOT NULL,
	[pageTaskId] [bigint] NULL,
	[uploadedBy] [bigint] NOT NULL,
	[imageType] [varchar](20) NOT NULL,
	[pageNumber] [int] NULL,
	[fileUrl] [varchar](512) NOT NULL,
	[originalFileName] [nvarchar](255) NOT NULL,
	[fileSizeBytes] [bigint] NULL,
	[uploadedAt] [datetime] NOT NULL,
	[isActive] [bit] NOT NULL,
	[note] [nvarchar](500) NULL,
 CONSTRAINT [PK_ChapterImage] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[DecisionSession]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[DecisionSession](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[seriesId] [bigint] NOT NULL,
	[rankingRecordId] [bigint] NOT NULL,
	[status] [varchar](10) NOT NULL,
	[result] [varchar](15) NULL,
	[systemSuggestion] [varchar](20) NULL,
	[openedAt] [datetime] NOT NULL,
	[closedAt] [datetime] NULL,
	[revenueTrendSnapshot] [nvarchar](max) NULL,
 CONSTRAINT [PK_DecisionSession] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[DecisionVote]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[DecisionVote](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[sessionId] [bigint] NOT NULL,
	[voterId] [bigint] NOT NULL,
	[decision] [varchar](15) NOT NULL,
	[justification] [nvarchar](max) NULL,
	[votedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_DecisionVote] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[MangakaAssistant]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[MangakaAssistant](
	[mangakaId] [bigint] NOT NULL,
	[assistantId] [bigint] NOT NULL,
	[enrolledAt] [datetime] NOT NULL,
 CONSTRAINT [PK_MangakaAssistant] PRIMARY KEY CLUSTERED
(
	[mangakaId] ASC,
	[assistantId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[MangakaRankingRecord]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[MangakaRankingRecord](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[periodId] [bigint] NOT NULL,
	[mangakaId] [bigint] NOT NULL,
	[totalReads] [bigint] NOT NULL,
	[totalRevenue] [decimal](15, 2) NOT NULL,
	[totalLikes] [bigint] NOT NULL,
	[rankPosition] [int] NOT NULL,
	[calculatedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_MangakaRankingRecord] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Manuscript]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Manuscript](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[chapterId] [bigint] NOT NULL,
	[version] [int] NOT NULL,
	[status] [varchar](20) NOT NULL,
	[submittedAt] [datetime] NOT NULL,
	[reviewDeadline]  AS (dateadd(hour,(48),[submittedAt])) PERSISTED,
	[fileUrl] [varchar](512) NOT NULL,
	[revisionDeadline] [datetime] NULL,
	[feedback] [nvarchar](max) NULL,
	[seriesTitle] [nvarchar](255) NULL,
	[chapterTitle] [nvarchar](255) NULL,
	[chapterNumber] [int] NULL,
	[originalFileName] [nvarchar](255) NULL,
	[uploadedAt] [datetime] NULL,
	[fileSize] [bigint] NULL,
	[fileExtension] [varchar](20) NULL,
	[notes] [nvarchar](max) NULL,
	[genre] [nvarchar](255) NULL,
 CONSTRAINT [PK_Manuscript] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ManuscriptPage]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ManuscriptPage](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[manuscriptVersionId] [bigint] NOT NULL,
	[displayOrder] [int] NOT NULL,
	[snapshotFileUrl] [varchar](512) NOT NULL,
	[originalFileUrl] [varchar](512) NOT NULL,
	[sourceChapterImageId] [bigint] NULL,
	[sourcePageTaskId] [bigint] NULL,
	[pageNumber] [int] NOT NULL,
	[snapshotCreatedAt] [datetime] NOT NULL,
	[snapshotChecksum] [varchar](64) NOT NULL,
 CONSTRAINT [PK_ManuscriptPage] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ManuscriptProductionLock]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ManuscriptProductionLock](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[chapterId] [bigint] NOT NULL,
	[manuscriptVersionId] [bigint] NOT NULL,
	[lockedAt] [datetime] NOT NULL,
	[lockedBy] [bigint] NOT NULL,
	[unlockedAt] [datetime] NULL,
 CONSTRAINT [PK_ManuscriptProductionLock] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_ManuscriptProductionLock_Chapter] UNIQUE NONCLUSTERED
(
	[chapterId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ManuscriptVersion]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ManuscriptVersion](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[chapterId] [bigint] NOT NULL,
	[version] [int] NOT NULL,
	[status] [varchar](20) NOT NULL,
	[createdAt] [datetime] NOT NULL,
	[submittedAt] [datetime] NULL,
	[approvedAt] [datetime] NULL,
	[rejectedAt] [datetime] NULL,
	[feedback] [nvarchar](max) NULL,
	[revisionNotes] [nvarchar](max) NULL,
	[totalPageCount] [int] NULL,
	[previousVersionId] [bigint] NULL,
	[publishedAt] [datetime] NULL,
	[createdBy] [bigint] NULL,
	[submittedBy] [bigint] NULL,
	[approvedBy] [bigint] NULL,
	[rejectedBy] [bigint] NULL,
 CONSTRAINT [PK_ManuscriptVersion] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_ManuscriptVersion_Chapter_Version] UNIQUE NONCLUSTERED
(
	[chapterId] ASC,
	[version] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Notification]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Notification](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[userId] [bigint] NOT NULL,
	[type] [varchar](50) NOT NULL,
	[title] [nvarchar](200) NULL,
	[message] [nvarchar](max) NOT NULL,
	[referenceId] [bigint] NULL,
	[referenceType] [varchar](50) NULL,
	[viewUrl] [nvarchar](500) NULL,
	[isRead] [bit] NOT NULL,
	[createdAt] [datetime] NOT NULL,
 CONSTRAINT [PK_Notification] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Page]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Page](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[chapterId] [bigint] NOT NULL,
	[pageNumber] [int] NOT NULL,
	[imageUrl] [nvarchar](512) NULL,
	[uploadedBy] [bigint] NULL,
	[uploadedAt] [datetime] NULL,
	[status] [varchar](20) NOT NULL,
	[createdAt] [datetime] NOT NULL,
	[completedStage] [varchar](30) NULL,
 CONSTRAINT [PK_Page] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Page_chapter_page] UNIQUE NONCLUSTERED
(
	[chapterId] ASC,
	[pageNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[PageTask]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[PageTask](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[chapterId] [bigint] NOT NULL,
	[pageId] [bigint] NULL,
	[assistantId] [bigint] NOT NULL,
	[pageRangeStart] [int] NOT NULL,
	[pageRangeEnd] [int] NOT NULL,
	[dueDate] [date] NOT NULL,
	[status] [varchar](20) NOT NULL,
	[rejectionCount] [int] NOT NULL,
	[rejectionReason] [nvarchar](300) NULL,
	[approvalComment] [nvarchar](300) NULL,
	[priority] [varchar](20) NULL,
	[notes] [nvarchar](500) NULL,
	[assignedAt] [datetime] NOT NULL,
	[updatedAt] [datetime] NOT NULL,
	[actionReason] [nvarchar](300) NULL,
	[previousAssistantId] [bigint] NULL,
	[lastProgressAt] [datetime] NULL,
	[isSalaried] [bit] NOT NULL CONSTRAINT [DF_PageTask_isSalaried] DEFAULT ((0)),
 CONSTRAINT [PK_PageTask] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Proposal]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Proposal](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[mangakaId] [bigint] NOT NULL,
	[title] [varchar](255) NOT NULL,
	[genre] [varchar](100) NOT NULL,
	[synopsis] [nvarchar](max) NOT NULL,
	[sampleFilePath] [varchar](512) NOT NULL,
	[originalFileName] [nvarchar](255) NOT NULL,
	[approximateChapter] [int] NOT NULL,
	[status] [varchar](20) NOT NULL,
	[submittedAt] [datetime] NULL,
	[rejectedAt] [datetime] NULL,
	[assignedEditorId] [bigint] NULL,
	[submitAttemptCount] [int] NOT NULL,
	[tantouReviewOverdue] [bit] NOT NULL,
	[createdAt] [datetime] NOT NULL,
	[updatedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_Proposal] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ProposalBoardRound]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ProposalBoardRound](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[proposalId] [bigint] NOT NULL,
	[submitAttemptNumber] [int] NOT NULL,
	[roundNumber] [int] NOT NULL,
	[status] [varchar](10) NOT NULL,
	[openedAt] [datetime] NOT NULL,
	[closesAt] [datetime] NOT NULL,
	[closedAt] [datetime] NULL,
	[closeReason] [varchar](30) NULL,
 CONSTRAINT [PK_ProposalBoardRound] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ProposalBoardRoundVoter]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[SystemSetting](
	[settingKey] [varchar](100) NOT NULL,
	[settingValue] [varchar](255) NOT NULL,
	[updatedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_SystemSetting] PRIMARY KEY CLUSTERED
(
	[settingKey] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ProposalBoardRoundVoter]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ProposalBoardRoundVoter](
	[roundId] [bigint] NOT NULL,
	[voterId] [bigint] NOT NULL,
 CONSTRAINT [PK_ProposalBoardRoundVoter] PRIMARY KEY CLUSTERED
(
	[roundId] ASC,
	[voterId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ProposalHistory]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ProposalHistory](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[proposalId] [bigint] NOT NULL,
	[actorId] [bigint] NULL,
	[actorRole] [varchar](50) NOT NULL,
	[actionType] [varchar](30) NOT NULL,
	[note] [nvarchar](max) NULL,
	[submitAttemptNumber] [int] NOT NULL,
	[boardRoundId] [bigint] NULL,
	[createdAt] [datetime] NOT NULL,
 CONSTRAINT [PK_ProposalHistory] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[RankingPeriod]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[RankingPeriod](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[name] [varchar](255) NOT NULL,
	[startDate] [date] NOT NULL,
	[endDate] [date] NOT NULL,
	[status] [varchar](15) NOT NULL,
	[calculatedAt] [datetime] NULL,
 CONSTRAINT [PK_RankingPeriod] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[RankingRecord]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[RankingRecord](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[periodId] [bigint] NOT NULL,
	[seriesId] [bigint] NOT NULL,
	[rankScore] [decimal](6, 2) NOT NULL,
	[rankPosition] [int] NOT NULL,
	[isBottomTwenty] [bit] NOT NULL,
	[calculatedAt] [datetime] NOT NULL,
	[totalLikes] [bigint] NULL,
	[totalReads] [bigint] NULL,
 CONSTRAINT [PK_RankingRecord] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ReviewDecision]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ReviewDecision](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[manuscriptVersionId] [bigint] NOT NULL,
	[reviewerId] [bigint] NOT NULL,
	[decisionType] [varchar](20) NOT NULL,
	[comment] [nvarchar](max) NULL,
	[decisionAt] [datetime] NOT NULL,
 CONSTRAINT [PK_ReviewDecision] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ReviewTask]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ReviewTask](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[versionId] [bigint] NOT NULL,
	[reviewerId] [bigint] NOT NULL,
	[assignedAt] [datetime] NOT NULL,
	[dueAt] [datetime] NOT NULL,
	[reviewStatus] [varchar](20) NOT NULL,
 CONSTRAINT [PK_ReviewTask] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Role]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Role](
	[id] [bigint] NOT NULL,
	[name] [varchar](50) NOT NULL,
 CONSTRAINT [PK_Role] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Role_name] UNIQUE NONCLUSTERED
(
	[name] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Series]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Series](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[proposalId] [bigint] NOT NULL,
	[mangakaId] [bigint] NOT NULL,
	[tantouEditorId] [bigint] NOT NULL,
	[title] [varchar](255) NOT NULL,
	[genre] [varchar](100) NOT NULL,
	[status] [varchar](10) NOT NULL,
	[publicationDate] [date] NULL,
	[createdAt] [datetime] NOT NULL,
 CONSTRAINT [PK_Series] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Series_proposalId] UNIQUE NONCLUSTERED
(
	[proposalId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[SeriesAssistant]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[SeriesAssistant](
	[seriesId] [bigint] NOT NULL,
	[assistantId] [bigint] NOT NULL,
	[enrolledAt] [datetime] NOT NULL,
 CONSTRAINT [PK_SeriesAssistant] PRIMARY KEY CLUSTERED
(
	[seriesId] ASC,
	[assistantId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[User]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[User](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[username] [varchar](100) NOT NULL,
	[passwordHash] [varchar](255) NOT NULL,
	[fullName] [varchar](255) NOT NULL,
	[email] [varchar](255) NOT NULL,
	[status] [varchar](10) NOT NULL,
	[createdAt] [datetime] NOT NULL,
	[updatedAt] [datetime] NOT NULL,
	[avatarUrl] [nvarchar](512) NULL,
 CONSTRAINT [PK_User] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_User_email] UNIQUE NONCLUSTERED
(
	[email] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_User_username] UNIQUE NONCLUSTERED
(
	[username] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[UserRole]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[UserRole](
	[userId] [bigint] NOT NULL,
	[roleId] [bigint] NOT NULL,
 CONSTRAINT [PK_UserRole] PRIMARY KEY CLUSTERED
(
	[userId] ASC,
	[roleId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[VoteEntry]    Script Date: 6/11/2026 10:09:13 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[VoteEntry](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[periodId] [bigint] NOT NULL,
	[seriesId] [bigint] NOT NULL,
	[boardMemberId] [bigint] NOT NULL,
	[voteCount] [int] NOT NULL,
	[readerCount] [int] NOT NULL,
	[revenue] [decimal](15, 2) NOT NULL,
	[submittedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_VoteEntry] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[TaskType] ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[TaskType](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[code] [varchar](50) NOT NULL,
	[displayName] [nvarchar](100) NOT NULL,
	[ratePerPage] [decimal](12, 2) NOT NULL,
	[isActive] [bit] NOT NULL,
	[createdAt] [datetime] NOT NULL,
	[updatedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_TaskType] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_TaskType_code] UNIQUE NONCLUSTERED
(
	[code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[PageTaskStage] ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[PageTaskStage](
	[taskId] [bigint] NOT NULL,
	[taskTypeCode] [varchar](50) NOT NULL,
 CONSTRAINT [PK_PageTaskStage] PRIMARY KEY CLUSTERED
(
	[taskId] ASC,
	[taskTypeCode] ASC
) ON [PRIMARY],
 CONSTRAINT [FK_PageTaskStage_task] FOREIGN KEY([taskId]) REFERENCES [dbo].[PageTask]([id]),
 CONSTRAINT [FK_PageTaskStage_type] FOREIGN KEY([taskTypeCode]) REFERENCES [dbo].[TaskType]([code])
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[PageTaskPageStage] ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[PageTaskPageStage](
    [taskId] [bigint] NOT NULL,
    [pageNumber] [int] NOT NULL,
    [taskTypeCode] [varchar](50) NOT NULL,
 CONSTRAINT [PK_PageTaskPageStage] PRIMARY KEY CLUSTERED
(
    [taskId] ASC,
    [pageNumber] ASC
) ON [PRIMARY],
 CONSTRAINT [FK_PageTaskPageStage_task] FOREIGN KEY([taskId]) REFERENCES [dbo].[PageTask]([id]),
 CONSTRAINT [FK_PageTaskPageStage_type] FOREIGN KEY([taskTypeCode]) REFERENCES [dbo].[TaskType]([code]),
 CONSTRAINT [CK_PageTaskPageStage_pageNumber] CHECK ([pageNumber] >= 1)
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[SalaryPeriod] ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[SalaryPeriod](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[mangakaId] [bigint] NOT NULL,
	[name] [varchar](255) NOT NULL,
	[status] [varchar](15) NOT NULL,
	[settledAt] [datetime] NULL,
	[createdAt] [datetime] NOT NULL,
 CONSTRAINT [PK_SalaryPeriod] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[AssistantSalaryRecord] ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[AssistantSalaryRecord](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[periodId] [bigint] NOT NULL,
	[assistantId] [bigint] NOT NULL,
	[totalTasksApproved] [int] NOT NULL,
	[totalPagesCompleted] [int] NOT NULL,
	[onTimeRate] [decimal](5, 2) NOT NULL,
	[kpiScore] [decimal](5, 2) NOT NULL,
	[grossSalary] [decimal](15, 2) NOT NULL,
	[bonus] [decimal](15, 2) NOT NULL,
	[deduction] [decimal](15, 2) NOT NULL,
	[netSalary] [decimal](15, 2) NOT NULL,
	[calculatedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_AssistantSalaryRecord] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_AssistantSalaryRecord_period_assistant] UNIQUE NONCLUSTERED
(
	[periodId] ASC,
	[assistantId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
GO

INSERT INTO [dbo].[TaskType]
	([code], [displayName], [ratePerPage], [isActive], [createdAt], [updatedAt])
VALUES
	('SKETCHING',  N'Sketching',  50000, 1, GETDATE(), GETDATE()),
	('INKING',     N'Inking',         45000, 1, GETDATE(), GETDATE()),
	('COLORING',   N'Coloring',         35000, 1, GETDATE(), GETDATE()),
	('SCREENTONE', N'Screentone',      30000, 1, GETDATE(), GETDATE()),
	('LETTERING',  N'Lettering', 20000, 1, GETDATE(), GETDATE()),
	('MIXED',      N'Mixed',        40000, 1, GETDATE(), GETDATE())
GO
/****** Object:  Index [IX_Annotation_ManuscriptPage]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Annotation_ManuscriptPage] ON [dbo].[Annotation]
(
	[manuscriptPageId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Annotation_ManuscriptVersion_Page]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Annotation_ManuscriptVersion_Page] ON [dbo].[Annotation]
(
	[manuscriptVersionId] ASC,
	[pageNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_AuditLog_actorId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_AuditLog_actorId] ON [dbo].[AuditLog]
(
	[actorId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_AuditLog_entityType_Id]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_AuditLog_entityType_Id] ON [dbo].[AuditLog]
(
	[entityType] ASC,
	[entityId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Chapter_seriesId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Chapter_seriesId] ON [dbo].[Chapter]
(
	[seriesId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_Chapter_status]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Chapter_status] ON [dbo].[Chapter]
(
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ChapterImage_chapterId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ChapterImage_chapterId] ON [dbo].[ChapterImage]
(
	[chapterId] ASC,
	[isActive] ASC,
	[pageNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ChapterImage_pageTaskId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ChapterImage_pageTaskId] ON [dbo].[ChapterImage]
(
	[pageTaskId] ASC
)
WHERE ([pageTaskId] IS NOT NULL)
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ChapterImage_uploadedBy]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ChapterImage_uploadedBy] ON [dbo].[ChapterImage]
(
	[uploadedBy] ASC,
	[uploadedAt] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_DecisionSession_openedAt]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_DecisionSession_openedAt] ON [dbo].[DecisionSession]
(
	[openedAt] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_DecisionSession_seriesId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_DecisionSession_seriesId] ON [dbo].[DecisionSession]
(
	[seriesId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_DecisionSession_seriesId_status]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_DecisionSession_seriesId_status] ON [dbo].[DecisionSession]
(
	[seriesId] ASC,
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [UX_DecisionSession_Open]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UX_DecisionSession_Open] ON [dbo].[DecisionSession]
(
	[seriesId] ASC
)
WHERE ([status]='OPEN')
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_DecisionVote_sessionId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_DecisionVote_sessionId] ON [dbo].[DecisionVote]
(
	[sessionId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [UQ_DecisionVote_session_voter]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UQ_DecisionVote_session_voter] ON [dbo].[DecisionVote]
(
	[sessionId] ASC,
	[voterId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [UX_DecisionVote_Session_Voter]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UX_DecisionVote_Session_Voter] ON [dbo].[DecisionVote]
(
	[sessionId] ASC,
	[voterId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_MangakaRankingRecord_periodId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_MangakaRankingRecord_periodId] ON [dbo].[MangakaRankingRecord]
(
	[periodId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Manuscript_chapterId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Manuscript_chapterId] ON [dbo].[Manuscript]
(
	[chapterId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ManuscriptPage_ManuscriptVersion_DisplayOrder]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ManuscriptPage_ManuscriptVersion_DisplayOrder] ON [dbo].[ManuscriptPage]
(
	[manuscriptVersionId] ASC,
	[displayOrder] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_ManuscriptVersion_Chapter_Status]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ManuscriptVersion_Chapter_Status] ON [dbo].[ManuscriptVersion]
(
	[chapterId] ASC,
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ManuscriptVersion_Chapter_Version]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ManuscriptVersion_Chapter_Version] ON [dbo].[ManuscriptVersion]
(
	[chapterId] ASC,
	[version] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ManuscriptVersion_PreviousVersion]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ManuscriptVersion_PreviousVersion] ON [dbo].[ManuscriptVersion]
(
	[previousVersionId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [UQ_ManuscriptVersion_ActiveWorkspace]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UQ_ManuscriptVersion_ActiveWorkspace] ON [dbo].[ManuscriptVersion]
(
	[chapterId] ASC
)
WHERE ([status] IN ('DRAFT', 'IN_PROGRESS', 'SUBMITTED_FOR_REVIEW', 'UNDER_REVIEW'))
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Notification_isRead]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Notification_isRead] ON [dbo].[Notification]
(
	[userId] ASC,
	[isRead] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Notification_userId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Notification_userId] ON [dbo].[Notification]
(
	[userId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Page_chapterId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Page_chapterId] ON [dbo].[Page]
(
	[chapterId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_PageTask_assistantId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_PageTask_assistantId] ON [dbo].[PageTask]
(
	[assistantId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_PageTask_chapterId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_PageTask_chapterId] ON [dbo].[PageTask]
(
	[chapterId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Proposal_assignedEditor]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Proposal_assignedEditor] ON [dbo].[Proposal]
(
	[assignedEditorId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Proposal_mangakaId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Proposal_mangakaId] ON [dbo].[Proposal]
(
	[mangakaId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_Proposal_status]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Proposal_status] ON [dbo].[Proposal]
(
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [UX_Proposal_one_draft_per_mangaka]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UX_Proposal_one_draft_per_mangaka] ON [dbo].[Proposal]
(
	[mangakaId] ASC
)
WHERE ([status]='DRAFT')
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_PBR_proposal_status]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_PBR_proposal_status] ON [dbo].[ProposalBoardRound]
(
	[proposalId] ASC,
	[submitAttemptNumber] ASC,
	[status] ASC,
	[roundNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_PH_boardRound]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_PH_boardRound] ON [dbo].[ProposalHistory]
(
	[boardRoundId] ASC,
	[actorRole] ASC,
	[actionType] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ProposalHistory_proposal]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ProposalHistory_proposal] ON [dbo].[ProposalHistory]
(
	[proposalId] ASC,
	[createdAt] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_RankingPeriod_status_endDate]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_RankingPeriod_status_endDate] ON [dbo].[RankingPeriod]
(
	[status] ASC,
	[endDate] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_RankingRecord_periodId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_RankingRecord_periodId] ON [dbo].[RankingRecord]
(
	[periodId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_RankingRecord_periodId_rankPosition]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_RankingRecord_periodId_rankPosition] ON [dbo].[RankingRecord]
(
	[periodId] ASC,
	[rankPosition] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_RankingRecord_seriesId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_RankingRecord_seriesId] ON [dbo].[RankingRecord]
(
	[seriesId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ReviewDecision_ManuscriptVersion]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ReviewDecision_ManuscriptVersion] ON [dbo].[ReviewDecision]
(
	[manuscriptVersionId] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ReviewDecision_Reviewer]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ReviewDecision_Reviewer] ON [dbo].[ReviewDecision]
(
	[reviewerId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ReviewTask_dueAt]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ReviewTask_dueAt] ON [dbo].[ReviewTask]
(
	[dueAt] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ReviewTask_reviewerId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ReviewTask_reviewerId] ON [dbo].[ReviewTask]
(
	[reviewerId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_ReviewTask_versionId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_ReviewTask_versionId] ON [dbo].[ReviewTask]
(
	[versionId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_Series_mangakaId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_Series_mangakaId] ON [dbo].[Series]
(
	[mangakaId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [UX_UserRole_single_admin]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UX_UserRole_single_admin] ON [dbo].[UserRole]
(
	[roleId] ASC
)
WHERE ([roleId]=(1))
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_VoteEntry_periodId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_VoteEntry_periodId] ON [dbo].[VoteEntry]
(
	[periodId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_VoteEntry_periodId_seriesId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_VoteEntry_periodId_seriesId] ON [dbo].[VoteEntry]
(
	[periodId] ASC,
	[seriesId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [IX_VoteEntry_seriesId]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE NONCLUSTERED INDEX [IX_VoteEntry_seriesId] ON [dbo].[VoteEntry]
(
	[seriesId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [UQ_VoteEntry_period_series_board]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UQ_VoteEntry_period_series_board] ON [dbo].[VoteEntry]
(
	[periodId] ASC,
	[seriesId] ASC,
	[boardMemberId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [UX_VoteEntry_Period_Series_Board]    Script Date: 6/11/2026 10:09:13 PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UX_VoteEntry_Period_Series_Board] ON [dbo].[VoteEntry]
(
	[periodId] ASC,
	[seriesId] ASC,
	[boardMemberId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
ALTER TABLE [dbo].[Annotation] ADD  CONSTRAINT [DF_Annotation_category]  DEFAULT ('OTHER') FOR [category]
GO
ALTER TABLE [dbo].[Annotation] ADD  CONSTRAINT [DF_Annotation_status]  DEFAULT ('OPEN') FOR [status]
GO
ALTER TABLE [dbo].[Annotation] ADD  CONSTRAINT [DF_Annotation_createdAt]  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[AuditLog] ADD  CONSTRAINT [DF_AuditLog_performedAt]  DEFAULT (getdate()) FOR [performedAt]
GO
ALTER TABLE [dbo].[Chapter] ADD  CONSTRAINT [DF_Chapter_status]  DEFAULT ('PLANNING') FOR [status]
GO
ALTER TABLE [dbo].[Chapter] ADD  CONSTRAINT [DF_Chapter_completionPct]  DEFAULT ((0.00)) FOR [completionPct]
GO
ALTER TABLE [dbo].[Chapter] ADD  CONSTRAINT [DF_Chapter_atRisk]  DEFAULT ((0)) FOR [atRisk]
GO
ALTER TABLE [dbo].[Chapter] ADD  CONSTRAINT [DF_Chapter_createdAt]  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[ChapterImage] ADD  CONSTRAINT [DF_ChapterImage_imageType]  DEFAULT ('PAGE') FOR [imageType]
GO
ALTER TABLE [dbo].[ChapterImage] ADD  CONSTRAINT [DF_ChapterImage_uploadedAt]  DEFAULT (getdate()) FOR [uploadedAt]
GO
ALTER TABLE [dbo].[ChapterImage] ADD  CONSTRAINT [DF_ChapterImage_isActive]  DEFAULT ((1)) FOR [isActive]
GO
ALTER TABLE [dbo].[DecisionSession] ADD  CONSTRAINT [DF_DecisionSession_status]  DEFAULT ('OPEN') FOR [status]
GO
ALTER TABLE [dbo].[DecisionSession] ADD  CONSTRAINT [DF_DecisionSession_openedAt]  DEFAULT (getdate()) FOR [openedAt]
GO
ALTER TABLE [dbo].[DecisionVote] ADD  CONSTRAINT [DF_DecisionVote_votedAt]  DEFAULT (getdate()) FOR [votedAt]
GO
ALTER TABLE [dbo].[MangakaAssistant] ADD  CONSTRAINT [DF_MA_enrolledAt]  DEFAULT (getdate()) FOR [enrolledAt]
GO
ALTER TABLE [dbo].[MangakaRankingRecord] ADD  DEFAULT ((0)) FOR [totalReads]
GO
ALTER TABLE [dbo].[MangakaRankingRecord] ADD  DEFAULT ((0)) FOR [totalRevenue]
GO
ALTER TABLE [dbo].[MangakaRankingRecord] ADD  DEFAULT ((0)) FOR [totalLikes]
GO
ALTER TABLE [dbo].[MangakaRankingRecord] ADD  DEFAULT (getdate()) FOR [calculatedAt]
GO
ALTER TABLE [dbo].[Manuscript] ADD  CONSTRAINT [DF_Manuscript_version]  DEFAULT ((1)) FOR [version]
GO
ALTER TABLE [dbo].[Manuscript] ADD  CONSTRAINT [DF_Manuscript_status]  DEFAULT ('SUBMITTED') FOR [status]
GO
ALTER TABLE [dbo].[Manuscript] ADD  CONSTRAINT [DF_Manuscript_submittedAt]  DEFAULT (getdate()) FOR [submittedAt]
GO
ALTER TABLE [dbo].[ManuscriptPage] ADD  DEFAULT (getdate()) FOR [snapshotCreatedAt]
GO
ALTER TABLE [dbo].[ManuscriptProductionLock] ADD  DEFAULT (getdate()) FOR [lockedAt]
GO
ALTER TABLE [dbo].[ManuscriptVersion] ADD  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[Notification] ADD  CONSTRAINT [DF_Notification_isRead]  DEFAULT ((0)) FOR [isRead]
GO
ALTER TABLE [dbo].[Notification] ADD  CONSTRAINT [DF_Notification_createdAt]  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[Page] ADD  CONSTRAINT [DF_Page_status]  DEFAULT ('EMPTY') FOR [status]
GO
ALTER TABLE [dbo].[Page] ADD  CONSTRAINT [DF_Page_createdAt]  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[PageTask] ADD  CONSTRAINT [DF_PageTask_status]  DEFAULT ('PENDING') FOR [status]
GO
ALTER TABLE [dbo].[PageTask] ADD  CONSTRAINT [DF_PageTask_rejectionCount]  DEFAULT ((0)) FOR [rejectionCount]
GO
ALTER TABLE [dbo].[PageTask] ADD  CONSTRAINT [DF_PageTask_priority]  DEFAULT ('NORMAL') FOR [priority]
GO
ALTER TABLE [dbo].[PageTask] ADD  CONSTRAINT [DF_PageTask_assignedAt]  DEFAULT (getdate()) FOR [assignedAt]
GO
ALTER TABLE [dbo].[PageTask] ADD  CONSTRAINT [DF_PageTask_updatedAt]  DEFAULT (getdate()) FOR [updatedAt]
GO
ALTER TABLE [dbo].[Proposal] ADD  CONSTRAINT [DF_Proposal_sampleFilePath]  DEFAULT ('') FOR [sampleFilePath]
GO
ALTER TABLE [dbo].[Proposal] ADD  CONSTRAINT [DF_Proposal_originalFileName]  DEFAULT ('') FOR [originalFileName]
GO
ALTER TABLE [dbo].[Proposal] ADD  CONSTRAINT [DF_Proposal_approxChapter]  DEFAULT ((1)) FOR [approximateChapter]
GO
ALTER TABLE [dbo].[Proposal] ADD  CONSTRAINT [DF_Proposal_status]  DEFAULT ('DRAFT') FOR [status]
GO
ALTER TABLE [dbo].[Proposal] ADD  CONSTRAINT [DF_Proposal_submitAttemptCount]  DEFAULT ((0)) FOR [submitAttemptCount]
GO
ALTER TABLE [dbo].[Proposal] ADD  CONSTRAINT [DF_Proposal_tantouReviewOverdue]  DEFAULT ((0)) FOR [tantouReviewOverdue]
GO
ALTER TABLE [dbo].[Proposal] ADD  CONSTRAINT [DF_Proposal_createdAt]  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[Proposal] ADD  CONSTRAINT [DF_Proposal_updatedAt]  DEFAULT (getdate()) FOR [updatedAt]
GO
ALTER TABLE [dbo].[ProposalHistory] ADD  CONSTRAINT [DF_PH_submitAttemptNumber]  DEFAULT ((0)) FOR [submitAttemptNumber]
GO
ALTER TABLE [dbo].[ProposalHistory] ADD  CONSTRAINT [DF_PH_createdAt]  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[RankingPeriod] ADD  CONSTRAINT [DF_RankingPeriod_status]  DEFAULT ('UPCOMING') FOR [status]
GO
ALTER TABLE [dbo].[RankingRecord] ADD  CONSTRAINT [DF_RankingRecord_isBottomTwenty]  DEFAULT ((0)) FOR [isBottomTwenty]
GO
ALTER TABLE [dbo].[RankingRecord] ADD  CONSTRAINT [DF_RankingRecord_calculatedAt]  DEFAULT (getdate()) FOR [calculatedAt]
GO
ALTER TABLE [dbo].[RankingRecord] ADD  DEFAULT ((0)) FOR [totalLikes]
GO
ALTER TABLE [dbo].[RankingRecord] ADD  DEFAULT ((0)) FOR [totalReads]
GO
ALTER TABLE [dbo].[ReviewDecision] ADD  DEFAULT (getdate()) FOR [decisionAt]
GO
ALTER TABLE [dbo].[Series] ADD  CONSTRAINT [DF_Series_status]  DEFAULT ('ACTIVE') FOR [status]
GO
ALTER TABLE [dbo].[Series] ADD  CONSTRAINT [DF_Series_createdAt]  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[User] ADD  CONSTRAINT [DF_User_status]  DEFAULT ('ACTIVE') FOR [status]
GO
ALTER TABLE [dbo].[User] ADD  CONSTRAINT [DF_User_createdAt]  DEFAULT (getdate()) FOR [createdAt]
GO
ALTER TABLE [dbo].[User] ADD  CONSTRAINT [DF_User_updatedAt]  DEFAULT (getdate()) FOR [updatedAt]
GO
ALTER TABLE [dbo].[VoteEntry] ADD  DEFAULT ((0)) FOR [revenue]
GO
ALTER TABLE [dbo].[VoteEntry] ADD  CONSTRAINT [DF_VoteEntry_submittedAt]  DEFAULT (getdate()) FOR [submittedAt]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [FK_Annotation_Editor] FOREIGN KEY([editorId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [FK_Annotation_Editor]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [FK_Annotation_ManuscriptPage] FOREIGN KEY([manuscriptPageId])
REFERENCES [dbo].[ManuscriptPage] ([id])
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [FK_Annotation_ManuscriptPage]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [FK_Annotation_ManuscriptVersion] FOREIGN KEY([manuscriptVersionId])
REFERENCES [dbo].[ManuscriptVersion] ([id])
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [FK_Annotation_ManuscriptVersion]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [FK_Annotation_Parent] FOREIGN KEY([parentAnnotationId])
REFERENCES [dbo].[Annotation] ([id])
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [FK_Annotation_Parent]
GO
ALTER TABLE [dbo].[AuditLog]  WITH CHECK ADD  CONSTRAINT [FK_AuditLog_Actor] FOREIGN KEY([actorId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[AuditLog] CHECK CONSTRAINT [FK_AuditLog_Actor]
GO
ALTER TABLE [dbo].[Chapter]  WITH CHECK ADD  CONSTRAINT [FK_Chapter_Series] FOREIGN KEY([seriesId])
REFERENCES [dbo].[Series] ([id])
GO
ALTER TABLE [dbo].[Chapter] CHECK CONSTRAINT [FK_Chapter_Series]
GO
ALTER TABLE [dbo].[ChapterImage]  WITH CHECK ADD  CONSTRAINT [FK_CI_Chapter] FOREIGN KEY([chapterId])
REFERENCES [dbo].[Chapter] ([id])
GO
ALTER TABLE [dbo].[ChapterImage] CHECK CONSTRAINT [FK_CI_Chapter]
GO
ALTER TABLE [dbo].[ChapterImage]  WITH CHECK ADD  CONSTRAINT [FK_CI_PageTask] FOREIGN KEY([pageTaskId])
REFERENCES [dbo].[PageTask] ([id])
GO
ALTER TABLE [dbo].[ChapterImage] CHECK CONSTRAINT [FK_CI_PageTask]
GO
ALTER TABLE [dbo].[ChapterImage]  WITH CHECK ADD  CONSTRAINT [FK_CI_UploadedBy] FOREIGN KEY([uploadedBy])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[ChapterImage] CHECK CONSTRAINT [FK_CI_UploadedBy]
GO
ALTER TABLE [dbo].[DecisionSession]  WITH CHECK ADD  CONSTRAINT [FK_DS_RankingRecord] FOREIGN KEY([rankingRecordId])
REFERENCES [dbo].[RankingRecord] ([id])
GO
ALTER TABLE [dbo].[DecisionSession] CHECK CONSTRAINT [FK_DS_RankingRecord]
GO
ALTER TABLE [dbo].[DecisionSession]  WITH CHECK ADD  CONSTRAINT [FK_DS_Series] FOREIGN KEY([seriesId])
REFERENCES [dbo].[Series] ([id])
GO
ALTER TABLE [dbo].[DecisionSession] CHECK CONSTRAINT [FK_DS_Series]
GO
ALTER TABLE [dbo].[DecisionVote]  WITH CHECK ADD  CONSTRAINT [FK_DV_Session] FOREIGN KEY([sessionId])
REFERENCES [dbo].[DecisionSession] ([id])
GO
ALTER TABLE [dbo].[DecisionVote] CHECK CONSTRAINT [FK_DV_Session]
GO
ALTER TABLE [dbo].[DecisionVote]  WITH CHECK ADD  CONSTRAINT [FK_DV_Voter] FOREIGN KEY([voterId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[DecisionVote] CHECK CONSTRAINT [FK_DV_Voter]
GO
ALTER TABLE [dbo].[MangakaAssistant]  WITH CHECK ADD  CONSTRAINT [FK_MA_Assistant] FOREIGN KEY([assistantId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[MangakaAssistant] CHECK CONSTRAINT [FK_MA_Assistant]
GO
ALTER TABLE [dbo].[MangakaAssistant]  WITH CHECK ADD  CONSTRAINT [FK_MA_Mangaka] FOREIGN KEY([mangakaId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[MangakaAssistant] CHECK CONSTRAINT [FK_MA_Mangaka]
GO
ALTER TABLE [dbo].[MangakaRankingRecord]  WITH CHECK ADD  CONSTRAINT [FK_MRR_Period] FOREIGN KEY([periodId])
REFERENCES [dbo].[RankingPeriod] ([id])
GO
ALTER TABLE [dbo].[MangakaRankingRecord] CHECK CONSTRAINT [FK_MRR_Period]
GO
ALTER TABLE [dbo].[MangakaRankingRecord]  WITH CHECK ADD  CONSTRAINT [FK_MRR_User] FOREIGN KEY([mangakaId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[MangakaRankingRecord] CHECK CONSTRAINT [FK_MRR_User]
GO
ALTER TABLE [dbo].[Manuscript]  WITH CHECK ADD  CONSTRAINT [FK_Manuscript_Chapter] FOREIGN KEY([chapterId])
REFERENCES [dbo].[Chapter] ([id])
GO
ALTER TABLE [dbo].[Manuscript] CHECK CONSTRAINT [FK_Manuscript_Chapter]
GO
ALTER TABLE [dbo].[ManuscriptPage]  WITH CHECK ADD  CONSTRAINT [FK_ManuscriptPage_ManuscriptVersion] FOREIGN KEY([manuscriptVersionId])
REFERENCES [dbo].[ManuscriptVersion] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[ManuscriptPage] CHECK CONSTRAINT [FK_ManuscriptPage_ManuscriptVersion]
GO
ALTER TABLE [dbo].[ManuscriptProductionLock]  WITH CHECK ADD  CONSTRAINT [FK_ManuscriptProductionLock_ManuscriptVersion] FOREIGN KEY([manuscriptVersionId])
REFERENCES [dbo].[ManuscriptVersion] ([id])
GO
ALTER TABLE [dbo].[ManuscriptProductionLock] CHECK CONSTRAINT [FK_ManuscriptProductionLock_ManuscriptVersion]
GO
ALTER TABLE [dbo].[ManuscriptVersion]  WITH CHECK ADD  CONSTRAINT [FK_ManuscriptVersion_Chapter] FOREIGN KEY([chapterId])
REFERENCES [dbo].[Chapter] ([id])
GO
ALTER TABLE [dbo].[ManuscriptVersion] CHECK CONSTRAINT [FK_ManuscriptVersion_Chapter]
GO
ALTER TABLE [dbo].[ManuscriptVersion]  WITH CHECK ADD  CONSTRAINT [FK_ManuscriptVersion_PreviousVersion] FOREIGN KEY([previousVersionId])
REFERENCES [dbo].[ManuscriptVersion] ([id])
GO
ALTER TABLE [dbo].[ManuscriptVersion] CHECK CONSTRAINT [FK_ManuscriptVersion_PreviousVersion]
GO
ALTER TABLE [dbo].[Notification]  WITH CHECK ADD  CONSTRAINT [FK_Notification_User] FOREIGN KEY([userId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[Notification] CHECK CONSTRAINT [FK_Notification_User]
GO
ALTER TABLE [dbo].[Page]  WITH CHECK ADD  CONSTRAINT [FK_Page_Chapter] FOREIGN KEY([chapterId])
REFERENCES [dbo].[Chapter] ([id])
GO
ALTER TABLE [dbo].[Page] CHECK CONSTRAINT [FK_Page_Chapter]
GO
ALTER TABLE [dbo].[PageTask]  WITH CHECK ADD  CONSTRAINT [FK_PageTask_Assistant] FOREIGN KEY([assistantId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[PageTask] CHECK CONSTRAINT [FK_PageTask_Assistant]
GO
ALTER TABLE [dbo].[PageTask]  WITH CHECK ADD  CONSTRAINT [FK_PageTask_Chapter] FOREIGN KEY([chapterId])
REFERENCES [dbo].[Chapter] ([id])
GO
ALTER TABLE [dbo].[PageTask] CHECK CONSTRAINT [FK_PageTask_Chapter]
GO
ALTER TABLE [dbo].[PageTask]  WITH CHECK ADD  CONSTRAINT [FK_PageTask_Page] FOREIGN KEY([pageId])
REFERENCES [dbo].[Page] ([id])
GO
ALTER TABLE [dbo].[PageTask] CHECK CONSTRAINT [FK_PageTask_Page]
GO
ALTER TABLE [dbo].[Proposal]  WITH CHECK ADD  CONSTRAINT [FK_Proposal_Editor] FOREIGN KEY([assignedEditorId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[Proposal] CHECK CONSTRAINT [FK_Proposal_Editor]
GO
ALTER TABLE [dbo].[Proposal]  WITH CHECK ADD  CONSTRAINT [FK_Proposal_Mangaka] FOREIGN KEY([mangakaId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[Proposal] CHECK CONSTRAINT [FK_Proposal_Mangaka]
GO
ALTER TABLE [dbo].[ProposalBoardRound]  WITH CHECK ADD  CONSTRAINT [FK_PBR_Proposal] FOREIGN KEY([proposalId])
REFERENCES [dbo].[Proposal] ([id])
GO
ALTER TABLE [dbo].[ProposalBoardRound] CHECK CONSTRAINT [FK_PBR_Proposal]
GO
ALTER TABLE [dbo].[ProposalBoardRoundVoter]  WITH CHECK ADD  CONSTRAINT [FK_PBRV_Round] FOREIGN KEY([roundId])
REFERENCES [dbo].[ProposalBoardRound] ([id])
GO
ALTER TABLE [dbo].[ProposalBoardRoundVoter] CHECK CONSTRAINT [FK_PBRV_Round]
GO
ALTER TABLE [dbo].[ProposalBoardRoundVoter]  WITH CHECK ADD  CONSTRAINT [FK_PBRV_Voter] FOREIGN KEY([voterId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[ProposalBoardRoundVoter] CHECK CONSTRAINT [FK_PBRV_Voter]
GO
ALTER TABLE [dbo].[ProposalHistory]  WITH CHECK ADD  CONSTRAINT [FK_PH_Actor] FOREIGN KEY([actorId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[ProposalHistory] CHECK CONSTRAINT [FK_PH_Actor]
GO
ALTER TABLE [dbo].[ProposalHistory]  WITH CHECK ADD  CONSTRAINT [FK_PH_BoardRound] FOREIGN KEY([boardRoundId])
REFERENCES [dbo].[ProposalBoardRound] ([id])
GO
ALTER TABLE [dbo].[ProposalHistory] CHECK CONSTRAINT [FK_PH_BoardRound]
GO
ALTER TABLE [dbo].[ProposalHistory]  WITH CHECK ADD  CONSTRAINT [FK_PH_Proposal] FOREIGN KEY([proposalId])
REFERENCES [dbo].[Proposal] ([id])
GO
ALTER TABLE [dbo].[ProposalHistory] CHECK CONSTRAINT [FK_PH_Proposal]
GO
ALTER TABLE [dbo].[RankingRecord]  WITH CHECK ADD  CONSTRAINT [FK_RR_Period] FOREIGN KEY([periodId])
REFERENCES [dbo].[RankingPeriod] ([id])
GO
ALTER TABLE [dbo].[RankingRecord] CHECK CONSTRAINT [FK_RR_Period]
GO
ALTER TABLE [dbo].[RankingRecord]  WITH CHECK ADD  CONSTRAINT [FK_RR_Series] FOREIGN KEY([seriesId])
REFERENCES [dbo].[Series] ([id])
GO
ALTER TABLE [dbo].[RankingRecord] CHECK CONSTRAINT [FK_RR_Series]
GO
ALTER TABLE [dbo].[ReviewDecision]  WITH CHECK ADD  CONSTRAINT [FK_ReviewDecision_ManuscriptVersion] FOREIGN KEY([manuscriptVersionId])
REFERENCES [dbo].[ManuscriptVersion] ([id])
GO
ALTER TABLE [dbo].[ReviewDecision] CHECK CONSTRAINT [FK_ReviewDecision_ManuscriptVersion]
GO
ALTER TABLE [dbo].[ReviewTask]  WITH CHECK ADD  CONSTRAINT [FK_ReviewTask_ManuscriptVersion] FOREIGN KEY([versionId])
REFERENCES [dbo].[ManuscriptVersion] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[ReviewTask] CHECK CONSTRAINT [FK_ReviewTask_ManuscriptVersion]
GO
ALTER TABLE [dbo].[ReviewTask]  WITH CHECK ADD  CONSTRAINT [FK_ReviewTask_User] FOREIGN KEY([reviewerId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[ReviewTask] CHECK CONSTRAINT [FK_ReviewTask_User]
GO
ALTER TABLE [dbo].[Series]  WITH CHECK ADD  CONSTRAINT [FK_Series_Mangaka] FOREIGN KEY([mangakaId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[Series] CHECK CONSTRAINT [FK_Series_Mangaka]
GO
ALTER TABLE [dbo].[Series]  WITH CHECK ADD  CONSTRAINT [FK_Series_Proposal] FOREIGN KEY([proposalId])
REFERENCES [dbo].[Proposal] ([id])
GO
ALTER TABLE [dbo].[Series] CHECK CONSTRAINT [FK_Series_Proposal]
GO
ALTER TABLE [dbo].[Series]  WITH CHECK ADD  CONSTRAINT [FK_Series_TantouEditor] FOREIGN KEY([tantouEditorId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[Series] CHECK CONSTRAINT [FK_Series_TantouEditor]
GO
ALTER TABLE [dbo].[SeriesAssistant]  WITH CHECK ADD  CONSTRAINT [FK_SeriesAssistant_Assistant] FOREIGN KEY([assistantId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[SeriesAssistant] CHECK CONSTRAINT [FK_SeriesAssistant_Assistant]
GO
ALTER TABLE [dbo].[SeriesAssistant]  WITH CHECK ADD  CONSTRAINT [FK_SeriesAssistant_Series] FOREIGN KEY([seriesId])
REFERENCES [dbo].[Series] ([id])
GO
ALTER TABLE [dbo].[SeriesAssistant] CHECK CONSTRAINT [FK_SeriesAssistant_Series]
GO
ALTER TABLE [dbo].[UserRole]  WITH CHECK ADD  CONSTRAINT [FK_UserRole_Role] FOREIGN KEY([roleId])
REFERENCES [dbo].[Role] ([id])
GO
ALTER TABLE [dbo].[UserRole] CHECK CONSTRAINT [FK_UserRole_Role]
GO
ALTER TABLE [dbo].[UserRole]  WITH CHECK ADD  CONSTRAINT [FK_UserRole_User] FOREIGN KEY([userId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[UserRole] CHECK CONSTRAINT [FK_UserRole_User]
GO
ALTER TABLE [dbo].[VoteEntry]  WITH CHECK ADD  CONSTRAINT [FK_VE_BoardMember] FOREIGN KEY([boardMemberId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[VoteEntry] CHECK CONSTRAINT [FK_VE_BoardMember]
GO
ALTER TABLE [dbo].[VoteEntry]  WITH CHECK ADD  CONSTRAINT [FK_VE_Period] FOREIGN KEY([periodId])
REFERENCES [dbo].[RankingPeriod] ([id])
GO
ALTER TABLE [dbo].[VoteEntry] CHECK CONSTRAINT [FK_VE_Period]
GO
ALTER TABLE [dbo].[VoteEntry]  WITH CHECK ADD  CONSTRAINT [FK_VE_Series] FOREIGN KEY([seriesId])
REFERENCES [dbo].[Series] ([id])
GO
ALTER TABLE [dbo].[VoteEntry] CHECK CONSTRAINT [FK_VE_Series]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [CK_Annotation_category] CHECK  (([category]='OTHER' OR [category]='PANELING' OR [category]='DIALOGUE' OR [category]='PACING' OR [category]='STORY' OR [category]='ART'))
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [CK_Annotation_category]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [CK_Annotation_Coordinates] CHECK  (([xPercent] IS NULL OR [xPercent]>=(0) AND [xPercent]<=(100)))
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [CK_Annotation_Coordinates]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [CK_Annotation_Coordinates_Height] CHECK  (([heightPercent] IS NULL OR [heightPercent]>(0) AND [heightPercent]<=(100)))
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [CK_Annotation_Coordinates_Height]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [CK_Annotation_Coordinates_Width] CHECK  (([widthPercent] IS NULL OR [widthPercent]>(0) AND [widthPercent]<=(100)))
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [CK_Annotation_Coordinates_Width]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [CK_Annotation_Coordinates_Y] CHECK  (([yPercent] IS NULL OR [yPercent]>=(0) AND [yPercent]<=(100)))
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [CK_Annotation_Coordinates_Y]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [CK_Annotation_pageNumber] CHECK  (([pageNumber]>=(1)))
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [CK_Annotation_pageNumber]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [CK_Annotation_Severity] CHECK  (([severity] IS NULL OR ([severity]='SUGGESTION' OR [severity]='LOW' OR [severity]='MEDIUM' OR [severity]='HIGH' OR [severity]='CRITICAL')))
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [CK_Annotation_Severity]
GO
ALTER TABLE [dbo].[Annotation]  WITH CHECK ADD  CONSTRAINT [CK_Annotation_status] CHECK  (([status]='RESOLVED' OR [status]='OPEN'))
GO
ALTER TABLE [dbo].[Annotation] CHECK CONSTRAINT [CK_Annotation_status]
GO
ALTER TABLE [dbo].[AuditLog]  WITH CHECK ADD  CONSTRAINT [CK_AuditLog_entityType] CHECK  (([entityType]='IMAGE' OR [entityType]='USER' OR [entityType]='DECISION' OR [entityType]='MANUSCRIPT' OR [entityType]='TASK' OR [entityType]='CHAPTER' OR [entityType]='PROPOSAL' OR [entityType]='RANKING_PERIOD' OR [entityType]='SERIES'))
GO
ALTER TABLE [dbo].[AuditLog] CHECK CONSTRAINT [CK_AuditLog_entityType]
GO
ALTER TABLE [dbo].[Chapter]  WITH CHECK ADD  CONSTRAINT [CK_Chapter_completionPct] CHECK  (([completionPct]>=(0.00) AND [completionPct]<=(100.00)))
GO
ALTER TABLE [dbo].[Chapter] CHECK CONSTRAINT [CK_Chapter_completionPct]
GO
ALTER TABLE [dbo].[Chapter]  WITH CHECK ADD  CONSTRAINT [CK_Chapter_status] CHECK  (([status]='REJECTED' OR [status]='APPROVED' OR [status]='EDITORIAL_REVIEW' OR [status]='COMPLETE' OR [status]='IN_PROGRESS' OR [status]='PLANNING'))
GO
ALTER TABLE [dbo].[Chapter] CHECK CONSTRAINT [CK_Chapter_status]
GO
ALTER TABLE [dbo].[ChapterImage]  WITH CHECK ADD  CONSTRAINT [CK_CI_fileSizeBytes] CHECK  (([fileSizeBytes] IS NULL OR [fileSizeBytes]>(0)))
GO
ALTER TABLE [dbo].[ChapterImage] CHECK CONSTRAINT [CK_CI_fileSizeBytes]
GO
ALTER TABLE [dbo].[ChapterImage]  WITH CHECK ADD  CONSTRAINT [CK_CI_imageType] CHECK  (([imageType]='REFERENCE' OR [imageType]='COVER' OR [imageType]='PAGE'))
GO
ALTER TABLE [dbo].[ChapterImage] CHECK CONSTRAINT [CK_CI_imageType]
GO
ALTER TABLE [dbo].[ChapterImage]  WITH CHECK ADD  CONSTRAINT [CK_CI_pageNumber] CHECK  (([pageNumber] IS NULL OR [pageNumber]>=(1)))
GO
ALTER TABLE [dbo].[ChapterImage] CHECK CONSTRAINT [CK_CI_pageNumber]
GO
ALTER TABLE [dbo].[DecisionSession]  WITH CHECK ADD  CONSTRAINT [CK_DS_result] CHECK  (([result] IS NULL OR ([result]='DEFERRED' OR [result]='CHANGE_TYPE' OR [result]='CANCEL' OR [result]='CONTINUE')))
GO
ALTER TABLE [dbo].[DecisionSession] CHECK CONSTRAINT [CK_DS_result]
GO
ALTER TABLE [dbo].[DecisionSession]  WITH CHECK ADD  CONSTRAINT [CK_DS_status] CHECK  (([status]='DEFERRED' OR [status]='CLOSED' OR [status]='OPEN'))
GO
ALTER TABLE [dbo].[DecisionSession] CHECK CONSTRAINT [CK_DS_status]
GO
ALTER TABLE [dbo].[DecisionVote]  WITH CHECK ADD  CONSTRAINT [CK_DV_decision] CHECK  (([decision]='CHANGE_TYPE' OR [decision]='CANCEL' OR [decision]='CONTINUE'))
GO
ALTER TABLE [dbo].[DecisionVote] CHECK CONSTRAINT [CK_DV_decision]
GO
ALTER TABLE [dbo].[Manuscript]  WITH CHECK ADD  CONSTRAINT [CK_Manuscript_fileExtension] CHECK  (([fileExtension] IS NULL OR ([fileExtension]='cbz' OR [fileExtension]='rar' OR [fileExtension]='zip' OR [fileExtension]='pdf')))
GO
ALTER TABLE [dbo].[Manuscript] CHECK CONSTRAINT [CK_Manuscript_fileExtension]
GO
ALTER TABLE [dbo].[Manuscript]  WITH CHECK ADD  CONSTRAINT [CK_Manuscript_fileSize] CHECK  (([fileSize] IS NULL OR [fileSize]>(0) AND [fileSize]<=(52428800)))
GO
ALTER TABLE [dbo].[Manuscript] CHECK CONSTRAINT [CK_Manuscript_fileSize]
GO
ALTER TABLE [dbo].[Manuscript]  WITH CHECK ADD  CONSTRAINT [CK_Manuscript_status] CHECK  (([status]='ARCHIVED' OR [status]='REJECTED' OR [status]='APPROVED' OR [status]='REVISION_REQUIRED' OR [status]='UNDER_REVIEW' OR [status]='SUBMITTED' OR [status]='DRAFT'))
GO
ALTER TABLE [dbo].[Manuscript] CHECK CONSTRAINT [CK_Manuscript_status]
GO
ALTER TABLE [dbo].[Manuscript]  WITH CHECK ADD  CONSTRAINT [CK_Manuscript_version] CHECK  (([version]>=(1)))
GO
ALTER TABLE [dbo].[Manuscript] CHECK CONSTRAINT [CK_Manuscript_version]
GO
ALTER TABLE [dbo].[ManuscriptVersion]  WITH CHECK ADD  CONSTRAINT [CK_ManuscriptVersion_Status] CHECK  (([status]='ARCHIVED' OR [status]='REJECTED' OR [status]='PUBLISHED' OR [status]='APPROVED' OR [status]='UNDER_REVIEW' OR [status]='SUBMITTED_FOR_REVIEW' OR [status]='IN_PROGRESS' OR [status]='DRAFT'))
GO
ALTER TABLE [dbo].[ManuscriptVersion] CHECK CONSTRAINT [CK_ManuscriptVersion_Status]
GO
ALTER TABLE [dbo].[Notification]  WITH CHECK ADD  CONSTRAINT [CK_Notification_refType] CHECK  (([referenceType] IS NULL OR ([referenceType]='SERIES' OR [referenceType]='DECISION' OR [referenceType]='PROPOSAL' OR [referenceType]='MANUSCRIPT' OR [referenceType]='CHAPTER' OR [referenceType]='TASK')))
GO
ALTER TABLE [dbo].[Notification] CHECK CONSTRAINT [CK_Notification_refType]
GO
ALTER TABLE [dbo].[Page]  WITH CHECK ADD  CONSTRAINT [CK_Page_pageNumber] CHECK  (([pageNumber]>=(1)))
GO
ALTER TABLE [dbo].[Page] CHECK CONSTRAINT [CK_Page_pageNumber]
GO
ALTER TABLE [dbo].[Page]  WITH CHECK ADD  CONSTRAINT [CK_Page_status] CHECK  (([status]='APPROVED' OR [status]='SUBMITTED' OR [status]='IN_PROGRESS' OR [status]='EMPTY'))
GO
ALTER TABLE [dbo].[Page] CHECK CONSTRAINT [CK_Page_status]
GO
ALTER TABLE [dbo].[PageTask]  WITH CHECK ADD  CONSTRAINT [CK_PageTask_pageRange] CHECK  (([pageRangeEnd]>=[pageRangeStart]))
GO
ALTER TABLE [dbo].[PageTask] CHECK CONSTRAINT [CK_PageTask_pageRange]
GO
ALTER TABLE [dbo].[PageTask]  WITH CHECK ADD  CONSTRAINT [CK_PageTask_status] CHECK  (([status]='CANCELLED' OR [status]='REASSIGNED' OR [status]='DELETED' OR [status]='OVERDUE' OR [status]='REJECTED' OR [status]='APPROVED' OR [status]='SUBMITTED' OR [status]='IN_PROGRESS' OR [status]='PENDING'))
GO
ALTER TABLE [dbo].[PageTask] CHECK CONSTRAINT [CK_PageTask_status]
GO
ALTER TABLE [dbo].[Proposal]  WITH CHECK ADD  CONSTRAINT [CK_Proposal_approxChapter] CHECK  (([approximateChapter]>=(1)))
GO
ALTER TABLE [dbo].[Proposal] CHECK CONSTRAINT [CK_Proposal_approxChapter]
GO
ALTER TABLE [dbo].[Proposal]  WITH CHECK ADD  CONSTRAINT [CK_Proposal_status] CHECK  (([status]='REJECTED' OR [status]='APPROVED' OR [status]='REVISION_REQUESTED' OR [status]='BOARD_REVIEW' OR [status]='UNDER_REVIEW' OR [status]='DRAFT'))
GO
ALTER TABLE [dbo].[Proposal] CHECK CONSTRAINT [CK_Proposal_status]
GO
ALTER TABLE [dbo].[Proposal]  WITH CHECK ADD  CONSTRAINT [CK_Proposal_submitAttempts] CHECK  (([submitAttemptCount]>=(0) AND [submitAttemptCount]<=(10)))
GO
ALTER TABLE [dbo].[Proposal] CHECK CONSTRAINT [CK_Proposal_submitAttempts]
GO
ALTER TABLE [dbo].[ProposalBoardRound]  WITH CHECK ADD  CONSTRAINT [CK_PBR_status] CHECK  (([status]='CLOSED' OR [status]='OPEN'))
GO
ALTER TABLE [dbo].[ProposalBoardRound] CHECK CONSTRAINT [CK_PBR_status]
GO
ALTER TABLE [dbo].[ProposalHistory]  WITH CHECK ADD  CONSTRAINT [CK_PH_actionType] CHECK  (([actionType]='RESUBMITTED' OR [actionType]='REVISE_REQUESTED' OR [actionType]='REJECTED' OR [actionType]='APPROVED' OR [actionType]='ASSIGNED_EDITOR' OR [actionType]='SUBMITTED' OR [actionType]='UPDATED' OR [actionType]='CREATED'))
GO
ALTER TABLE [dbo].[ProposalHistory] CHECK CONSTRAINT [CK_PH_actionType]
GO
ALTER TABLE [dbo].[ProposalHistory]  WITH CHECK ADD  CONSTRAINT [CK_PH_submitAttempt] CHECK  (([submitAttemptNumber]>=(0) AND [submitAttemptNumber]<=(2)))
GO
ALTER TABLE [dbo].[ProposalHistory] CHECK CONSTRAINT [CK_PH_submitAttempt]
GO
ALTER TABLE [dbo].[RankingPeriod]  WITH CHECK ADD  CONSTRAINT [CK_RankingPeriod_dates] CHECK  (([endDate]>[startDate]))
GO
ALTER TABLE [dbo].[RankingPeriod] CHECK CONSTRAINT [CK_RankingPeriod_dates]
GO
ALTER TABLE [dbo].[RankingPeriod]  WITH CHECK ADD  CONSTRAINT [CK_RankingPeriod_status] CHECK  (([status]='CALCULATED' OR [status]='CALCULATING' OR [status]='CLOSED' OR [status]='OPEN' OR [status]='UPCOMING'))
GO
ALTER TABLE [dbo].[RankingPeriod] CHECK CONSTRAINT [CK_RankingPeriod_status]
GO
ALTER TABLE [dbo].[RankingRecord]  WITH CHECK ADD  CONSTRAINT [CK_RR_rankPosition] CHECK  (([rankPosition]>=(1)))
GO
ALTER TABLE [dbo].[RankingRecord] CHECK CONSTRAINT [CK_RR_rankPosition]
GO
ALTER TABLE [dbo].[RankingRecord]  WITH CHECK ADD  CONSTRAINT [CK_RR_rankScore] CHECK  (([rankScore]>=(0)))
GO
ALTER TABLE [dbo].[RankingRecord] CHECK CONSTRAINT [CK_RR_rankScore]
GO
ALTER TABLE [dbo].[ReviewDecision]  WITH CHECK ADD  CONSTRAINT [CK_ReviewDecision_DecisionType] CHECK  (([decisionType]='REJECT' OR [decisionType]='APPROVE'))
GO
ALTER TABLE [dbo].[ReviewDecision] CHECK CONSTRAINT [CK_ReviewDecision_DecisionType]
GO
ALTER TABLE [dbo].[ReviewTask]  WITH CHECK ADD  CONSTRAINT [CK_ReviewTask_reviewStatus] CHECK  (([reviewStatus]='ASSIGNED' OR [reviewStatus]='IN_PROGRESS' OR [reviewStatus]='COMPLETED' OR [reviewStatus]='OVERDUE'))
GO
ALTER TABLE [dbo].[ReviewTask] CHECK CONSTRAINT [CK_ReviewTask_reviewStatus]
GO
ALTER TABLE [dbo].[Role]  WITH CHECK ADD  CONSTRAINT [CK_Role_name] CHECK  (([name]='EDITORIAL_BOARD' OR [name]='TANTOU_EDITOR' OR [name]='ASSISTANT' OR [name]='MANGAKA' OR [name]='ADMIN'))
GO
ALTER TABLE [dbo].[Role] CHECK CONSTRAINT [CK_Role_name]
GO
ALTER TABLE [dbo].[Series]  WITH CHECK ADD  CONSTRAINT [CK_Series_status] CHECK  (([status]='CANCELLED' OR [status]='ACTIVE'))
GO
ALTER TABLE [dbo].[Series] CHECK CONSTRAINT [CK_Series_status]
GO
ALTER TABLE [dbo].[User]  WITH CHECK ADD  CONSTRAINT [CK_User_status] CHECK  (([status]='INACTIVE' OR [status]='ACTIVE'))
GO
ALTER TABLE [dbo].[User] CHECK CONSTRAINT [CK_User_status]
GO
ALTER TABLE [dbo].[VoteEntry]  WITH CHECK ADD  CONSTRAINT [CK_VE_readerCount] CHECK  (([readerCount]>(0)))
GO
ALTER TABLE [dbo].[VoteEntry] CHECK CONSTRAINT [CK_VE_readerCount]
GO
ALTER TABLE [dbo].[VoteEntry]  WITH CHECK ADD  CONSTRAINT [CK_VE_voteCount] CHECK  (([voteCount]>=(0)))
GO
ALTER TABLE [dbo].[VoteEntry] CHECK CONSTRAINT [CK_VE_voteCount]
GO

/* Migration for databases created before PageTask supported multiple stages. */
IF OBJECT_ID(N'[dbo].[PageTaskStage]', N'U') IS NULL
BEGIN
	CREATE TABLE [dbo].[PageTaskStage](
		[taskId] [bigint] NOT NULL,
		[taskTypeCode] [varchar](50) NOT NULL,
		CONSTRAINT [PK_PageTaskStage] PRIMARY KEY ([taskId], [taskTypeCode]),
		CONSTRAINT [FK_PageTaskStage_task] FOREIGN KEY ([taskId]) REFERENCES [dbo].[PageTask]([id]),
		CONSTRAINT [FK_PageTaskStage_type] FOREIGN KEY ([taskTypeCode]) REFERENCES [dbo].[TaskType]([code])
	);
END
GO

IF COL_LENGTH('dbo.PageTask', 'taskType') IS NOT NULL
BEGIN
	EXEC(N'
		INSERT INTO [dbo].[PageTaskStage] ([taskId], [taskTypeCode])
		SELECT t.[id], UPPER(t.[taskType])
		FROM [dbo].[PageTask] t
		JOIN [dbo].[TaskType] tt ON tt.[code] = UPPER(t.[taskType])
		WHERE NULLIF(LTRIM(RTRIM(t.[taskType])), '''') IS NOT NULL
		  AND NOT EXISTS (
			  SELECT 1 FROM [dbo].[PageTaskStage] pts
			  WHERE pts.[taskId] = t.[id]
			    AND pts.[taskTypeCode] = UPPER(t.[taskType])
		  );
		ALTER TABLE [dbo].[PageTask] DROP COLUMN [taskType];
	');
END
GO
/* Normalize TaskType display names for existing databases with mojibake values. */
UPDATE [dbo].[TaskType]
SET [displayName] = N'Sketching', [updatedAt] = GETDATE()
WHERE [code] = 'SKETCHING'
GO
UPDATE [dbo].[TaskType]
SET [displayName] = N'Inking', [updatedAt] = GETDATE()
WHERE [code] = 'INKING'
GO
UPDATE [dbo].[TaskType]
SET [displayName] = N'Coloring', [updatedAt] = GETDATE()
WHERE [code] = 'COLORING'
GO
UPDATE [dbo].[TaskType]
SET [displayName] = N'Screentone', [updatedAt] = GETDATE()
WHERE [code] = 'SCREENTONE'
GO
UPDATE [dbo].[TaskType]
SET [displayName] = N'Lettering', [updatedAt] = GETDATE()
WHERE [code] = 'LETTERING'
GO
UPDATE [dbo].[TaskType]
SET [displayName] = N'Mixed', [updatedAt] = GETDATE()
WHERE [code] = 'MIXED'
GO
/* Salary tracking migration for existing databases. */
IF COL_LENGTH('dbo.PageTask', 'isSalaried') IS NULL
BEGIN
    ALTER TABLE [dbo].[PageTask] ADD [isSalaried] BIT NOT NULL
        CONSTRAINT [DF_PageTask_isSalaried] DEFAULT ((0)) WITH VALUES;
END
GO
IF COL_LENGTH('dbo.SalaryPeriod', 'startDate') IS NOT NULL
BEGIN
    ALTER TABLE [dbo].[SalaryPeriod] DROP COLUMN [startDate];
END
GO
IF COL_LENGTH('dbo.SalaryPeriod', 'endDate') IS NOT NULL
BEGIN
    ALTER TABLE [dbo].[SalaryPeriod] DROP COLUMN [endDate];
END
GO
/* Image pHash dedup + submission history migration for existing databases. */
IF COL_LENGTH('dbo.ChapterImage', 'imagePhash') IS NULL
BEGIN
    ALTER TABLE [dbo].[ChapterImage] ADD [imagePhash] [char](16) NULL;
END
GO
/* Drop dead ChapterImage columns: imageUrl was a computed column duplicating fileUrl,
   and pageId was never populated or read anywhere in the app. */
IF COL_LENGTH('dbo.ChapterImage', 'imageUrl') IS NOT NULL
BEGIN
    ALTER TABLE [dbo].[ChapterImage] DROP COLUMN [imageUrl];
END
GO
IF OBJECT_ID('dbo.FK_ChapterImage_Page', 'F') IS NOT NULL
BEGIN
    ALTER TABLE [dbo].[ChapterImage] DROP CONSTRAINT [FK_ChapterImage_Page];
END
GO
IF COL_LENGTH('dbo.ChapterImage', 'pageId') IS NOT NULL
BEGIN
    ALTER TABLE [dbo].[ChapterImage] DROP COLUMN [pageId];
END
GO
IF OBJECT_ID(N'[dbo].[TaskReviewHistory]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[TaskReviewHistory](
        [id] [bigint] IDENTITY(1,1) NOT NULL,
        [taskId] [bigint] NOT NULL,
        [roundNumber] [int] NOT NULL,
        [submittedAt] [datetime] NOT NULL,
        [submittedBy] [bigint] NOT NULL,
        [decision] [varchar](20) NULL,
        [reviewedAt] [datetime] NULL,
        [reviewedBy] [bigint] NULL,
        [reviewComment] [nvarchar](300) NULL,
        [imageIdsSnapshot] [nvarchar](500) NULL,
        CONSTRAINT [PK_TaskReviewHistory] PRIMARY KEY CLUSTERED ([id] ASC)
    );
    CREATE INDEX [IX_TaskReviewHistory_taskId] ON [dbo].[TaskReviewHistory]([taskId] ASC, [roundNumber] DESC);
END
GO
/* Page image/stage revision history (chapter workspace) for rollback. */
IF OBJECT_ID(N'[dbo].[PageRevision]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[PageRevision](
        [id] [bigint] IDENTITY(1,1) NOT NULL,
        [pageId] [bigint] NOT NULL,
        [imageUrl] [varchar](512) NULL,
        [completedStage] [varchar](30) NULL,
        [changedBy] [bigint] NULL,
        [changedAt] [datetime] NOT NULL,
        [source] [varchar](20) NOT NULL,
        [imagePhash] [char](16) NULL,
        CONSTRAINT [PK_PageRevision] PRIMARY KEY CLUSTERED ([id] ASC)
    );
    CREATE INDEX [IX_PageRevision_pageId] ON [dbo].[PageRevision]([pageId] ASC, [id] DESC);
END
GO
IF OBJECT_ID(N'[dbo].[PageRevision]', N'U') IS NOT NULL AND COL_LENGTH('dbo.PageRevision', 'imagePhash') IS NULL
BEGIN
    ALTER TABLE [dbo].[PageRevision] ADD [imagePhash] [char](16) NULL;
END
GO
/* Per-page task stage migration for salary calculation. */
IF OBJECT_ID(N'[dbo].[PageTaskPageStage]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[PageTaskPageStage](
        [taskId] [bigint] NOT NULL,
        [pageNumber] [int] NOT NULL,
        [taskTypeCode] [varchar](50) NOT NULL,
        CONSTRAINT [PK_PageTaskPageStage] PRIMARY KEY ([taskId], [pageNumber]),
        CONSTRAINT [FK_PageTaskPageStage_task] FOREIGN KEY ([taskId]) REFERENCES [dbo].[PageTask]([id]),
        CONSTRAINT [FK_PageTaskPageStage_type] FOREIGN KEY ([taskTypeCode]) REFERENCES [dbo].[TaskType]([code]),
        CONSTRAINT [CK_PageTaskPageStage_pageNumber] CHECK ([pageNumber] >= 1)
    );
END
GO

INSERT INTO [dbo].[PageTaskPageStage] ([taskId], [pageNumber], [taskTypeCode])
SELECT t.[id], p.[pageNumber],
       CASE
           WHEN stageRow.[taskTypeCode] IS NOT NULL AND stageRow.[taskTypeCode] <> 'MIXED'
               THEN stageRow.[taskTypeCode]
           WHEN UPPER(t.[status]) = 'APPROVED'
               THEN COALESCE(NULLIF(UPPER(p.[completedStage]), ''), 'SKETCHING')
           WHEN p.[completedStage] IS NULL OR LTRIM(RTRIM(p.[completedStage])) = '' THEN 'SKETCHING'
           WHEN UPPER(p.[completedStage]) = 'SKETCHING' THEN 'INKING'
           WHEN UPPER(p.[completedStage]) = 'INKING' THEN 'COLORING'
           WHEN UPPER(p.[completedStage]) = 'COLORING' THEN 'SCREENTONE'
           WHEN UPPER(p.[completedStage]) = 'SCREENTONE' THEN 'LETTERING'
           ELSE 'LETTERING'
       END
FROM [dbo].[PageTask] t
JOIN [dbo].[Page] p
  ON p.[chapterId] = t.[chapterId]
 AND p.[pageNumber] BETWEEN t.[pageRangeStart] AND t.[pageRangeEnd]
OUTER APPLY (
    SELECT TOP 1 pts.[taskTypeCode]
    FROM [dbo].[PageTaskStage] pts
    WHERE pts.[taskId] = t.[id]
    ORDER BY CASE WHEN pts.[taskTypeCode] = 'MIXED' THEN 1 ELSE 0 END, pts.[taskTypeCode]
) stageRow
WHERE NOT EXISTS (
    SELECT 1 FROM [dbo].[PageTaskPageStage] existing
    WHERE existing.[taskId] = t.[id]
      AND existing.[pageNumber] = p.[pageNumber]
);
GO
/****** Object:  Table [dbo].[RankingCsvUpload] ******/
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[RankingCsvUpload](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[periodId] [bigint] NOT NULL,
	[boardMemberId] [bigint] NOT NULL,
	[csvFileName] [varchar](255) NOT NULL,
	[csvContent] [nvarchar](max) NOT NULL,
	[uploadedAt] [datetime] NOT NULL,
 CONSTRAINT [PK_RankingCsvUpload] PRIMARY KEY CLUSTERED
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Index [UIX_RankingCsvUpload_Period_Board] ******/
CREATE UNIQUE NONCLUSTERED INDEX [UIX_RankingCsvUpload_Period_Board] ON [dbo].[RankingCsvUpload]
(
	[periodId] ASC,
	[boardMemberId] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
ALTER TABLE [dbo].[RankingCsvUpload]  WITH CHECK ADD  CONSTRAINT [FK_RankingCsvUpload_RankingPeriod] FOREIGN KEY([periodId])
REFERENCES [dbo].[RankingPeriod] ([id])
GO
ALTER TABLE [dbo].[RankingCsvUpload] CHECK CONSTRAINT [FK_RankingCsvUpload_RankingPeriod]
GO
ALTER TABLE [dbo].[RankingCsvUpload]  WITH CHECK ADD  CONSTRAINT [FK_RankingCsvUpload_User] FOREIGN KEY([boardMemberId])
REFERENCES [dbo].[User] ([id])
GO
ALTER TABLE [dbo].[RankingCsvUpload] CHECK CONSTRAINT [FK_RankingCsvUpload_User]
GO

/* Salary tables were missing FKs entirely (only PK/UNIQUE) - add them so orphaned
   rows can't silently accumulate if a referenced user/period disappears. */
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_SalaryPeriod_Mangaka')
BEGIN
    ALTER TABLE [dbo].[SalaryPeriod]  WITH CHECK ADD  CONSTRAINT [FK_SalaryPeriod_Mangaka] FOREIGN KEY([mangakaId])
    REFERENCES [dbo].[User] ([id]);
    ALTER TABLE [dbo].[SalaryPeriod] CHECK CONSTRAINT [FK_SalaryPeriod_Mangaka];
END
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ASR_Period')
BEGIN
    ALTER TABLE [dbo].[AssistantSalaryRecord]  WITH CHECK ADD  CONSTRAINT [FK_ASR_Period] FOREIGN KEY([periodId])
    REFERENCES [dbo].[SalaryPeriod] ([id]);
    ALTER TABLE [dbo].[AssistantSalaryRecord] CHECK CONSTRAINT [FK_ASR_Period];
END
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ASR_Assistant')
BEGIN
    ALTER TABLE [dbo].[AssistantSalaryRecord]  WITH CHECK ADD  CONSTRAINT [FK_ASR_Assistant] FOREIGN KEY([assistantId])
    REFERENCES [dbo].[User] ([id]);
    ALTER TABLE [dbo].[AssistantSalaryRecord] CHECK CONSTRAINT [FK_ASR_Assistant];
END
GO



GO
GO
