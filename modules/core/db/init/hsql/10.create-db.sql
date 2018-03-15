-- begin IMAPSAMPLE_IMAP_MESSAGE
create table IMAPSAMPLE_IMAP_MESSAGE (
    ID varchar(36) not null,
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
    TO_LIST varchar(255),
    CC_LIST varchar(255),
    BCC_LIST varchar(255),
    SUBJECT varchar(255),
    FLAGS_LIST varchar(255),
    DATE_ timestamp,
    SEEN_TIME timestamp,
    MESSAGE_UID bigint not null,
    FOLDER_NAME varchar(255) not null,
    MAIL_BOX_ID varchar(36),
    --
    primary key (ID)
)^
-- end IMAPSAMPLE_IMAP_MESSAGE
