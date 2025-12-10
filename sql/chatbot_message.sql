create table CHATBOT_MESSAGE
(
    CHATBOT_MESSAGE_ID bigint auto_increment
        primary key,
    SESSION_ID         bigint                             not null,
    USER_ID            bigint                             null,
    ROLE               varchar(20)                        not null,
    CONTENT            text                               not null,
    CREATED_AT         datetime default CURRENT_TIMESTAMP null,
    UPDATED_AT         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_chatbot_message_user
        foreign key (USER_ID) references USERS (USER_ID)
            on update cascade on delete set null
)
    collate = utf8mb4_unicode_ci;

create index idx_message_session_created
    on CHATBOT_MESSAGE (SESSION_ID, CREATED_AT);