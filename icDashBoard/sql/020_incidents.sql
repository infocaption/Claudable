-- 020_incidents.sql — Incident reporting / Change management
-- Creates incidents table, registers module, imports 477 historical records

CREATE TABLE IF NOT EXISTS `incidents` (
    `id`               INT AUTO_INCREMENT PRIMARY KEY,
    `description`      TEXT NOT NULL,
    `solution`         TEXT NULL,
    `level`            ENUM('Critical','High','Medium','Low','Change','Security') NOT NULL DEFAULT 'Medium',
    `reporter_user_id` INT NULL,
    `reporter_name`    VARCHAR(255) NULL,
    `server`           VARCHAR(100) NULL,
    `incident_time`    DATETIME NOT NULL,
    `clear_time`       DATETIME NULL,
    `created_by`       INT NULL,
    `created_at`       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_inc_reporter` FOREIGN KEY (`reporter_user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_inc_creator` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL,
    INDEX `idx_inc_level` (`level`),
    INDEX `idx_inc_server` (`server`),
    INDEX `idx_inc_time` (`incident_time`),
    INDEX `idx_inc_reporter` (`reporter_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Register module
INSERT INTO `modules` (`owner_user_id`, `module_type`, `name`, `icon`, `description`, `category`, `entry_file`, `directory_name`, `is_active`)
VALUES (NULL, 'system', 'Incidentrapporter', '🚨', 'Incidentrapportering och change management', 'admin', 'index.html', 'incidents', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Assign to Alla group
INSERT IGNORE INTO `module_groups` (`module_id`, `group_id`)
SELECT m.id, g.id FROM `modules` m, `groups` g
WHERE m.directory_name = 'incidents' AND g.name = 'Alla';

-- Import historical data (477 records)
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Minnet tog slut', NULL, 'Critical', 'Zid Eriksson', '2025-12-15 15:20:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppsättning byggvarubedomningen', NULL, 'Change', 'JH', '2025-12-15 14:06:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('nedstängning österåker dag1', NULL, 'Change', 'BJ', '2025-12-15 10:13:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppsättning skellefteå', NULL, 'Change', 'JH', '2025-12-11 17:03:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ftp skellefteå', NULL, 'Change', 'JH', '2025-12-11 17:02:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ftp norrlandsoperan', NULL, 'Change', 'JH', '2025-12-11 15:19:00', NULL, 'InfoCaption-21');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('365 norrlandsoperan', NULL, 'Change', 'JH', '2025-12-11 15:19:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('FTP socialstyrelsen + FTP hallsberg BJ', NULL, 'Change', 'JH', '2025-12-10 16:17:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('365 socialstyrelsen', NULL, 'Change', 'JH', '2025-12-10 16:17:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('hallsberg knepig dag 1', NULL, 'Change', 'BJ', '2025-12-10 15:50:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('IC-18 har fått os uppgradering till server 2022', NULL, 'Change', 'Zid Eriksson', '2025-12-09 23:07:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Städat bort gamla db:s och userkopplingar i samband med utf-8 (alla servrar)', NULL, 'Change', 'BJ', '2025-12-04 16:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat certifikat för support.timecare.se', NULL, 'Change', 'Zid Eriksson', '2025-12-09 11:23:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Omstart', NULL, 'Medium', 'Zid Eriksson', '2025-12-08 12:30:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Overhead memory tog slut', NULL, 'Critical', 'Zid Eriksson', '2025-12-03 14:09:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('AUpdate IC 365', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-12-02 07:54:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('generellt städ jobb på', NULL, 'Change', 'Zid Eriksson', '2025-12-02 13:43:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Rensat bort server xml kopior som är äldre än ett år', NULL, 'Change', 'Zid Eriksson', '2025-12-02 13:28:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Kommenterat ut gamla certifikat och justerat default domän från guidecloud.se till infocaption.com', NULL, 'Change', 'Zid Eriksson', '2025-12-02 13:26:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Bytt domän på education.servicedesk.work till beonenorth.infocaption.com pga att certifikatet har gått ut.', NULL, 'Change', 'Zid Eriksson', '2025-12-02 13:25:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fel konfad smtp tog över port 443 och gjorde så att tomcat inte kunder leverera via https', NULL, 'Critical', 'Zid Eriksson', '2025-11-28 07:37:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('MySQL connections tog slut', NULL, 'Critical', 'Zid Eriksson', '2025-11-29 07:36:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('FTP captives\r\n', NULL, 'Change', 'JH', '2025-11-28 10:31:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ssatt upp server catpives', NULL, 'Change', 'JH', '2025-11-28 10:31:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Generellt städjobb', NULL, 'Change', 'Zid Eriksson', '2025-11-26 07:45:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Har kontaktat spamhaus via deras tjänst och fått oss delistde från deras spam filter', NULL, 'Change', 'Zid Eriksson', '2025-11-25 15:01:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Justerat port 25 behörighet för smtp', NULL, 'Change', 'Zid Eriksson', '2025-11-25 14:32:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Det fanns gamla certifikat i användning som missades att tas bort från server.xml', NULL, 'Critical', 'Zid Eriksson', '2025-11-25 05:31:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('flytta gamla certifikat till old mappen under ssl, rensas efter skrik test', NULL, 'Change', 'Zid Eriksson', '2025-11-24 16:23:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat capios certifikat', NULL, 'Change', 'Zid Eriksson', '2025-11-24 16:07:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat capios certifikat', NULL, 'Change', 'Zid Eriksson', '2025-11-24 16:07:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('IC-20 Tomcat hade stoppats. Startade om den.', NULL, 'Critical', 'Anja Runestedt Palmér', '2025-11-20 14:15:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('IC-19 Tomcat hade stoppats. Startade om den.', NULL, 'Critical', 'Anja Runestedt Palmér', '2025-11-18 13:50:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Utf8', NULL, 'Change', 'BJ', '2025-11-19 22:29:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Utf8', NULL, 'Change', 'BJ', '2025-11-19 22:28:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Havar Ameen och Magnus Nilsson har fått rättighet till sharepoint admin i två veckor', NULL, 'Change', 'BJ', '2025-11-19 09:36:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('utf8', NULL, 'Change', 'BJ', '2025-11-06 22:42:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('utf8', NULL, 'Change', 'BJ', '2025-11-05 23:21:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('SFTP crediflow', NULL, 'Change', 'Jonas Hammarberg', '2025-11-05 17:29:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt upp crediflow\r\n', NULL, 'Change', 'Jonas Hammarberg', '2025-11-05 17:29:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tar ner smartassisten coor ', NULL, 'Change', 'Zid Eriksson', '2025-11-04 13:50:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat certifikat utbportalen.solom.se', NULL, 'Change', 'Zid Eriksson', '2025-10-30 11:19:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('plockat bort kommungemensamt', NULL, 'Change', 'Zid Eriksson', '2025-10-30 11:01:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat tomcat daily', NULL, 'Change', 'BJ', '2025-10-29 23:48:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat hso med 5.98', NULL, 'Change', 'Zid Eriksson', '2025-10-29 23:47:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat helseinord med digitalcoach 5.98', NULL, 'Change', 'Zid Eriksson', '2025-10-29 23:47:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('fortsatt krash, justerat minnet på tomcat', NULL, 'Critical', 'Zid Eriksson', '2025-10-29 23:46:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updaterat guider.nu certifikat', NULL, 'Change', 'Zid Eriksson', '2025-10-27 13:35:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat certifikat berg.se', NULL, 'Change', 'Zid Eriksson', '2025-10-27 11:57:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Iver notifierade oss om att skyddet hade tagit bort en oönskad skadlig fil. filen hade kommit in via mail servern som en attachment.\r\nIngen åtgärd behövs.', NULL, 'Security', 'Zid Eriksson', '2025-10-24 18:16:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('IC365 tierp', NULL, 'Change', 'BJ', '2025-10-27 09:43:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Beställt mer disk till c: för att ge pagefile mer utrymme att överleva när minnet skiter sig.', NULL, 'Change', 'Zid Eriksson', '2025-10-27 09:27:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('tar bort\r\nNSclient ', NULL, 'Change', 'Zid Eriksson', '2025-10-27 09:27:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('avinstallerar 7zip, filezilla, java8, silverlight', NULL, 'Change', 'Zid Eriksson', '2025-10-27 08:05:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Cert trollhattan', NULL, 'Change', 'BJ', '2025-10-24 23:31:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('slut på minne, gav tomcat en omstart', NULL, 'Critical', 'BJ', '2025-10-23 08:40:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('slut på minne, gav tomcat en omstart', NULL, 'Critical', 'BJ', '2025-10-15 08:57:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Sörmlands sparbank, serveruppsättning dag 1', NULL, 'Change', 'Anja Runestedt Palmér', '2025-10-21 16:27:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('update IC365', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-10-21 20:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Ikea har nu java 21', NULL, 'Change', 'Zid Eriksson', '2025-10-14 08:38:00', NULL, 'InfoCaption-21');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Hogias cert är uppdaterat', NULL, 'Change', 'Zid Eriksson', '2025-10-14 11:37:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fick från ASSA att det gjorts brute force intrångsförsök via självreg. LG notifierad och ärende skapat för att förhindra i framtiden', NULL, 'Security', 'BJ', '2025-10-10 16:14:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('startar om ic11 för certifikat', NULL, 'High', 'Zid Eriksson', '2025-10-07 06:27:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('fixat cert procydo.com', NULL, 'Change', 'Zid Eriksson', '2025-10-07 06:26:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Dag 2 365 skatteverket', NULL, 'Change', 'BJ', '2025-10-06 13:13:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Dag 1 CUP vetlanda', NULL, 'Change', 'BJ', '2025-10-06 12:56:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppsättning av skatteverket IC365 + SFTP IC26', NULL, 'Change', 'Anja Runestedt Palmér', '2025-10-03 11:33:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('fixat getinges certifikat', NULL, 'Change', 'Zid Eriksson', '2025-10-02 13:33:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt upp ljusnarsberg på ic-26\r\nSFTP på ic-10', NULL, 'Change', 'Jonas Hammarberg', '2025-10-01 16:35:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Server krashade ned sig helt, gjort en system omstart', NULL, 'Critical', 'Zid Eriksson', '2025-09-22 07:52:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Mjuk-krasch som återhämtade sig. Fann att NCC hade 150k sessioner, startade om dom', NULL, 'High', 'BJ', '2025-09-18 14:06:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('update Säkerhetsmedvetenhet', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-09-16 20:03:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('CUP mullsjö', NULL, 'Security', 'BJ', '2025-09-15 16:50:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('stängt ner axfoodcloud.infocaption.com', NULL, 'Change', 'Zid Eriksson', '2025-09-15 16:34:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mysql saknade rätt antal anslutningar, åtgärdat nu', NULL, 'Change', 'Zid Eriksson', '2025-09-11 07:40:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('En instans crashade (https://esfkompetens5.infocaption.com/) och vägra läppa en tråd, startar om tomcat', NULL, 'Critical', 'Zid Eriksson', '2025-09-11 07:19:00', NULL, 'InfoCaption-26');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatyerade training', NULL, 'Change', 'BJ', '2025-09-10 17:26:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt upp avesta i molnet ic-26', NULL, 'Change', 'Jonas Hammarberg', '2025-09-10 14:03:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('UTF8 till goliska och lerum (båda på ic9)', NULL, 'Change', 'BJ', '2025-09-08 19:42:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Minnesläcka sänkte tomcat', NULL, 'Critical', 'BJ', '2025-09-05 10:25:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('minnesläcka sänkte tomcat', NULL, 'Critical', 'Zid Eriksson', '2025-09-04 15:26:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat hso', NULL, 'Change', 'Zid Eriksson', '2025-09-04 02:22:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('separerat db user för lindab och lindab clone', NULL, 'Change', 'Zid Eriksson', '2025-09-03 14:13:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updaterat certifikatet supportguider.stockholm.se', NULL, 'Change', 'Zid Eriksson', '2025-08-28 14:43:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering IC365', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-09-02 21:07:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Minnesläcka krasch x2 (troligen svchost) fick starta om hela maskinen ena gången', NULL, 'Critical', 'BJ/Zid', '2025-08-27 15:51:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('utf8 konvert falköping', NULL, 'Change', 'BJ', '2025-08-27 15:50:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('utf8 konvert dela', NULL, 'Change', 'BJ', '2025-08-27 15:50:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('utf8 konvert fvb', NULL, 'Change', 'BJ', '2025-08-27 15:49:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('justerat dbbrokern på alla instansner till 10 maxconns', NULL, 'Change', 'Zid Eriksson', '2025-08-26 22:55:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('gav ic9 en omstart', NULL, 'Medium', 'Zid Eriksson', '2025-08-26 22:43:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('axfood certifikat på plats', NULL, 'Change', 'Zid Eriksson', '2025-08-26 22:17:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Minnes take tog slut då flera applikationerna slogs om minnet. Startade om mysql, justerade initial minnet på tomcat. (Windows successfully diagnosed a low virtual memory condition. The following programs consumed the most virtual memory: tomcat9.exe (40808) consumed 9988636672 bytes, svchost.exe (4692) consumed 5545459712 bytes, and sfc.exe (18328) consumed 2507907072 bytes.)', NULL, 'Critical', 'Zid Eriksson', '2025-08-21 08:27:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ressurectade hjelp2 som hjelp2.infocaption.com', NULL, 'Change', 'Björn J', '2025-08-14 16:33:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat certifikatet för infokoping.se', NULL, 'Change', 'Zid Eriksson', '2025-08-11 08:27:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt site-map för help2.nlsh i removed för borttagning', NULL, 'Change', 'Jonas Hammarberg', '2025-07-23 13:27:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tagit bort hosttagg för hjelp2.nlsh.no', NULL, 'Change', 'Jonas Hammarberg', '2025-07-22 13:40:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat certifikat för eguide.agresso.no', NULL, 'Change', 'Zid Eriksson', '2025-07-18 15:01:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Städat ic-11', NULL, 'Change', 'Zid Eriksson', '2025-07-18 10:01:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tog bort IP i SPF record som lades till igår', NULL, 'Change', 'Björn J', '2025-07-10 08:38:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Justerat SPF record - lade till en IP-adress', NULL, 'Change', 'Björn J', '2025-07-09 16:54:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('konverterat uppsalas server till utf8', NULL, 'Change', 'Zid Eriksson', '2025-07-09 13:04:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updaterat procydo.com certifikat', NULL, 'Change', 'Zid Eriksson', '2025-07-08 08:30:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Support now has correct naming convention', NULL, 'Change', 'Zid Eriksson', '2025-07-07 12:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('JAVA migration 11 -> 21', NULL, 'Change', 'Zid Eriksson', '2025-07-07 12:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat update', NULL, 'Change', 'Zid Eriksson', '2025-07-07 12:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Windows updates', NULL, 'Change', 'Zid Eriksson', '2025-07-07 12:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('startar om tomcat pga avsaknad omstart efter nattens utrullning', NULL, 'Critical', 'Zid Eriksson', '2025-07-02 10:31:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('rensade lite förlegade databaser', NULL, 'Change', 'Zid Eriksson', '2025-07-01 16:43:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('justerade så support portalens sso var kopplad till ic all', NULL, 'Change', 'Zid Eriksson', '2025-07-01 09:48:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('coeo inkasso uppsatt (IC26)', NULL, 'Change', 'Björn J', '2025-07-01 08:07:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Påbörjat uppsättning coeo inkasso (IC26)', NULL, 'Change', 'Björn J', '2025-06-30 14:20:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Påbörjat nedstängning torsgby', NULL, 'Change', 'Björn J', '2025-06-30 10:35:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Rensat bort ip-adresser och includes som vi inte använder i SPF', NULL, 'Change', 'Björn J', '2025-06-26 10:24:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Ändrade dmarc policy till "quarantine", testning sker över sommaren', NULL, 'Change', 'Björn J', '2025-06-26 09:09:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Aktiverade DKIM via exchange för infocaption.com', NULL, 'Change', 'Björn J', '2025-06-26 09:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lade till DKIM selectors i infocaption.com DNS', NULL, 'Change', 'Björn J', '2025-06-26 08:05:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tog bort felaktig cname selector1._ i dns för infocaption.com', NULL, 'Change', 'Björn J', '2025-06-25 16:46:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tillfälligt fel med vissa guidetyper\r\nTidigare idag gick det inte att visa guider av följande guidetyper: utbildning, arbetsflöde och URL-guide.\r\n07:21 - Första felrapporten mottagen.\r\n08:50 - Vi arbetar med att ta fram en åtgärd.\r\n10:29 - En första åtgärd genomgår just nu testning.\r\n10:59 - Åtgärden har testats. Inom kort meddelas kunder om lösningen och hur man påverkas.\r\n14:07 - Åtgärden har tillämpats hos berörda kunder.\r\n\r\n\r\n', NULL, 'High', 'Björn J', '2025-06-23 07:31:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt upp gavlegardarna på ic26 samt SFTP för dem.', NULL, 'Change', 'Jonas Hammarberg', '2025-06-19 16:48:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('städat undan mysql backup', NULL, 'Change', 'Zid Eriksson', '2025-06-19 12:26:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Startat om coor servern pga at 5.97 inte laddats in ordentligt', NULL, 'Medium', 'Zid Eriksson', '2025-06-18 12:53:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tagit bort hosttagg för torsby', NULL, 'Change', 'Jonas Hammarberg', '2025-06-16 11:22:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Patchat IKEA', NULL, 'Change', 'Zid Eriksson', '2025-06-13 00:01:00', NULL, 'InfoCaption-21');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat lindabs certifikat', NULL, 'Change', 'Zid Eriksson', '2025-06-10 22:30:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt upp atherfoldconsulting på ic-26', NULL, 'Change', 'Jonas Hammarberg', '2025-06-09 11:46:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('updaterat ncc certifikat', NULL, 'Change', 'Zid Eriksson', '2025-06-05 23:06:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdatering säkerhetsmedvetenhet', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-06-05 20:32:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('liten Patch IC365', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-06-04 20:05:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('stängt ner afconsult domänen på server.xml åt evry', NULL, 'Change', 'Zid Eriksson', '2025-05-30 09:41:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Justerat resning av updateringspaket mappen till 15 dagar istället för 30', NULL, 'Change', 'Zid Eriksson', '2025-05-28 11:19:00', NULL, 'InfoCaption-2');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering IC365', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-05-28 10:54:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('support var lite överbelastad, kan ha varit guide hälsa sidan.', NULL, 'High', 'Zid Eriksson', '2025-05-27 13:05:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('tomcat var avstängt och hade inte gått i gång, gav den en knuff ner för trappen', NULL, 'Critical', 'Zid Eriksson', '2025-05-26 05:55:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Testade kontextstödet mot hsotest för bugganalys', NULL, 'Low', 'Peter Jäderlund', '2025-05-22 16:30:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt up SFTP åt söderköping på ic-26', NULL, 'Change', 'Jonas Hammarberg', '2025-05-22 15:33:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt upp ftp för elfsborg på ic-26', NULL, 'Change', 'Jonas Hammarberg', '2025-05-22 11:57:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('MTM uppsättning, skarp server från TRY på IC-26', NULL, 'Change', 'Anja Runestedt Palmér', '2025-05-20 16:39:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Justerat coor så att vi spårar anslutningar av deras gamla domän', NULL, 'Change', 'Zid Eriksson', '2025-05-20 11:01:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat gick ned under natten (0430), startade om på morgonen', NULL, 'Critical', 'BJ', '2025-05-16 08:08:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt upp gk.ic.com sftp', NULL, 'Change', 'Jonas Hammarberg', '2025-04-30 10:45:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt upp gk.ic.com', NULL, 'Change', 'Jonas Hammarberg', '2025-04-30 10:45:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat certifikat för alla pache24 på ic18 15 och 20.\r\nSamt uppdaterat souregear repository certifikat', NULL, 'Change', 'Zid Eriksson', '2025-04-25 23:52:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updated vips to 5.96', NULL, 'Change', 'Havar Ameen', '2025-04-24 23:49:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Höglandet fick UTF-8 medan servern var nere', NULL, 'Change', 'BJ', '2025-04-23 16:32:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Läst in backup för höglandet pga de strulade med användarimport', NULL, 'Critical', 'BJ', '2025-04-23 16:31:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat skövde till UTF-8', NULL, 'Change', 'BJ', '2025-04-23 12:31:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Avslutning av re-uppsättning av newkvarn', NULL, 'Change', 'BJ', '2025-04-23 22:30:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Börjat confa 26', NULL, 'Change', 'BJ', '2025-04-22 20:44:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Börja sätta upp nya nykvarn servern', NULL, 'Change', 'BJ', '2025-04-22 14:35:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt in uppdatering av coors redirect cert', NULL, 'Change', 'BJ', '2025-04-22 14:33:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Certifikat uppdaterade på samtliga kundservrar för infocaption.com\r\nkvar är 20,18,15 pga apache24', NULL, 'Change', 'Zid Eriksson', '2025-04-22 00:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Try hade olika problem.\r\nbalder blev felkonfad efter nattlig omstart. apache24 hade hakat sig. mysql connections togslut.', NULL, 'Critical', 'Zid Eriksson', '2025-04-17 08:11:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('satt upp sftp för vadstena', NULL, 'Change', 'JH', '2025-04-15 14:27:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt upp sftp för mjölby (365)', NULL, 'Change', 'Jonas Hammarberg', '2025-04-15 14:27:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt upp sftp för mjölby (365)', NULL, 'Change', 'Jonas Hammarberg', '2025-04-15 14:27:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satte upp try balder och uppdaterade till 595', NULL, 'Change', 'JH BJ', '2025-04-15 12:20:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fel confad server xml, åtgärdad', NULL, 'Critical', 'Zid Eriksson', '2025-04-15 05:55:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('vadstena 365 på plats', NULL, 'Change', 'Zid Eriksson', '2025-04-14 22:42:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mjölby 365 är på plats', NULL, 'Change', 'Zid Eriksson', '2025-04-14 21:38:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Stängde ned bgc', NULL, 'Change', 'Björn J', '2025-04-10 22:37:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satte upp bgc', NULL, 'Change', 'Björn', '2025-04-10 22:40:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('updaterat procydo.com certifikat', NULL, 'Change', 'Zid Eriksson', '2025-04-11 05:49:00', NULL, 'InfoCaption-2');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('fixat utf8 på sameskolstyrelsen', NULL, 'Change', 'Zid Eriksson', '2025-04-11 00:38:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat apache 2.4 till Apache 2.4.63', NULL, 'Change', 'Zid Eriksson', '2025-04-11 00:09:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat apache 2.4 till Apache 2.4.63', NULL, 'Change', 'Zid Eriksson', '2025-04-11 00:09:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat apache 2.4 till Apache 2.4.63', NULL, 'Change', 'Zid Eriksson', '2025-04-10 23:30:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('training konverteras till innodb', NULL, 'Change', 'Zid Eriksson', '2025-04-08 15:18:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('gav ic-18 tomcat en omstart och la på cupclone', NULL, 'Change', 'Zid Eriksson', '2025-04-08 07:50:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('updaterat vannas certifikat', NULL, 'Change', 'Zid Eriksson', '2025-04-04 21:43:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt upp try för hammarby.infocaption.com', NULL, 'Change', 'Jonas Hammarberg', '2025-04-03 13:11:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat timestamps som var fel på soloms server', NULL, 'Change', 'Zid Eriksson', '2025-04-03 07:48:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat timestamps som var fel på tjörns server', NULL, 'Change', 'Zid Eriksson', '2025-04-03 07:40:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat timestamps som var fel på sundsvall server', NULL, 'Change', 'Zid Eriksson', '2025-04-03 07:32:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fyttat danderyd till removed samt tagit bort övriga filer för dem', NULL, 'Change', 'jonka spökmök (JH)', '2025-04-01 16:44:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt upp plattform för OBF', NULL, 'Change', 'Jonas Hammarberg', '2025-04-01 16:32:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('satt upp plattform för hylte kommun', NULL, 'Change', 'Jonas Hammarberg', '2025-04-01 15:38:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat gnosjö till 5.95', NULL, 'Change', 'Zid Eriksson', '2025-03-31 22:13:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat halmstad till 5.95', NULL, 'Change', 'Zid Eriksson', '2025-03-31 22:10:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt på vara.infocaption.com', NULL, 'Change', 'Zid Eriksson', '2025-03-28 09:49:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tagit bort danderyds host tag', NULL, 'Change', 'Jonas Hammarberg', '2025-03-28 09:46:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Satt upp nykvarn.infocaption.com', NULL, 'Change', 'Jonas Hammarberg', '2025-03-27 10:16:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat certifikat för eitech', NULL, 'Change', 'Zid Eriksson', '2025-03-24 16:51:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Correction for ID 265: updated to version 9.0.102', NULL, 'Security', 'Björn J', '2025-03-19 09:50:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updated Tomcat to 9.0.99 for all customer servers', NULL, 'Security', 'Björn J', '2025-03-18 23:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updated tomcat 9 to latest version due to vunerability', NULL, 'Change', 'Havar Ameen', '2025-03-18 12:10:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('update to latest 5.95', NULL, 'Change', 'Havar Ameen', '2025-03-17 20:19:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering vips + latestdemo - Senaste 5.95', NULL, 'Change', 'Havar Ameen', '2025-03-17 08:31:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Utökat cache size från 10mb till 100mb', NULL, 'Change', 'Zid Eriksson', '2025-03-13 09:38:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat körde ihop sih, loggen visar att cachen är för liten', NULL, 'Critical', 'Zid Eriksson', '2025-03-13 09:38:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mysql hade crashat', NULL, 'Critical', 'Zid Eriksson', '2025-03-12 15:11:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering vips', NULL, 'Change', 'Uppdatering vips', '2025-03-11 18:12:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering IC365', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-03-18 09:33:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ordnat .well-known/acme-challenge/ för utfärdande av certifikat med http-01 validering i webbapps mappen', NULL, 'Change', 'Zid Eriksson', '2025-03-11 08:12:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering av demo.infocaption.com till 5.95', NULL, 'Change', 'Havar Ameen', '2025-03-07 08:50:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('undeployat docs och examples från tomcat', NULL, 'Change', 'Zid Eriksson', '2025-03-06 10:57:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Beställt 40gb diskutökning', NULL, 'Change', 'Zid Eriksson', '2025-03-06 10:57:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat mysql', NULL, 'Change', 'Zid Eriksson', '2025-03-05 00:07:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat mysql', NULL, 'Change', 'Zid Eriksson', '2025-02-28 00:25:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat mysql	', NULL, 'Change', 'Zid Eriksson', '2025-02-27 23:40:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat mysql', NULL, 'Change', 'Zid Eriksson', '2025-02-27 23:21:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('bokat patchning 5.94 för 02:00', NULL, 'Change', 'Zid Eriksson', '2025-02-27 00:40:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat mysql', NULL, 'Change', 'Zid Eriksson', '2025-02-27 00:27:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('åtgärdat mysql', NULL, 'Change', 'Zid Eriksson', '2025-02-27 00:06:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fix gjordes november förra året\r\ngjorde lät justering på brokers idag', NULL, 'Change', 'Zid Eriksson', '2025-02-26 23:29:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('har ändrat databasen för training till utf8mb4_swedish_ci och tabellen cells', NULL, 'Change', 'Zid Eriksson', '2025-02-26 10:32:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Åtgärdat connectionbrokers så att de använder 10 connections', NULL, 'Change', 'Zid Eriksson', '2025-02-25 10:11:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Log filerna drog iväg på tomcat, fyllde C: och databasen crasha för den fick inte skriva längre. rensar logg filer och kör repair på smartasslog', NULL, 'Critical', 'Zid Eriksson', '2025-02-24 16:00:00', NULL, 'InfoCaption-21');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Bokat samtliga till patching med icUpdate-v594_STR594-db88971c2bef1ebb437d74ede16030b61fbe1f36.zip', NULL, 'Change', 'Zid Eriksson', '2025-02-20 20:22:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Rensat loggfiler för fargelanda', NULL, 'Change', 'Jonas Hammarberg', '2025-02-20 14:00:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Rensat loggfiler för fargelanda', NULL, 'Change', 'Jonas Hammarberg', '2025-02-20 13:59:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Flyttat fargelandas webbapp till removed', NULL, 'Change', 'Jonas Hammarberg', '2025-02-20 13:56:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Flyttat fargelandas webbapp till removed', NULL, 'Change', 'Jonas Hammarberg', '2025-02-20 13:55:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Rensat luleavikarie', NULL, 'Change', 'Zid Eriksson', '2025-02-19 14:38:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterar vips till senaste 5.95', NULL, 'Medium', 'Vipsuppdatering ', '2025-02-19 14:04:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Rättelse:\r\nTagit bort host tag för fargelanda.infocaption.com', NULL, 'Change', 'Jonas Hammarberg', '2025-02-19 10:20:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tagit bort host tag för guider.fargelanda.se', NULL, 'Change', 'Jonas Hammarberg', '2025-02-19 10:23:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tagit bort host tag för fargelunda', NULL, 'Change', 'Jonas Hammarberg', '2025-02-19 10:20:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering Vips -> Nya ÖE (5.95)', NULL, 'Change', 'Havar Ameen', '2025-02-18 12:30:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('rensat filer för uppsägning av kund emerga', NULL, 'Change', 'Jonas Hammarberg', '2025-02-18 09:30:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt på ett script som rensar releasedata på gamla zip paket', NULL, 'Change', 'Zid Eriksson', '2025-02-17 13:40:00', NULL, 'InfoCaption-2');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tagit bort host tag för emerga', NULL, 'Change', 'Jonas Hammarberg', '2025-02-17 09:03:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Stängt av defender på ic-14', NULL, 'Change', 'Zid Eriksson', '2025-02-17 08:04:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('tagit bort halmstads ip låsning', NULL, 'Change', 'Zid Eriksson', '2025-02-14 15:44:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Certifikat hade inte lästs in, gav servern en omstart', NULL, 'Critical', 'Zid Eriksson', '2025-02-14 08:43:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Gick ner pga felkonf i server.xml', NULL, 'Critical', 'Zid Eriksson', '2025-02-14 08:13:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Server performance justeringar och mysql fix på ic5', NULL, 'Change', 'Zid Eriksson', '2025-02-14 00:43:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('https://ewiz.no/diagnostics/HeapCheck.jsp\r\n Säger att den inte svarar fast den gör de?', NULL, 'Medium', 'Zid Eriksson', '2025-02-08 08:44:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fixat mysql på ic17', NULL, 'Change', 'Zid Eriksson', '2025-02-07 01:02:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Autoupdate Content NOmrmedvetneht 4 servrar', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-02-06 23:20:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Startat om tomcat.', NULL, 'Critical', 'Jonas Hammarberg', '2025-02-06 08:20:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Totalkrash, krävde remote omstart', NULL, 'Critical', 'Björn', '2025-02-05 11:00:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('stänger ner ist-intern', NULL, 'Change', 'Zid Eriksson', '2025-02-03 11:07:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering av molnkaniner i natt', NULL, 'Change', 'Zid Eriksson', '2025-01-31 01:04:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('updaterat digital coach och helseinord med nytt paket', NULL, 'Change', 'Zid Eriksson', '2025-01-31 01:04:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt på certifikat för infokoping.se', NULL, 'Change', 'Zid Eriksson', '2025-01-31 01:03:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('flyttat init4u från 20 till 11', NULL, 'Change', 'Zid Eriksson', '2025-01-31 01:02:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('db crash', NULL, 'Critical', 'Zid Eriksson', '2025-01-31 00:32:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Hicka, servern var oresponsiv ett tag men blev stabil efter ett tag igen', NULL, 'High', 'Björn', '2025-01-29 10:00:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Städ arbete på servern, rensar gamla backup filer, release paket, och skräp databaser', NULL, 'Change', 'Zid Eriksson', '2025-01-29 13:25:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Återläste olofströms system portal', NULL, 'Change', 'Zid Eriksson', '2025-01-29 07:32:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Återläste olofströms system portal', NULL, 'Change', 'Zid Eriksson', '2025-01-29 07:32:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Stänger ner productsecurity.infocaption.com ingen sparning krävs', NULL, 'Change', 'Zid Eriksson', '2025-01-28 11:06:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat helseinord.infocaption.com med digital coach apketet', NULL, 'Change', 'Zid Eriksson', '2025-01-28 09:59:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat digitalcoach.infocaption.com med digital coach apketet', NULL, 'Change', 'Zid Eriksson', '2025-01-28 09:58:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Städ arbete på servern, rensar gamla backup filer, release paket, och skräp databaser', NULL, 'Change', 'Zid Eriksson', '2025-01-28 07:49:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Första större IC365-autpupdate', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-02-18 15:11:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt på infokoping.se på servern för att bereda på cert', NULL, 'Change', 'Zid Eriksson', '2025-01-27 13:59:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt på ett schemalagt jobb på servern för att åtgärda foodix diplom efter varje natt vid 06', NULL, 'Change', 'Zid Eriksson', '2025-01-27 13:35:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Påbörjat uppsättning av sameskolstyrelsen', NULL, 'Change', 'Björn', '2025-01-24 09:34:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Klonen fick 5.94 och vaknade aldrig igen.\r\nÅterupplivade den med en skum bok jag hitta', NULL, 'Critical', 'Zid Eriksson', '2025-01-24 06:00:00', NULL, 'InfoCaption-21');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('identifierade oförvaltade och otrygga jsp filer på custom hos ncc', NULL, 'Security', 'Zid Eriksson', '2025-01-23 16:42:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterade MySQL till 5.7', NULL, 'Change', 'Zid Eriksson', '2025-01-22 23:16:00', NULL, 'InfoCaption-21');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterade JAVA/Tomcat', NULL, 'Change', 'Zid Eriksson', '2025-01-22 23:15:00', NULL, 'InfoCaption-21');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Server segade ner med hög användning från både tomcat och mysql', NULL, 'High', 'Zid Eriksson', '2025-01-21 13:55:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('lagt på nytt Certifikat för comfort.se', NULL, 'Change', 'Zid Eriksson', '2025-01-20 21:44:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('la på querystring på altotest redirect jsp\r\nString sForwardURL = sForwardBaseURL + sGotoPage+"?"+request.getQueryString()', NULL, 'Change', 'Zid Eriksson', '2025-01-16 10:23:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Körde update smartassuser set validto = \'2025-02-28\' where validto > \'2025-01-14\'\r\n\r\nPå LINDAB', NULL, 'Change', 'Zid Eriksson', '2025-01-16 09:54:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updaterat vips med 5.94', NULL, 'Change', 'Zid Eriksson', '2025-01-14 23:25:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updaterat bestpractise 5.94', NULL, 'Change', 'Zid Eriksson', '2025-01-14 23:24:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updaterat latestdemo med 5.94', NULL, 'Change', 'Zid Eriksson', '2025-01-14 23:09:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Bokat upp molnkaniner för uppdatering natten till tisdag den 21a', NULL, 'Change', 'Zid Eriksson', '2025-01-14 22:56:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('AutoUppdatering norsk IC365 2 kunder', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2025-01-15 23:05:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt till buggar som alias till utveckling@infocaption.com', NULL, 'Change', 'Björn', '2025-01-13 16:31:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Har förnyat *.procydo.com certifikatet', NULL, 'Change', 'Zid Eriksson', '2025-01-10 11:25:00', NULL, 'InfoCaption-2');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt på SQL fil i deploy paket för try som lägger på rättigheter för serviceuser', NULL, 'Change', 'Zid Eriksson', '2025-01-09 10:03:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('unit4 map har raderats helt på kunds begäran efter avtall har sagts upp', NULL, 'Change', 'Zid Eriksson', '2025-01-09 08:01:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Starta om Tomcat', NULL, 'Change', 'Zid Eriksson', '2025-01-08 13:45:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat hängde sig helt, totalt över arbetad.', NULL, 'Critical', 'Zid Eriksson', '2025-01-08 13:44:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('tagit bort unit4 map från server.xml', NULL, 'Change', 'Jonas Hammarberg', '2025-01-08 11:22:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Okänt fel, troligtvis nån hicka med mysql, gick över fort, men var tillräcklig för larmet', NULL, 'High', 'Zid Eriksson', '2025-01-08 10:11:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Installerade 365-portaler på trollhättan', NULL, 'Change', 'Björn', '2025-01-02 17:00:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Installation av content2 (Infoköping)', NULL, 'Low', 'Jonas Hammarberg', '2025-01-02 11:07:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat-All upptäcktes stoppad. Loggen påtalade stopp typ 10.38 på morgonen. Tomcat-all startades om men det hjälpte inte. Init4u hade blivit borttagen men låg kvar i server.xml av okänd anledning. En andra omstart efter fix av server.xml löste det hela.', NULL, 'Critical', 'Peter Jäderlund', '2025-01-01 16:39:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('startar om hela ic-20', NULL, 'Change', 'Zid Eriksson', '2025-01-01 10:32:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Startade Tomcat-All', NULL, 'Change', 'Zid Eriksson', '2025-01-01 10:06:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat-all på var nedstängd', NULL, 'Critical', 'Zid Eriksson', '2025-01-01 10:05:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Remote taskkill av tomcat på ic5 för att sedan logga in med rdp och starta tomcat på nytt', NULL, 'Change', 'Björn', '2024-12-30 09:15:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Webbappar var unresponsive och gick ej att nå servern via rdp', NULL, 'Critical', 'Björn', '2024-12-30 09:00:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterade digitalcoach.infocaption.com med https://jenkins.infocaption.com/archives/STR593/icUpdate-v593_STR593-793831f0d73d6bc70a24795928a72578ce1d29db.zip\r\n ', NULL, 'Change', 'Zid Eriksson', '2024-12-20 16:52:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lagt till vaxholm på ic-25', NULL, 'Change', 'Jonas Hammarberg', '2024-12-20 16:50:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updaterat C:\\IC-Tools\\Api\\iciapi_service.exe för att stödja change report', NULL, 'Change', 'Zid Eriksson', '2024-12-19 14:25:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fixat DMARC och annat småskruv på guidecloud.se för att bli compliant med googles nya policys om dmarc krav', NULL, 'Change', 'Zid Eriksson', '2024-12-19 13:06:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterade sala.se certifikat på ic-24', NULL, 'Change', 'Zid Eriksson', '2024-12-19 00:15:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterade berg.se certifikat på ic-24', NULL, 'Change', 'Zid Eriksson', '2024-12-19 00:04:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('En återinläsning av lindabs organisationer skapa dubbel core org som ställde till det.', NULL, 'Medium', 'Zid Eriksson', '2024-12-17 12:16:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('bakelse hade av misstag fått en insert av 365 klonen', NULL, 'High', 'Zid Eriksson', '2024-12-17 12:15:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat var igång men ingen sajt var når bar, startade om den', NULL, 'Critical', 'Zid Eriksson', '2024-12-05 05:42:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat hade överarbetat sig själv tills den blev utbränd.\r\n\r\nAvlivade den och starta om den', NULL, 'Critical', 'Zid Eriksson', '2024-11-26 09:11:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Ikeas server hade gått ner', NULL, 'Critical', 'Zid Eriksson', '2024-11-10 20:25:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Servern sluta svara, men kom igång igen', NULL, 'Critical', 'Zid Eriksson', '2024-10-16 08:52:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fel konfigurering av kramfors, har åtgärdat så det är rätt nu', NULL, 'Low', 'Zid Eriksson', '2024-09-20 07:09:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updatering av mysql, gick inte smort alls', NULL, 'Medium', 'Zid Eriksson', '2024-09-16 23:00:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Hotjar säkerhets incident', NULL, 'Security', 'Zid Eriksson', '2024-08-26 08:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('PE sänkte sig själv och ic-22.\r\nVi har eskalerat problemet och inväntar svar', NULL, 'Critical', 'Zid Eriksson', '2024-09-09 12:30:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Någon routing station gick ner och blandade kunder samt internt hade problem å nå våra servrar.\r\n\r\nGick över efter en halvtimme', NULL, 'Critical', 'Zid Eriksson', '2024-09-05 11:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Segat ned till oanvändbart läge. Inget larm från CG, intern rapport uppdagade felet. Startade om.', NULL, 'Critical', 'Björn', '2024-08-15 13:28:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('CG larmade + autorapporter inkomna. Servern är extremt långsam. Tomcat pinnad på 100%. Omstart av servern utförd.', NULL, 'Critical', 'Björn', '2024-08-14 07:50:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('IKEA dog idag vid 10:30, övervakaren larmade, men verkar inte kunna ha startat på egen hand, behöver ses över\r\n\r\nFelet åtgärdades 20:50', NULL, 'Critical', 'Zid Eriksson', '2024-08-11 10:30:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat dog', NULL, 'Critical', 'Zid Eriksson', '2024-07-30 13:48:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Mysql hade stängt av sig', NULL, 'Critical', 'Zid Eriksson', '2024-07-04 14:50:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Iver utsattes för ddos attack', NULL, 'Critical', 'Zid Eriksson', '2024-06-26 12:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Iver utsattes för driftstörning', NULL, 'Critical', 'Zid Eriksson', '2024-06-25 14:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Umeå var bokad med en uppdatering i natt\r\nVet ej om det är en glitch i uppdateraren eller om någon tänkte att de skulle få en patch.\r\nMen ser ut som de fick 5.91 utan omstart och jar filerna togs bort, och tyvärr krashade det servern', NULL, 'Medium', 'Zid Eriksson', '2024-06-24 09:48:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat troligtvis hängt sig pga minnes problem.', NULL, 'Critical', 'Zid Eriksson', '2024-06-15 12:11:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('MySQL hade stoppats, uppe igen 21:15', NULL, 'Critical', 'PeterJäderlund', '2024-06-11 21:07:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('En miss i konfigurationen av älmhults certifikat sänkte tomcat', NULL, 'Critical', 'Zid Eriksson', '2024-06-08 07:43:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Mysql klarade inte anslutningarna, gick från 151 till 500, ska gå ner på 250', NULL, 'Medium', 'Zid Eriksson', '2024-06-04 05:05:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Mysql klarade inte anslutningarna, gick från 151 till 500, ska gå ner på 250', NULL, 'Medium', 'Zid Eriksson', '2024-06-04 05:04:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Mysql klarade inte anslutningarna, gick från 151 till 500, ska gå ner på 250', NULL, 'Medium', 'Zid Eriksson', '2024-06-04 05:03:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('PE kunde inte arbeta med få anslutnigar till db (10) det köades upp för många och krashade.\r\n\r\nLa på maxtak för 100\r\n\r\nHavar ska kolla koden för kontextstödet', NULL, 'Critical', 'Zid Eriksson', '2024-06-03 09:57:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat startade inte efter nattens uppdatering,passa på år rensa jar filerna och startade den', NULL, 'Critical', 'Zid Eriksson', '2024-05-31 05:44:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Startade om några kunder pga jar fil rensing', NULL, 'Medium', 'Zid Eriksson', '2024-05-30 08:56:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Startade om pga jar fil rensing', NULL, 'Medium', 'Zid Eriksson', '2024-05-30 08:14:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Startade om pga jar fil rensing', NULL, 'Medium', 'Zid Eriksson', '2024-05-30 08:13:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Hicka, URLcheckern larmade kort om skövde', NULL, 'Low', 'Björn', '2024-05-07 15:02:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Segar ner till obrukligt stopp\r\nger den en omstart', NULL, 'Critical', 'Zid Eriksson', '2024-04-24 11:51:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('is no boot', NULL, 'Critical', 'Zid Eriksson', '2024-04-18 07:25:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-25 network interface cotroller NIC gick inte igång efter underhåll.', NULL, 'Critical', 'Zid Eriksson', '2024-04-15 23:52:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Service underhåll', NULL, 'Medium', 'Zid Eriksson', '2024-04-15 23:52:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mysql dog, startade upp och körde en repair', NULL, 'Critical', 'Zid Eriksson', '2024-04-09 22:22:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Omladdning av certifikat för *.infocaption.com låste servern, som kan ha orsakats av tidig v5.90 och hög belastning', NULL, 'Critical', 'Peter Jäderlund', '2024-04-09 11:38:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mysql dog, startade upp och körde en repair', NULL, 'Critical', 'Zid Eriksson', '2024-04-09 08:24:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Allmänt långsam. Startade om över lunchen', NULL, 'Critical', 'Björn Johansson', '2024-04-04 11:15:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat dog någon gång mellan 17 och 08', NULL, 'Critical', 'Björn Johansson', '2024-04-02 17:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('CPU går till 100% emellanåt och segar ned alla webappar. Ingen kund rapporterade', NULL, 'Medium', 'Björn Johansson', '2024-04-02 14:09:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Karolinska smartassuser tabell hade blivit korrupt\r\nReparerade', NULL, 'High', 'Zid Eriksson', '2024-03-28 22:22:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Disk tog slut och mysql crashade', NULL, 'Critical', 'Zid Eriksson', '2024-03-22 08:43:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Sega ner till stop, startade om, den ska få bättre hårdvara idag.', NULL, 'Critical', 'Zid Eriksson', '2024-03-21 10:52:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('X', NULL, 'Critical', 'Zid Eriksson', '2024-03-19 06:07:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('X', NULL, 'Critical', 'Zid Eriksson', '2024-03-19 06:07:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('X', NULL, 'Critical', 'Zid Eriksson', '2024-03-18 22:06:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('X', NULL, 'Critical', 'Zid Eriksson', '2024-03-18 22:06:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-18 10:50:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-18 09:05:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-18 09:01:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-18 07:15:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-18 07:15:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-17 21:53:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-17 20:38:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-17 19:03:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-17 11:42:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-17 10:25:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-17 10:25:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-17 01:05:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-16 19:32:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-16 15:29:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-16 10:25:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-16 10:25:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-16 10:15:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-16 10:14:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-16 10:14:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-15 23:43:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-15 19:50:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-15 17:00:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-15 12:00:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-15 06:00:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner till stopp', NULL, 'Critical', 'Zid Eriksson', '2024-03-15 06:00:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner startade om', NULL, 'Critical', 'Zid Eriksson', '2024-03-14 19:21:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sega ner', NULL, 'Critical', 'Zid Eriksson', '2024-03-13 20:29:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Servern sega ner.\r\nStarta om', NULL, 'Critical', 'Zid Eriksson', '2024-03-11 09:38:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Hängde sig helt och behövde en omstart', NULL, 'Critical', 'Zid Eriksson', '2024-03-10 20:00:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Morten Spaniland har klickat och accepterat malware applikation på sin dator', NULL, 'Security', 'Zid Eriksson', '2024-03-07 11:41:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Memory ran out', NULL, 'Critical', 'Zid Eriksson', '2024-03-07 08:39:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Upplevs segt', NULL, 'Medium', 'Zid Eriksson', '2024-03-06 15:25:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('IKEAs tomcat crashade pga\r\nSTATUS_HEAP_CORRUPTION (0xc0000374)', NULL, 'Critical', 'Zid Eriksson', '2024-03-06 11:28:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Minnet tagit slut', NULL, 'Critical', 'Zid Eriksson', '2024-03-03 08:00:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Trög på backend', NULL, 'Medium', 'Zid Eriksson', '2024-02-26 13:24:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Iver hade klantat med donator.se domän server som infocaption.com använder, detta gjorde så att alla infocaption.com blev onårbara', NULL, 'Critical', 'Zid Eriksson', '2024-02-20 08:00:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Slowed down. got a reboot\r\nIST has quite a bit of connects.', NULL, 'Critical', 'Zid Eriksson', '2024-02-13 00:40:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Slowed down to a halt, gave it a full reboot', NULL, 'Security', 'Zid Eriksson', '2024-02-06 15:49:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-18 hade inte startat efter nattlig omstart, den kunde inte släppa tråden', NULL, 'Critical', 'Zid Eriksson', '2024-02-01 08:08:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Kan ej någ kundportalen (server unavailable)', NULL, 'Medium', 'Elin', '2024-01-31 15:19:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Servern ville inte starta pga en fel konfiguration.\r\nHade lagt certifikatet för sala i fel mapp.\r\nDetta är åtgärdat.', NULL, 'Critical', 'Zid Eriksson', '2024-01-25 06:32:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppföljning på ic-25\r\nEn gammal testinstans verkade inte hålla med om java 11 och bestämde sig för att inte fungera.\r\nTog bort testinstansen och servern starta på 5 minuter', NULL, 'Critical', 'Zid Eriksson', '2024-01-19 09:28:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat crash', NULL, 'Critical', 'Zid Eriksson', '2024-01-19 08:02:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat verkar ha hängt sig vid uppstart efter nattens omstart', NULL, 'Critical', 'Zid Eriksson', '2024-01-19 07:13:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-18 sega ner fick omstart', NULL, 'Critical', 'Zid Eriksson', '2024-01-18 08:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('17on hade crashat vid omstart.\r\nRIP', NULL, 'Critical', 'Zid Eriksson', '2024-01-12 05:12:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('En tabell hade kollapsat på NCC, har lagat den', NULL, 'Medium', 'Zid Eriksson', '2024-01-10 12:03:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-25 sega ner till total crash', NULL, 'Critical', 'Zid Eriksson', '2024-01-08 13:52:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mysql stängde av sig på 9an', NULL, 'Critical', 'Zid Eriksson', '2023-12-28 07:05:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mysql stängde av sig på 22an', NULL, 'Critical', 'Zid Eriksson', '2023-12-27 14:05:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Query för mailutskick sänkte hela servern i 30 min', NULL, 'Critical', 'Björn Johansson(Zid)', '2023-12-18 14:09:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat hade krashat', NULL, 'Critical', 'Zid Eriksson', '2023-12-10 09:19:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat hade kraschat', NULL, 'Critical', 'Björn Johansson', '2023-12-06 17:44:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('MySQL omstartad, tjänsten hade stängt ned', NULL, 'Critical', 'Peter Jäderlund', '2023-12-05 12:55:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fick slut på minnet\r\nFår nog lov å öka denna å', NULL, 'Critical', 'Zid Eriksson', '2023-12-05 05:27:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Slut på minne', NULL, 'Critical', 'Zid Eriksson', '2023-12-04 09:56:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat stängdes av på ic21 vid 9 tiden', NULL, 'Critical', 'Zid Eriksson', '2023-12-03 09:35:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Jag upptäckte i dag(vid 9) att våran SMTP på InfoCaption-11 har nyttjats för spam utskick till random personer.\r\n(Den typen av spam utskick man vanligtvis får)\r\n\r\nJag å Peter har undersökt och kommit fram till att vi hade en whitelistad ip-range på 62.0.0.0/8 sen tidigare.\r\n\r\nOch denna tillåter då att våran SMTP kan stå som en relay nod för utskick från den ip range.\r\n\r\nDetta har någon då hittat och nyttjat för utskick\r\n\r\nIngen data har läckts, och inget konto har blivit förlorat.\r\nVåran SMTP har bara skickat vidare spam som kommit till den.\r\n\r\nVi har täppt detta hål genom att plocka bort den ip range.\r\nsamt så har vi stängt andra services på den SMTP servern som inte ska användas, och kommer stänga port 25.\r\nVi har även gjort en justering mot användare där, så vi har städat den lite.\r\n\r\nI slutsats:\r\nVi hade en öppning som kunde nyttjas\r\nVi stängde den\r\nVi gjort andra små städningar\r\nJag kommer övervaka den under dagen\r\n\r\nSäkerhetsincident åtgärdad\r\n', NULL, 'Security', 'Zid Eriksson', '2023-12-01 09:26:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat fick slut på minne', NULL, 'Critical', 'Björn Johansson', '2023-11-30 14:28:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Cache problemet är löst.\r\nMer minne kommer i kväll\r\n\r\nStartade om den vid lunch, segade ner ordentligt', NULL, 'Critical', 'Zid Eriksson', '2023-11-29 12:06:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('segade ner till nästan fullstop\r\n\r\nhar ökat cache', NULL, 'Critical', 'Zid Eriksson', '2023-11-22 10:01:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat fick slut på minne', NULL, 'Critical', 'Zid Eriksson', '2023-11-15 16:29:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Mysqlen hade stängt av sig', NULL, 'Critical', 'Zid Eriksson', '2023-11-12 10:15:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('5emman blev trött och fick sig en spark så den kom igång igen.', NULL, 'Critical', 'Zid Eriksson', '2023-11-09 14:50:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Den blev överarbetad.\r\nHåller ett öga på den', NULL, 'Critical', 'Zid Eriksson', '2023-10-27 15:42:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mysql på 10an hade dött', NULL, 'Critical', 'Zid Eriksson', '2023-10-27 05:29:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Fel konfiguration så att tomcat inte riktigt gick igång', NULL, 'Critical', 'Zid Eriksson', '2023-10-25 05:07:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('OOM', NULL, 'Critical', 'Zid Eriksson', '2023-10-25 03:39:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('out of memory', NULL, 'Critical', 'Zid Eriksson', '2023-10-18 11:54:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-23 Tomcat stoppad. ', NULL, 'Critical', 'Peter Jäderlund', '2023-10-17 00:53:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-23 nere, Tomcat stoppad', NULL, 'Critical', 'Peter Jäderlund', '2023-10-15 08:23:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-23 \r\njava.lang.OutOfMemoryError: GC overhead limit exceeded', NULL, 'Critical', 'Peter Jäderlund', '2023-10-10 09:13:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat-9-All inte igång efter nattens omstart. Omstartad 08.11', NULL, 'Critical', 'Peter Jäderlund', '2023-10-10 08:15:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat-All instansen på ic-20 var stoppad och inte igång.\r\n\r\nFel i Tomcat-loggen, direkt efter nattens omstart: Could not reserve enough space for object heap\r\n\r\nUppe igen 07.45\r\n', NULL, 'Critical', 'Peter Jäderlund', '2023-10-09 07:30:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-20 all gav upp\r\n\r\n16-Sep-2023 03:01:08.060 WARNING [Thread-74] org.apache.catalina.loader.WebappClassLoaderBase.clearReferencesThreads The web application [ROOT] appears to have started a thread named [Thread-9] but has failed to stop it. This is very likely to create a memory leak. Stack trace of thread:\r\n java.lang.Thread.sleep(Native Method)\r\n com.infocaption.smartass.email.EmailWorker.processQueue(EmailWorker.java:157)\r\n com.infocaption.smartass.email.EmailWorker.access$100(EmailWorker.java:32)\r\n com.infocaption.smartass.email.EmailWorker$1.run(EmailWorker.java:111)\r\n java.lang.Thread.run(Unknown Source)', NULL, 'Critical', 'zid', '2023-09-16 03:02:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Gav upp i dag, gav den en uppstart', NULL, 'Critical', 'Zid Eriksson', '2023-09-13 09:08:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('25 fick en uppdatering på tomcat och java\r\n\r\nKör nu java 11', NULL, 'Medium', 'Zid Eriksson', '2023-08-29 12:00:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Alla servrar förutom ikea och internal har fått tomcat 9.0.79', NULL, 'Medium', 'Zid Eriksson', '2023-08-24 04:27:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Slut på minne. Tomcat fastnade. Startade om maskinen.', NULL, 'Critical', 'Zid Eriksson', '2023-08-14 14:38:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Slut på minne. Tomcat fastnade. Startade om maskinen.', NULL, 'Critical', 'Björn Johansson', '2023-08-11 12:50:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Slut på minne. Startade om Tomcat och MySQL', NULL, 'Critical', 'Björn Johansson', '2023-08-08 08:00:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-17 hade helt tröttat ut sig, men gjorde en clean up på skräp och gav maskinen en full omstart, det verkar rulla på nu.', NULL, 'Critical', 'Zid Eriksson', '2023-08-01 14:43:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Minnet tog slut, startade om tomcat', NULL, 'Critical', 'Björn Johansson', '2023-08-01 10:50:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-9 blev trött slut på ram och överkokad cpu. satt omstart på varje natt tillsvidare', NULL, 'Critical', 'Zid Eriksson', '2023-07-03 08:00:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic-9 blev trött slut på ram och överkokad cpu. satt omstart på varje natt tillsvidare', NULL, 'Critical', 'Zid Eriksson', '2023-07-03 08:00:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Nattligt arbete med krympning av disk från 1,4TB disk till 750GB. Klart 06:10 ', NULL, 'Low', 'Peter Jäderlund', '2023-06-28 23:45:00', NULL, 'InfoCaption-14');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Mysql var avstängd, starta den och starta om tomcat', NULL, 'Critical', 'Zid Eriksson', '2023-06-27 06:49:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Tomcat fick sig en omstart pga minnesläcka', NULL, 'High', 'Zid Eriksson', '2023-06-22 11:13:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Minnesläcka', NULL, 'High', 'Zid Eriksson', '2023-06-19 06:01:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('ic10 tröttnade på livet.', NULL, 'Critical', 'Zid Eriksson', '2023-06-14 23:09:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('9an blev också väldigt trött\r\nminnet fullt och cpun överarbetar\r\nGav den en omstart', NULL, 'Critical', 'Zid Eriksson', '2023-06-12 08:46:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Ic 22 blev kloggad igen trots cpu uppdateringen, minnet fylls upp och gc hinner inte med, gav den en omstart.\r\n\r\nVi hitta eventuellt nå fel med tomcat restartern, kan vara så att den inte har körts och är då en bidragande faktor till varför den segar ned sig.', NULL, 'Critical', 'Zid Eriksson', '2023-06-12 08:00:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('MYSQL var nedstängt och tomcat hade hakat upp sig.\r\nGav den en omstart.', NULL, 'Critical', 'Zid Eriksson', '2023-06-12 07:42:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Startade om ic-22\r\ndå den segade ner rejält och flera kunder blev rejält påverkade', NULL, 'Medium', 'Zid Eriksson', '2023-06-08 14:00:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Vips SSO blev påverkad av uppdateringen till java 11', NULL, 'Medium', 'Zid Eriksson', '2023-06-08 00:00:00', NULL, 'InfoCaption-15');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('InfoCaption-22 är fortfarande låg, även efter minnesjustering.\r\n\r\nTrolig Orsak, PE Accounting har hög belastning', NULL, 'High', 'Zid Eriksson', '2023-06-08 13:45:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('InfoCaption-22 fick minnesproblem och hela servern startades om av Iver', NULL, 'Critical', 'Peter Jäderlund', '2023-06-08 08:54:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Generellt strul på flera servrar där molnkaniner uppdaterats. Uppstarten av Webapplikationerna har fastnat av okänd anledning ', NULL, 'Critical', 'Peter Jäderlund', '2023-06-05 08:52:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('InfoCaption-22 nere på kvällen: Out-of-memory i Tomcat-loggen', NULL, 'Critical', 'Peter Jäderlund', '2023-06-04 08:51:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Flyttat databsen för cms från 15 till 18', NULL, 'Change', 'Zid Eriksson', '2025-12-17 07:55:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('plockat ner hjelp2', NULL, 'Change', 'Zid Eriksson', '2025-12-17 08:20:00', NULL, 'InfoCaption-8');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Österåker dag 2', NULL, 'Change', 'BJ', '2025-12-17 09:30:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Stänger ner comfort', NULL, 'Change', 'Zid Eriksson', '2025-12-18 11:46:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppsättning 365 sudarkivera', NULL, 'Change', 'JH', '2025-12-18 14:25:00', NULL, 'InfoCaption-11');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat skövdes certifikat', NULL, 'Change', 'Zid Eriksson', '2025-12-19 09:05:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat salas certifikat', NULL, 'Change', 'Zid Eriksson', '2025-12-19 09:24:00', NULL, 'InfoCaption-24');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('lagt comfortkedjans webapp i "removed"', NULL, 'Change', 'JH', '2025-12-19 16:33:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat procydo.com', NULL, 'Change', 'Zid Eriksson', '2025-12-19 16:38:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('IC365 till Esem', NULL, 'Low', 'Anja Runestedt Palmér', '2025-12-29 11:19:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('smartasstest uppdaterades till senaste för test, men Java21 saknades på servern.\r\nDöpte om smartasstest och skapade enkel tom smartasstest och startade om servern. work-katalogen (cachen) togs bort för smartasstest innan omstarten', NULL, 'Change', 'Peter Jäderlund', '2026-01-08 08:45:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('tagit bort hosttag för nybro samt ronneby', NULL, 'Change', 'jonas hammarberg', '2026-01-08 10:29:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('tomcat dog pga för lite overhead minne', NULL, 'Critical', 'Zid Eriksson', '2026-01-08 13:01:00', NULL, 'InfoCaption-25');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('lagt på en åtgärd för sysscheduler som ska döda den på varje server 06:30', NULL, 'Change', 'Zid Eriksson', '2026-01-08 13:28:00', NULL, 'InfoCaption-2');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Starta om serern för att peinternal inte skulle flyttas än,omstart krävdes för rollback', NULL, 'High', 'Zid Eriksson', '2026-01-15 14:08:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Bytte domän på pe och peinternal till kleer och kleerinternal samt redirect', NULL, 'Change', 'Zid Eriksson', '2026-01-14 23:09:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('C:\\IC-Tools\\incident20260119\r\nVi ser att ett pentest startade 30 sek innan httpd.exe försvann. Pentestet omfattade endast proxyservern, ej tomcat.\r\nCisco hade karantänat filen, antagligen på grund av att den inte tyckte om de requests som kom in. Tjänsten är återställd och uppdaterades.', NULL, 'Security', 'BJ+ZE', '2026-01-19 12:31:00', NULL, 'InfoCaption-20');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Omstart av tomcat pga seg server', NULL, 'High', 'Zid Eriksson', '2026-01-19 16:28:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('satt upp PTS med SFTP på ic10', NULL, 'Change', 'Jonas Hammarberg', '2026-01-20 15:22:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Nybro till removed', NULL, 'Change', 'BJ', '2026-01-21 14:46:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Gjort allt annat för nedstängning nybro dag2', NULL, 'Change', 'BJ', '2026-01-21 14:50:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Ronnebydag2', NULL, 'Change', 'BJ', '2026-01-22 09:31:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat hsotest till 5.99', NULL, 'Change', 'Zid Eriksson', '2026-01-22 16:15:00', NULL, 'InfoCaption-23');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('mysql hade stängt ner sig pga crash', NULL, 'Critical', 'Zid Eriksson', '2026-01-26 15:34:00', NULL, 'InfoCaption-19');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdatering IC 365', NULL, 'Change', 'stefan.ekeroth@infocaption.com', '2026-02-02 20:03:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Flyttade TaD1 tillbaka till normal drift', NULL, 'Change', 'Zid Eriksson', '2026-01-29 23:40:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Updaterade tomcat til lsenaste', NULL, 'Change', 'Zid Eriksson', '2026-01-29 23:40:00', NULL, 'InfoCaption-10');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat guidecloud.se certifikat', NULL, 'Change', 'Zid Eriksson', '2026-02-02 07:18:00', NULL, 'Other');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('oxceed nedstängning dag1', NULL, 'Change', 'BJ', '2026-02-06 16:01:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Oxceed dag 2', NULL, 'Change', 'BJ', '2026-02-09 15:29:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('dedicare dag 1', NULL, 'Change', 'BJ', '2026-02-10 14:19:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Lindabs och älmhults processbackuper slutade funka 4/12', NULL, 'Medium', 'BJ', '2026-02-10 14:55:00', NULL, 'InfoCaption-22');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('dedicare dag 2', NULL, 'Change', 'BJ', '2026-02-11 11:20:00', NULL, 'InfoCaption-16');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('la på tomcat_18 user', NULL, 'Change', 'Zid Eriksson', '2026-02-11 23:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Bytte till tomcat_18 user för tomcat', NULL, 'Change', 'Zid Eriksson', '2026-02-12 12:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('tomcat ville inte starta med tomcat_18\r\nbytte tillbaka', NULL, 'Critical', 'Zid Eriksson', '2026-02-13 07:00:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat bodens certifikat', NULL, 'Change', 'Zid Eriksson', '2026-02-16 13:51:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat bodens certifikat', NULL, 'Change', 'Zid Eriksson', '2026-02-16 13:51:00', NULL, 'InfoCaption-17');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('uppdaterat smartguide.stenametall.com certifikat', NULL, 'Change', 'Zid Eriksson', '2026-02-16 13:57:00', NULL, 'InfoCaption-13');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('lagt på certifikat och host i förbererande av karlskrona domän flytt', NULL, 'Change', 'Zid Eriksson', '2026-02-16 14:29:00', NULL, 'InfoCaption-5');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Uppdaterat vips till 5.101 inline special bygg', NULL, 'Change', 'Zid Eriksson', '2026-02-19 08:12:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('Åtgärdat UTF-8 på vips', NULL, 'Change', 'Zid Eriksson', '2026-02-19 08:13:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('lagt på tomcat_18 som user ', NULL, 'Change', 'Zid Eriksson', '2026-02-20 00:14:00', NULL, 'InfoCaption-18');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('agresso har stängt av sin domän', NULL, 'High', 'Zid Eriksson', '2026-02-21 08:24:00', NULL, 'InfoCaption-9');
INSERT INTO `incidents` (`description`, `solution`, `level`, `reporter_name`, `incident_time`, `clear_time`, `server`) VALUES
	('sthlmstad fastna med en query', NULL, 'Medium', 'Zid Eriksson', '2026-02-24 10:56:00', NULL, 'InfoCaption-11');

-- Normalize reporter name variants
UPDATE `incidents` SET `reporter_name` = 'Björn Johansson' WHERE `reporter_name` IN ('BJ', 'Bjorn J', 'Bjorn', 'Bjorn Johansson', 'Björn Johansson(Zid)');
UPDATE `incidents` SET `reporter_name` = 'Jonas Hammarberg' WHERE `reporter_name` IN ('JH', 'jonas hammarberg', 'jonka spokmok (JH)');
UPDATE `incidents` SET `reporter_name` = 'Peter Jäderlund' WHERE `reporter_name` IN ('Peter Jaderlund', 'PeterJaderlund');
UPDATE `incidents` SET `reporter_name` = 'Stefan Ekeroth' WHERE `reporter_name` = 'stefan.ekeroth@infocaption.com';
UPDATE `incidents` SET `reporter_name` = 'Zid Eriksson' WHERE `reporter_name` = 'zid';
UPDATE `incidents` SET `reporter_name` = 'Björn Johansson + Zid Eriksson' WHERE `reporter_name` IN ('BJ/Zid', 'BJ+ZE');
UPDATE `incidents` SET `reporter_name` = 'Jonas Hammarberg + Björn Johansson' WHERE `reporter_name` = 'JH BJ';

-- Map reporters to user accounts where possible
UPDATE `incidents` i JOIN `users` u ON i.`reporter_name` = u.`full_name`
SET i.`reporter_user_id` = u.`id` WHERE i.`reporter_user_id` IS NULL;
