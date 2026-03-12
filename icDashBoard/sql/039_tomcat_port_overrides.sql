-- 039: Port overrides for Tomcat health checks
-- When Apache24 sits in front, health checks need to hit port 80/443 instead of Tomcat's internal ports

ALTER TABLE tomcat_instances
  ADD COLUMN http_port_override INT NULL AFTER health_error,
  ADD COLUMN https_port_override INT NULL AFTER http_port_override;
