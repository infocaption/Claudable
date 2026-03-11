-- 014_protect_alla_group.sql
-- Adds a BEFORE DELETE trigger to prevent accidental deletion of the "Alla" group.
-- The "Alla" group is the implicit default group for all users.
-- Application-level protection exists in GroupManageServlet, but this adds DB-level safety.

DELIMITER //

DROP TRIGGER IF EXISTS trg_prevent_alla_delete //

CREATE TRIGGER trg_prevent_alla_delete
BEFORE DELETE ON `groups`
FOR EACH ROW
BEGIN
    IF OLD.name = 'Alla' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Cannot delete the default "Alla" group. This group is required by the system.';
    END IF;
END //

DELIMITER ;
