package com.haulmont.components.samples.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;

import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;
import java.util.Date;
import java.util.function.Supplier;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.UUID;
import javax.persistence.Lob;

@NamePattern("%s from %s|subject,from")
@Table(name = "IMAPSAMPLE_IMAP_DEMO_MESSAGE")
@Entity(name = "imapsample$ImapDemoMessage")
public class ImapDemoMessage extends StandardEntity {
    private static final long serialVersionUID = 1529635256109331665L;

    @Column(name = "SEEN")
    private Boolean seen;

    @Column(name = "FROM_")
    protected String from;

    @Lob
    @Column(name = "TO_LIST")
    protected String toList;

    @Lob
    @Column(name = "CC_LIST")
    protected String ccList;

    @Lob
    @Column(name = "BCC_LIST")
    protected String bccList;

    @Column(name = "SUBJECT")
    protected String subject;

    @Column(name = "FLAGS_LIST")
    protected String flagsList;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "DATE_")
    protected Date date;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "SEEN_TIME")
    private Date seenTime;

    @Column(name = "MESSAGE_UID", nullable = false)
    private Long messageUid;

    @Column(name = "FOLDER_NAME", nullable = false)
    protected String folderName;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MAIL_BOX_ID")
    protected ImapMailBox mailBox;

    @NotNull
    @Column(name = "IMAP_MESSAGE_ID", nullable = false)
    private UUID imapMessageId;

    public void setImapMessageId(UUID imapMessageId) {
        this.imapMessageId = imapMessageId;
    }

    public UUID getImapMessageId() {
        return imapMessageId;
    }


    public void setDate(Date date) {
        this.date = date;
    }

    public Date getDate() {
        return date;
    }


    public void setFlagsList(String flagsList) {
        this.flagsList = flagsList;
    }

    public String getFlagsList() {
        return flagsList;
    }


    public void setFrom(String from) {
        this.from = from;
    }

    public String getFrom() {
        return from;
    }

    public void setToList(String toList) {
        this.toList = toList;
    }

    public String getToList() {
        return toList;
    }

    public void setCcList(String ccList) {
        this.ccList = ccList;
    }

    public String getCcList() {
        return ccList;
    }

    public void setBccList(String bccList) {
        this.bccList = bccList;
    }

    public String getBccList() {
        return bccList;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }


    public void setSeenTime(Date seenTime) {
        this.seenTime = seenTime;
    }

    public Date getSeenTime() {
        return seenTime;
    }


    public void setSeen(Boolean seen) {
        this.seen = seen;
    }

    public Boolean getSeen() {
        return seen;
    }

    public void setMessageUid(Long messageUid) {
        this.messageUid = messageUid;
    }

    public Long getMessageUid() {
        return messageUid;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setMailBox(ImapMailBox mailBox) {
        this.mailBox = mailBox;
    }

    public ImapMailBox getMailBox() {
        return mailBox;
    }

    public static void fillMessage(ImapDemoMessage mailMessage, ImapMessageDto dto, Supplier<ImapMailBox> mailBoxSupplier) {
        mailMessage.setMessageUid(dto.getUid());
        mailMessage.setMailBox(mailBoxSupplier.get());
        mailMessage.setFolderName(dto.getFolderName());
        mailMessage.setDate(dto.getDate());
        mailMessage.setSubject(dto.getSubject());
        mailMessage.setFrom(dto.getFrom());
        mailMessage.setToList(dto.getTo());
        mailMessage.setBccList(dto.getBcc());
        mailMessage.setCcList(dto.getCc());
        mailMessage.setFlagsList(dto.getFlags());
    }
}