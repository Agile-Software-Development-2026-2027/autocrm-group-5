-- SCRUM-88: one table for transcripts from both channels (manual upload and Fireflies).
CREATE TABLE transcripts (
                             id            VARCHAR(255)             NOT NULL,
                             title         VARCHAR(255)             NOT NULL,
                             source        VARCHAR(20)              NOT NULL,
                             status        VARCHAR(20)              NOT NULL,
                             text          VARCHAR(1000000),
                             error_message VARCHAR(1000),
                             created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
                             updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
                             CONSTRAINT pk_transcripts PRIMARY KEY (id)

);