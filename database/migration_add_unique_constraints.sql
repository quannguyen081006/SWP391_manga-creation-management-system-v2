-- Migration: Add unique constraints for business rule enforcement
-- BR-RNK-06: Prevent duplicate vote entries
-- BR-DEC-07: Prevent duplicate decision votes

-- Add unique constraint on VoteEntry to prevent duplicate submissions
-- BR-RNK-06: Board Member cannot submit duplicate vote for same series in same period
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'UQ_VoteEntry_period_series_board' AND object_id = OBJECT_ID('VoteEntry'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UQ_VoteEntry_period_series_board] 
    ON [dbo].[VoteEntry] ([periodId], [seriesId], [boardMemberId]);
    PRINT 'Created unique constraint on VoteEntry(periodId, seriesId, boardMemberId)';
END
GO

-- Add unique constraint on DecisionVote to prevent duplicate votes
-- BR-DEC-07: Board Member cannot vote twice in same decision session
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'UQ_DecisionVote_session_voter' AND object_id = OBJECT_ID('DecisionVote'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UQ_DecisionVote_session_voter] 
    ON [dbo].[DecisionVote] ([sessionId], [voterId]);
    PRINT 'Created unique constraint on DecisionVote(sessionId, voterId)';
END
GO

-- Update AuditLog check constraint to include RANKING_PERIOD entity type if needed
-- BR-RNK-09: Allow audit logs for ranking period actions
IF EXISTS (SELECT * FROM sys.check_constraints WHERE name = 'CK_AuditLog_entityType' AND parent_object_id = OBJECT_ID('AuditLog'))
BEGIN
    -- Drop existing constraint
    ALTER TABLE [dbo].[AuditLog] DROP CONSTRAINT [CK_AuditLog_entityType];
    PRINT 'Dropped existing CK_AuditLog_entityType constraint';
END
GO

-- Add updated check constraint with RANKING_PERIOD included
IF NOT EXISTS (SELECT * FROM sys.check_constraints WHERE name = 'CK_AuditLog_entityType' AND parent_object_id = OBJECT_ID('AuditLog'))
BEGIN
    ALTER TABLE [dbo].[AuditLog] WITH CHECK ADD CONSTRAINT [CK_AuditLog_entityType] 
    CHECK (([entityType]='DECISION' OR [entityType]='PROPOSAL' OR [entityType]='SERIES' OR [entityType]='USER' OR [entityType]='RANKING_PERIOD'));
    PRINT 'Created updated CK_AuditLog_entityType constraint with RANKING_PERIOD';
END
GO
