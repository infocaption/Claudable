-- Migration 005: Seed email templates (modernized versions of legacy .htm templates)
-- Templates use {{variables}} for per-recipient substitution:
--   {{serverUrl}}   - Recipient's server URL (from Listor contact data)
--   {{email}}       - Recipient's email address (always available)
--   {{companyName}} - Recipient's company name (from Listor contact data)
--   {{updateDate}}  - Global update date/time (set in send form)
-- Owner: user id 3 (zid@infocaption.com) — adjust if needed

-- ============================================================
-- 1. Uppdatering Online (SV)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Uppdatering Online (SV)', 'InfoCaption serveruppdatering - {{serverUrl}}',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#5458f2,#6c63ff);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">Planerad uppdatering</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hej!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      Nu &auml;r det snart dags f&ouml;r uppdatering av er server:<br>
      <strong style="color:#5458f2;">{{serverUrl}}</strong>
    </p>
    <div style="background:#f8f8ff;border-left:4px solid #5458f2;padding:14px 18px;border-radius:4px;margin:20px 0;">
      <span style="font-size:14px;color:#666;">Planerad tid:</span><br>
      <strong style="font-size:16px;color:#333;">{{updateDate}}</strong>
    </div>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:16px 0;">
      Uppdateringen sker automatiskt &ndash; ni beh&ouml;ver inte g&ouml;ra n&aring;got!
    </p>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      Om ni inte &ouml;nskar att er server uppdateras, kontakta v&aring;r support.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Med v&auml;nliga h&auml;lsningar<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 2. Uppdatering Online (EN)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Update Online (EN)', 'InfoCaption server update - {{serverUrl}}',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#5458f2,#6c63ff);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">Scheduled Update</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hi!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      It is time to update your server:<br>
      <strong style="color:#5458f2;">{{serverUrl}}</strong>
    </p>
    <div style="background:#f8f8ff;border-left:4px solid #5458f2;padding:14px 18px;border-radius:4px;margin:20px 0;">
      <span style="font-size:14px;color:#666;">Scheduled time:</span><br>
      <strong style="font-size:16px;color:#333;">{{updateDate}}</strong>
    </div>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:16px 0;">
      The update is applied automatically &ndash; you do not need to do anything!
    </p>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      If you do not wish your server to be updated, please contact our support.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Best regards<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 3. Uppdatering Online (NO)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Oppdatering Online (NO)', 'InfoCaption serveroppdatering - {{serverUrl}}',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#5458f2,#6c63ff);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">Planlagt oppdatering</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hei!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      N&aring; er det p&aring; tide &aring; oppdatere serveren din:<br>
      <strong style="color:#5458f2;">{{serverUrl}}</strong>
    </p>
    <div style="background:#f8f8ff;border-left:4px solid #5458f2;padding:14px 18px;border-radius:4px;margin:20px 0;">
      <span style="font-size:14px;color:#666;">Planlagt tid:</span><br>
      <strong style="font-size:16px;color:#333;">{{updateDate}}</strong>
    </div>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:16px 0;">
      Oppdateringen skjer automatisk &ndash; du trenger ikke gj&oslash;re noe!
    </p>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      Hvis du ikke &oslash;nsker at serveren din skal oppdateres, kontakt v&aring;r support.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Med vennlig hilsen<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 4. Ändrad tid för uppdatering (SV)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Ändrad tid uppdatering (SV)', 'OBS: Ändrad tid - serveruppdatering {{serverUrl}}',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#e8830c,#f59e0b);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">&#x26A0; &Auml;ndrad uppdateringstid</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hej!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      <strong>OBS:</strong> &Auml;ndrad tid f&ouml;r er serveruppdatering:<br>
      <strong style="color:#e8830c;">{{serverUrl}}</strong>
    </p>
    <div style="background:#fff8f0;border-left:4px solid #e8830c;padding:14px 18px;border-radius:4px;margin:20px 0;">
      <span style="font-size:14px;color:#666;">Ny planerad tid:</span><br>
      <strong style="font-size:16px;color:#333;">{{updateDate}}</strong>
    </div>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      Om ni inte &ouml;nskar att er server uppdateras, kontakta v&aring;r support.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Med v&auml;nliga h&auml;lsningar<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 5. Changed time for update (EN)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Changed update time (EN)', 'Note: Changed time - server update {{serverUrl}}',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#e8830c,#f59e0b);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">&#x26A0; Changed Update Time</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hi!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      <strong>Note:</strong> Changed time for your server update:<br>
      <strong style="color:#e8830c;">{{serverUrl}}</strong>
    </p>
    <div style="background:#fff8f0;border-left:4px solid #e8830c;padding:14px 18px;border-radius:4px;margin:20px 0;">
      <span style="font-size:14px;color:#666;">New scheduled time:</span><br>
      <strong style="font-size:16px;color:#333;">{{updateDate}}</strong>
    </div>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      If you do not wish your server to be updated, please contact our support.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Best regards<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 6. Endret tid for oppdatering (NO)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Endret tid oppdatering (NO)', 'Merk: Endret tid - serveroppdatering {{serverUrl}}',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#e8830c,#f59e0b);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">&#x26A0; Endret oppdateringstid</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hei!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      <strong>Merk:</strong> Endret tid for serveroppdateringen:<br>
      <strong style="color:#e8830c;">{{serverUrl}}</strong>
    </p>
    <div style="background:#fff8f0;border-left:4px solid #e8830c;padding:14px 18px;border-radius:4px;margin:20px 0;">
      <span style="font-size:14px;color:#666;">Ny planlagt tid:</span><br>
      <strong style="font-size:16px;color:#333;">{{updateDate}}</strong>
    </div>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      Hvis du ikke &oslash;nsker at serveren din skal oppdateres, kontakt v&aring;r support.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Med vennlig hilsen<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 7. Ny version lokal server (SV)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Ny version lokal server (SV)', 'Ny InfoCaption-version tillgänglig',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#10b981,#059669);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">&#x2728; Ny version tillg&auml;nglig</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hej!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      En ny version av InfoCaption server &auml;r nu tillg&auml;nglig p&aring; v&aring;r portal.
    </p>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      Kontakta oss om ni &ouml;nskar hj&auml;lp med uppdateringen.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Med v&auml;nliga h&auml;lsningar<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 8. New version local server (EN)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'New version local server (EN)', 'New InfoCaption version available',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#10b981,#059669);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">&#x2728; New Version Available</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hi!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      A new version of InfoCaption server is now available on our portal.
    </p>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      Contact us if you need help with the update.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Best regards<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 9. Ny versjon lokal server (NO)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Ny versjon lokal server (NO)', 'Ny InfoCaption-versjon tilgjengelig',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#10b981,#059669);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption Server</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">&#x2728; Ny versjon tilgjengelig</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">Hei!</p>
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      En ny versjon av InfoCaption server er n&aring; tilgjengelig p&aring; v&aring;r portal.
    </p>
    <p style="font-size:14px;color:#666;line-height:1.6;margin:16px 0 0;">
      Kontakt oss hvis du trenger hjelp med oppdateringen.
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Med vennlig hilsen<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');

-- ============================================================
-- 10. Generellt meddelande (SV/EN/NO - one template)
-- ============================================================
INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES
(3, 'Generellt meddelande', 'InfoCaption - Meddelande',
'<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:''Segoe UI'',''Open Sans'',Helvetica,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;">
<tr><td align="center" style="padding:30px 16px;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
  <tr><td style="background:linear-gradient(135deg,#5458f2,#6c63ff);padding:28px 32px;">
    <span style="font-size:24px;color:#ffffff;font-weight:600;">InfoCaption</span><br>
    <span style="font-size:16px;color:rgba(255,255,255,0.85);margin-top:6px;display:inline-block;">Meddelande</span>
  </td></tr>
  <tr><td style="padding:28px 32px 32px;">
    <p style="font-size:15px;color:#333;line-height:1.6;margin:0 0 16px;">
      Skriv ditt meddelande h&auml;r...
    </p>
  </td></tr>
  <tr><td style="background:#f9f9fb;padding:18px 32px;border-top:1px solid #eee;">
    <p style="font-size:13px;color:#999;margin:0;">Med v&auml;nliga h&auml;lsningar<br><strong style="color:#666;">InfoCaption</strong></p>
  </td></tr>
</table>
</td></tr></table></body>');
