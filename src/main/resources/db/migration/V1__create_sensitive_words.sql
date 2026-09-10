CREATE TABLE dbo.sensitive_words (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_sensitive_words PRIMARY KEY,
    word NVARCHAR(200) NOT NULL,
    normalized_word NVARCHAR(200) COLLATE Latin1_General_100_BIN2 NOT NULL,
    created_at DATETIME2(6) NOT NULL CONSTRAINT df_sensitive_words_created DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(6) NOT NULL CONSTRAINT df_sensitive_words_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_sensitive_words_normalized UNIQUE (normalized_word),
    CONSTRAINT ck_sensitive_words_not_blank CHECK (LEN(LTRIM(RTRIM(word))) > 0),
    CONSTRAINT ck_sensitive_words_normalized_not_blank CHECK (LEN(LTRIM(RTRIM(normalized_word))) > 0)
);
