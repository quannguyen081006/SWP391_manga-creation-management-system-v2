-- ============================================================
-- Migration: add Proposal.sampleFileHash for duplicate sample-file detection
-- ------------------------------------------------------------
-- Stores the SHA-256 (hex, 64 chars) of each proposal's uploaded sample file so
-- create / edit / resubmit can reject a file whose content is identical to an
-- existing proposal's file. Safe to run more than once.
-- ============================================================
IF COL_LENGTH('dbo.Proposal', 'sampleFileHash') IS NULL
BEGIN
    ALTER TABLE [dbo].[Proposal] ADD [sampleFileHash] [varchar](64) NULL;
END
GO

-- Speeds up the duplicate lookup (WHERE sampleFileHash = ?).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Proposal_sampleFileHash' AND object_id = OBJECT_ID('dbo.Proposal'))
BEGIN
    CREATE INDEX [IX_Proposal_sampleFileHash] ON [dbo].[Proposal] ([sampleFileHash]);
END
GO
