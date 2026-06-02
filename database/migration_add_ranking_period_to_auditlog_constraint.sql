-- Migration: Add RANKING_PERIOD and SERIES to CK_AuditLog_entityType constraint
-- This migration updates the check constraint to allow audit logs for ranking period and series actions

-- Drop existing constraint
IF EXISTS (SELECT * FROM sys.check_constraints WHERE name = 'CK_AuditLog_entityType' AND parent_object_id = OBJECT_ID('AuditLog'))
BEGIN
    ALTER TABLE [dbo].[AuditLog] DROP CONSTRAINT [CK_AuditLog_entityType];
    PRINT 'Dropped existing CK_AuditLog_entityType constraint';
END
GO

-- Add updated check constraint with RANKING_PERIOD and SERIES included
IF NOT EXISTS (SELECT * FROM sys.check_constraints WHERE name = 'CK_AuditLog_entityType' AND parent_object_id = OBJECT_ID('AuditLog'))
BEGIN
    ALTER TABLE [dbo].[AuditLog] WITH CHECK ADD CONSTRAINT [CK_AuditLog_entityType] 
    CHECK (([entityType]='IMAGE' OR [entityType]='USER' OR [entityType]='DECISION' OR [entityType]='MANUSCRIPT' OR [entityType]='TASK' OR [entityType]='CHAPTER' OR [entityType]='PROPOSAL' OR [entityType]='RANKING_PERIOD' OR [entityType]='SERIES'));
    PRINT 'Created updated CK_AuditLog_entityType constraint with RANKING_PERIOD and SERIES';
END
GO
