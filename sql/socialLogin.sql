create table SOCIALLOGIN
(
    SOCIAL_ACCOUNT_ID             bigint auto_increment
        primary key,
    SOCIALLOGIN_PROVIDER          varchar(50)                        not null,
    SOCIALLOGIN_PROVIDER_ID       varchar(255)                       not null,
    SOCIALLOGIN_SOCIAL_EMAIL      varchar(255)                       null,
    SOCIALLOGIN_SOCIAL_CREATEDATE datetime default CURRENT_TIMESTAMP not null,
    SOCIALLOGIN_SOCIAL_UPDATEDATE datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    USER_ID                       bigint                             not null,
    constraint UQ_SOCIALLOGIN_PROVIDER_PROVIDERID
        unique (SOCIALLOGIN_PROVIDER, SOCIALLOGIN_PROVIDER_ID),
    constraint FK_SOCIALLOGIN_USER
        foreign key (USER_ID) references USERS (USER_ID)
            on delete cascade
);