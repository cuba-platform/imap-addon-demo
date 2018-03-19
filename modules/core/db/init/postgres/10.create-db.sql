-- begin IMAPSAMPLE_IMAP_MESSAGE
create table IMAPSAMPLE_IMAP_MESSAGE (
    ID uuid,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    SEEN boolean,
    FROM_ varchar(255),
    TO_LIST text,
    CC_LIST text,
    BCC_LIST text,
    SUBJECT varchar(255),
    FLAGS_LIST varchar(255),
    DATE_ timestamp,
    SEEN_TIME timestamp,
    MESSAGE_UID bigint not null,
    FOLDER_NAME varchar(255) not null,
    MAIL_BOX_ID uuid,
    IMAP_MESSAGE_ID uuid not null,
    --
    primary key (ID)
)^
-- end IMAPSAMPLE_IMAP_MESSAGE
