-- begin IMAPSAMPLE_MAIL_MESSAGE
alter table IMAPSAMPLE_MAIL_MESSAGE add constraint FK_IMAPSAMPLE_MAIL_MESSAGE_MAIL_BOX foreign key (MAIL_BOX_ID) references MAILCOMPONENT_MAIL_BOX(ID)^
create index IDX_IMAPSAMPLE_MAIL_MESSAGE_MAIL_BOX on IMAPSAMPLE_MAIL_MESSAGE (MAIL_BOX_ID)^
-- end IMAPSAMPLE_MAIL_MESSAGE
