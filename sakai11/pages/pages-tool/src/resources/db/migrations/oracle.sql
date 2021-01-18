CREATE TABLE pages_page (
  context char(36) PRIMARY KEY,
  title varchar2(200) NOT NULL,
  content CLOB NOT NULL
);