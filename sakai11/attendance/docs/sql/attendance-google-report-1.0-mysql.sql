CREATE TABLE `attendance_record_override_t` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `NETID` varchar(255) NOT NULL,
  `SITE_ID` varchar(255) NOT NULL,
  `EVENT_NAME` varchar(255) NOT NULL,
  `STATUS` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `UNIQ_ATT_REC_O` UNIQUE (`NETID`, `SITE_ID`, `EVENT_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE nyu_t_attendance_jobs (job varchar(64), last_success_time bigint) ENGINE=InnoDB DEFAULT CHARSET=utf8;
