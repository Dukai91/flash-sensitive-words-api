-- Run as a database administrator after provisioning the flash_runtime LOGIN
-- through your secrets/identity tooling. No password belongs in this file.
-- Execute against the sensitive_words application database after Flyway migration.
IF USER_ID(N'flash_runtime') IS NULL
    CREATE USER flash_runtime FOR LOGIN flash_runtime;

GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.sensitive_words TO flash_runtime;
GRANT SELECT, UPDATE ON OBJECT::dbo.vocabulary_configuration TO flash_runtime;
-- Do not grant db_owner, db_ddladmin or permission to edit flyway_schema_history.
-- Network policy and production JWT scopes control which callers can invoke CRUD.
