# Guide Planner — Modulspecifikation

Denna fil beskriver arkitektur, API, databasschema, frontend-mönster och kända fallgropar för Guide Planner-modulen. Använd den som kontext vid AI-assisterade ändringar.

---

## Översikt

Guide Planner är ett projektplaneringsverktyg för guideuppdateringar och översättningar. All data lagras i MySQL (delad mellan alla inloggade användare). Modulen laddas som en HTML-fil i en iframe inuti InfoCaption Dashboard.

**Filer:**

| Fil | Syfte |
|-----|-------|
| `modules/guide-planner/index.html` | Frontend (HTML + CSS + JS, allt i en fil) |
| `servlet/GuidePlannerApiServlet.java` | Backend REST API (15 endpoints) |
| `sql/015_guide_planner.sql` | Databasmigration (3 tabeller + modulregistrering) |

---

## Databasschema

### guide_projects

| Kolumn | Typ | Beskrivning |
|--------|-----|-------------|
| id | INT PK AUTO_INCREMENT | Projekt-ID |
| name | VARCHAR(255) NOT NULL | Projektnamn |
| created_by | INT NULL (FK users) | Skapare |
| created_at | TIMESTAMP | Skapandedatum |
| updated_at | TIMESTAMP (ON UPDATE) | Senast uppdaterad |

### guide_tasks

| Kolumn | Typ | Beskrivning |
|--------|-----|-------------|
| id | INT PK AUTO_INCREMENT | Task-ID |
| project_id | INT NOT NULL (FK guide_projects CASCADE) | Tillhör projekt |
| guide_id | VARCHAR(255) DEFAULT '' | Grupp-ID (härleds från sv_id) |
| sv_id | VARCHAR(255) DEFAULT '' | Svenskt guide-ID (t.ex. `SV-1234-01`) |
| en_id | VARCHAR(255) DEFAULT '' | Engelskt guide-ID |
| no_id | VARCHAR(255) DEFAULT '' | Norskt guide-ID |
| namn | VARCHAR(500) DEFAULT '' | Guidens namn |
| description | TEXT NULL | Beskrivning/kommentar |
| task_type | ENUM('new','update') DEFAULT 'update' | Ny guide eller uppdatering |
| status | ENUM('not_started','in_progress','bumped','skipped','completed') DEFAULT 'not_started' | Aktuell status |
| assignee | VARCHAR(255) DEFAULT '' | Tilldelad person (fritext) |
| sort_order | INT DEFAULT 0 | Sorteringsordning inom guide-grupp |
| started_at | TIMESTAMP NULL | Tidsstämpel när status sattes till `in_progress` |
| completed_at | TIMESTAMP NULL | Tidsstämpel när status sattes till `completed` |
| completion_time | VARCHAR(50) NULL | Beräknad tid (format `H:MM:SS` eller `M:SS`) |
| created_at | TIMESTAMP | Skapad |
| updated_at | TIMESTAMP (ON UPDATE) | Senast uppdaterad |

**Index:** `project_id`, `guide_id`, `status`

### guide_project_assignees

| Kolumn | Typ | Beskrivning |
|--------|-----|-------------|
| id | INT PK AUTO_INCREMENT | |
| project_id | INT NOT NULL (FK guide_projects CASCADE) | Tillhör projekt |
| assignee_name | VARCHAR(255) NOT NULL | Personens namn |

**UNIQUE:** `(project_id, assignee_name)`

### guide_id-härleding

`guide_id` härleds automatiskt från `sv_id` genom att ta bort den sista `-`-delen:
- `SV-1234-01` → `SV-1234`
- `SV-1234-02` → `SV-1234`
- `ABC` → `ABC` (inga bindestreck)

Tasks med samma `guide_id` grupperas visuellt i en kollapsbar grupp.

---

## REST API

**Bas-URL:** `/api/guide-planner`

All kommunikation sker med JSON. CSRF-token krävs för POST/PUT/DELETE (hanteras automatiskt av `ic-utils.js`). Session-auth krävs (401 om ej inloggad).

### Projekt

| Metod | Path | Beskrivning | Request body | Response |
|-------|------|-------------|-------------|----------|
| GET | `/projects` | Lista alla projekt | — | `[{id, name, taskCount, completedCount, createdAt, updatedAt}]` |
| POST | `/projects` | Skapa projekt | `{name}` | `{id, name}` |
| PUT | `/projects` | Byt namn | `{id, name}` | `{updated: N}` |
| DELETE | `/projects?id=X` | Ta bort projekt | — | `{deleted: N}` |

**Regler:** Det sista projektet kan inte tas bort (400-fel). Om inga projekt finns vid `init()` skapas "Projekt 1" automatiskt.

### Tasks

| Metod | Path | Beskrivning | Request body | Response |
|-------|------|-------------|-------------|----------|
| GET | `/tasks?projectId=X` | Lista tasks för ett projekt | — | `[{id, projectId, guideId, svId, enId, noId, namn, description, taskType, status, assignee, sortOrder, startedAt, completedAt, completionTime, createdAt, updatedAt}]` |
| POST | `/tasks` | Skapa en task | `{projectId, svId?, guideId?, namn?, description?, enId?, noId?, taskType?, status?, assignee?, sortOrder?}` | `{id}` |
| PUT | `/tasks` | Uppdatera task (partiell) | `{id, svId?, enId?, noId?, namn?, description?, assignee?, taskType?, status?, sortOrder?}` | `{updated: N}` |
| DELETE | `/tasks` | Ta bort tasks | `{ids: [1, 2, 3]}` | `{deleted: N}` |
| POST | `/tasks/bulk-update` | Bulk-uppdatera status/assignee | `{ids: [...], status?, assignee?}` | `{updated: N}` |
| POST | `/tasks/reorder` | Flytta task inom grupp | `{projectId, guideId, taskId, newIndex}` | `{reordered: true}` |
| POST | `/tasks/import` | Importera tasks | `{projectId, tasks: [...], mode: "merge"\|"replace"}` | `{imported: N}` |

### Assignees

| Metod | Path | Beskrivning | Request body | Response |
|-------|------|-------------|-------------|----------|
| GET | `/assignees?projectId=X` | Lista assignees | — | `["Anna", "Erik", ...]` |
| POST | `/assignees` | Lägg till assignee | `{projectId, name}` | `{added: true}` |
| DELETE | `/assignees?projectId=X&name=Y` | Ta bort assignee | — | `{deleted: N}` |

### Status-sidoeffekter (server)

Servern applicerar tidsstämplar automatiskt vid statusändringar:

| Ny status | SQL-effekt |
|-----------|-----------|
| `in_progress` | `started_at = COALESCE(started_at, NOW())`, `completed_at = NULL`, `completion_time = NULL` |
| `completed` | `started_at = COALESCE(started_at, NOW())`, `completed_at = NOW()` |
| Övriga (`not_started`, `bumped`, `skipped`) | `started_at = NULL`, `completed_at = NULL`, `completion_time = NULL` |

### Import-lägen

- **merge** (default): Om `sv_id` redan finns i projektet → UPDATE (namn, description, en_id, no_id, task_type, assignee). Annars INSERT.
- **replace**: DELETE alla tasks i projektet, sedan INSERT alla nya.

---

## Frontend-arkitektur

### State Management

All state lagras i en global variabel `state`:

```javascript
state = {
    version: 2,
    activeProjectId: <int>,
    projects: [
        {
            id: <int>,            // Server-genererat INT
            name: "Projekt 1",
            data: {
                version: 2,
                tasks: [...],          // Array av task-objekt
                knownAssignees: [...], // Array av strängar
                lastModified: "ISO"
            }
        }
    ],
    _ui: {
        selectedIds: Set(),
        expandedGroups: Set(),
        filters: { search: '', statuses: [], assignees: [] },
        sort: { field: 'sortOrder', direction: 'asc' },
        importModalOpen: false,
        settingsModalOpen: false
    }
}
```

### Reducer-mönster

Alla state-ändringar går via `dispatch(action)`:

1. **Lokal mutation** — Reducer uppdaterar `state` direkt (optimistic update)
2. **API-synk** — `apiSync(action)` körs asynkront (fire-and-forget med error toast)
3. **Render** — `render()` anropas omedelbart efter lokal mutation

**Reducer-actions:**

| Action | Payload | Beskrivning |
|--------|---------|-------------|
| `UPDATE_TASK` | `{id, updates}` | Partiell uppdatering av en task |
| `ADD_TASK` | task-objekt | Lägg till ny task |
| `DELETE_TASKS` | `[id, id, ...]` | Ta bort tasks |
| `IMPORT_TASKS` | `{newTasks, mode}` | Importera tasks (merge/replace) |
| `BULK_UPDATE_STATUS` | `{ids, status}` | Bulk-ändra status |
| `BULK_UPDATE_ASSIGNEE` | `{ids, assignee}` | Bulk-tilldela |
| `REORDER_TASK` | `{taskId, newIndex, guideId}` | Drag-and-drop omsortering |
| `TOGGLE_SELECTION` | taskId | Toggle markering |
| `SELECT_ALL_VISIBLE` | `[id, ...]` | Markera alla synliga |
| `CLEAR_SELECTION` | — | Avmarkera alla |
| `SET_FILTER` | `{search?, statuses?, assignees?}` | Uppdatera filter |
| `RESET_FILTERS` | — | Rensa alla filter |
| `SET_SORT` | `{field, direction}` | Sortering |
| `TOGGLE_EXPAND` | guideId | Toggle grupp-expansion |
| `EXPAND_ALL` | `[guideId, ...]` | Expandera alla grupper |
| `COLLAPSE_ALL` | — | Fäll ihop alla |
| `SWITCH_PROJECT` | projectId | Byt aktivt projekt (laddar tasks från API) |
| `ADD_PROJECT` | name | Skapa nytt projekt |
| `RENAME_PROJECT` | `{id, name}` | Byt namn på projekt |
| `DELETE_PROJECT` | projectId | Ta bort projekt |
| `SET_ASSIGNEES` | `[name, ...]` | Uppdatera assignee-listan |
| `CLEAR_ALL_DATA` | — | Rensa alla tasks i aktivt projekt |
| `SET_IMPORT_MODAL` | boolean | Öppna/stäng import-modal |
| `SET_SETTINGS_MODAL` | boolean | Öppna/stäng team-inställningar |

### API-funktionen

```javascript
async function api(path, options = {}) {
    const url = API_BASE + path;   // API_BASE = '../../api/guide-planner'
    if (!options.headers) options.headers = {};
    if (!options.headers['Content-Type']) options.headers['Content-Type'] = 'application/json';
    const resp = await fetch(url, options);
    // ... felhantering ...
}
```

**VIKTIGT:** CSRF-token injiceras automatiskt av `ic-utils.js` globala `fetch`-wrapper. Modulen ska INTE hantera CSRF manuellt.

### Task-ID:n (kritiskt)

- Server genererar `INT` ID:n (auto_increment)
- Vid `ADD_TASK`: lokal task skapas med UUID som temporärt ID. Efter `POST /tasks` returnerar servern det riktiga ID:t, som ersätter UUID:n i state
- Vid `IMPORT_TASKS`: hela task-listan laddas om från servern (`reloadCurrentProject()`) för att få korrekta ID:n
- HTML `data-*` attribut returnerar alltid **strängar** via `dataset` — alla ID-extraheringar MÅSTE använda `parseInt(value, 10)`

### Event-hantering

All event-hantering sker via **click delegation** på `#app` och `document`:

- `data-action` attribut på knappar styr vilken handling som utförs
- `data-tid` = task-ID, `data-gid` = guide-grupp-ID, `data-pid` = projekt-ID
- `data-dd` = dropdown-typ (status, assignee, status-filter, assignee-filter)
- `data-dd-action` = dropdown-item-handling (set-status, set-assignee, etc.)
- `data-edit-task` + `data-edit-field` = inline-redigering

### Modaler

Tre modaler finns: **Import**, **Inställningar (Team)**, **Bekräfta**

- Modaler appendas till `document.body` (utanför `#app`)
- Varje modal har ett wrapper-element med unikt ID (`import-modal-wrap`, `settings-modal-wrap`, `confirm-modal-wrap`)
- `cleanupModal(id)` tar bort wrapper-elementet
- Overlay `.gp-overlay` har `data-action`-attribut för click-outside-to-close
- **Ingen `stopPropagation()`** på `.gp-modal` (det blockerar delegerade klick-handlers)

### Bekräftelse-dialog

Destruktiva actions visar en bekräftelse-modal:

```javascript
confirmAction = { msg: 'Text att visa', fn: () => { /* körs vid "Bekräfta" */ } };
render();
```

Följande actions kräver bekräftelse:
- Rensa alla tasks
- Ta bort projekt-tab
- Ta bort guide-grupp
- Bulk-ta bort markerade tasks

### Dropdown-menyer

Dropdowns renderas som absolut positionerade element direkt på `document.body`:
- Positioneras relativt till ankaret (`getBoundingClientRect`)
- Kan öppnas uppåt om det saknas plats nedanför
- Stängs vid klick utanför eller Escape
- Typ: `status` (per task), `assignee` (per task), `status-filter` (toolbar), `assignee-filter` (toolbar)

---

## CSS-konventioner

Alla klasser prefixas med `gp-` (guide planner):

| Prefix | Område |
|--------|--------|
| `.gp-header*` | Sticky header |
| `.gp-tab*` | Projekt-flikar |
| `.gp-toolbar`, `.gp-search*`, `.gp-filter*` | Toolbar, sök, filter |
| `.gp-bulk*` | Bulk-action bar (markerade) |
| `.gp-group`, `.gp-ghdr*` | Guide-gruppering |
| `.gp-table`, `.gp-drag`, `.gp-chk` | Task-tabell |
| `.gp-status*` | Status-pill |
| `.gp-editable`, `.gp-edit-input` | Inline-redigering |
| `.gp-id-*` | ID-celler (SV/EN/NO) |
| `.gp-add-*` | Lägg till-formulär |
| `.gp-overlay`, `.gp-modal*` | Modaler |
| `.gp-import-*` | Import-specifikt |
| `.gp-member*`, `.gp-avatar` | Team-inställningar |
| `.gp-toast*` | Toast-notifikationer |
| `.gp-dropdown*` | Dropdown-menyer |
| `.gp-chip*` | Filter-chips |
| `.gp-progress*` | Framstegsindikator |
| `.gp-empty*` | Tomt tillstånd |
| `.gp-footer` | Sidfot |

CSS-variabler från dashboardens designsystem (`ic-styles.css`) används:
- `--ic-primary` (#5458F2), `--ic-secondary` (#D4D5FC)
- `--ic-bg-light`, `--ic-text-dark`, `--ic-text-muted`
- `--ic-success`, `--ic-danger`
- `--ic-radius-sm`, `--ic-radius-md`, `--ic-radius-lg`

---

## Import/Export

### Import-format

Stöder TSV (tab-separerad, klistrad från Excel) och CSV (komma-separerad fil):

**Kolumnmappning (case-insensitive):**

| Kolumnnamn (ingång) | Fältnamn |
|---------------------|----------|
| `sv id`, `sv_id`, `svid`, `swedish id`, `guide-id`, `guideid`, `guide id` | `svId` |
| `namn`, `name`, `titel`, `title` | `namn` |
| `kommentar`, `comment`, `comments`, `description` | `description` |
| `eng id`, `eng_id`, `engid`, `english id`, `en id`, `en_id`, `enid` | `enId` |
| `no id`, `no_id`, `noid`, `norwegian id` | `noId` |
| `task type`, `tasktype`, `task_type`, `type` | `taskType` |
| `assignee`, `assigned to` | `assignee` |

### Export

CSV med kolumner: Typ, Namn, SV ID, EN ID, NO ID, Status, Beskrivning, Tilldelad, Tid.
Använder `ICUtils.exportToCSV()` om tillgänglig, annars Blob-download.

---

## Backend-mönster

### Servlet-struktur

Följer samma mönster som övriga servlets i projektet:

- `init()` med `CREATE TABLE IF NOT EXISTS` (idempotent)
- Session-auth via `session.getAttribute("user")` → `User`
- Path-routing via `req.getPathInfo()` i `doGet/doPost/doPut/doDelete`
- JSON-output med `StringBuilder` + `JsonUtil.quote()` (ingen JSON-lib)
- JSON-parsning med regex: `extractJsonString()`, `extractJsonInt()`, `extractJsonIntArray()`, `extractJsonObjectArray()`
- `try-with-resources` för alla JDBC-operationer
- Parametriserade SQL-frågor (förhindrar SQL-injection)

### Partiell uppdatering (PUT /tasks)

Servern bygger dynamiskt `UPDATE ... SET`-satser:
- `paramClauses` — kolumner med `?`-parametrar (strängar, int)
- `literalClauses` — kolumner med literal SQL (t.ex. `started_at = NOW()`)
- Kombineras: `UPDATE guide_tasks SET [paramClauses], [literalClauses] WHERE id = ?`

### Automatisk assignee-synk

När en task tilldelas en person (`assignee`), läggs personen automatiskt till i `guide_project_assignees` via `INSERT IGNORE`.

### web.xml-registrering

```xml
<servlet>
    <servlet-name>GuidePlannerApiServlet</servlet-name>
    <servlet-class>com.infocaption.dashboard.servlet.GuidePlannerApiServlet</servlet-class>
    <load-on-startup>9</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>GuidePlannerApiServlet</servlet-name>
    <url-pattern>/api/guide-planner/*</url-pattern>
</servlet-mapping>
```

---

## Kända fallgropar och gotchas

### 1. parseInt för dataset-värden (KRITISKT)

HTML `data-*`-attribut returnerar alltid strängar via `dataset`. Task-ID:n från API:et är heltal. `===`-jämförelse mellan sträng och heltal misslyckas alltid:

```javascript
// FEL: 1 === "1" → false
const tid = btn.dataset.tid;

// RÄTT:
const tid = parseInt(btn.dataset.tid, 10);
```

Alla ställen som extraherar `data-tid`, `data-pid`, `data-idx` MÅSTE använda `parseInt(value, 10)`.

### 2. CSRF hanteras av ic-utils.js

Modulen ska INTE ha manuell CSRF-hantering. `ic-utils.js` (inkluderad via `<script src="../../shared/ic-utils.js">`) wrapar `window.fetch` globalt och injicerar `X-CSRF-Token` header automatiskt på POST/PUT/DELETE.

Om du lägger till manuell CSRF-kod (postMessage, header-injektion) kommer det att krocka med ic-utils.js-wrappern.

### 3. Ingen stopPropagation på modal-containrar

Modaler använder delegerade klick-handlers på `document`-nivå. Om `.gp-modal` har `onclick="event.stopPropagation()"` blockeras alla knappar inuti modalen.

### 4. Optimistic updates

State uppdateras lokalt INNAN API-anropet görs. Om API-anropet misslyckas visas en error-toast men lokalt state har redan ändrats. Vid kritiska operationer (import, bulk-update) laddas data om från servern efteråt (`reloadCurrentProject()`).

### 5. Task-ID-livscykel

Nyss skapade tasks har ett temporärt UUID som ID. Efter att servern svarar med det riktiga INT-ID:t uppdateras det lokalt:

```javascript
const localTask = proj.data.tasks.find(t => t.id === task.id);
if (localTask && result.id) localTask.id = result.id;
```

Om en användare klickar snabbt på en nyss skapad task innan servern svarat, kan ID:t fortfarande vara ett UUID, vilket gör att API-anrop med det ID:t misslyckas.

### 6. Reorder-transaktion

`POST /tasks/reorder` kör i en databastransaktion. Den hämtar alla tasks i guide-gruppen, tar bort den flyttade tasken, sätter in den på ny position, och uppdaterar `sort_order` för alla via batch-update.

### 7. Ingen JSON-library

Backend använder `StringBuilder` + `JsonUtil.quote()` för JSON-output och regex-baserad parsning. Lägg INTE till Jackson/Gson.

### 8. completion_time beräknas på klienten

Tiden beräknas som skillnaden mellan `startedAt` och `completedAt`:
- Frontend: `calcCompletionTime(start, end)` → format `H:MM:SS` eller `M:SS`
- Backend: Sätter `started_at`/`completed_at` via SQL `NOW()` men beräknar INTE `completion_time` — det hanteras av klienten vid optimistic update. Vid bulk-update laddas data om från servern.

### 9. ic-utils.js beroenden

Modulen använder `ICUtils` för:
- `ICUtils.notifyReady('guide-planner')` — signalerar att modulen är laddad
- `ICUtils.showToast(message, type)` — om tillgänglig, delegerar toast till dashboard
- `ICUtils.exportToCSV(data, filename, headers)` — om tillgänglig, använder dashboardens exportfunktion

---

## Statusflöde

```
not_started (grå)
    ↓
in_progress (gul) ← kräver att assignee finns (UI-validering)
    ↓
completed (grön) ← sätter completedAt, beräknar completion_time

bumped (orange) ← kan sättas från vilken status som helst
skipped (röd)   ← kan sättas från vilken status som helst
```

Statuskonfig:

| Status | Label | Bakgrund | Dot-färg |
|--------|-------|----------|----------|
| `not_started` | Ej startad | #e5e7eb | #9ca3af |
| `in_progress` | Pågående | #fef3c7 | #f59e0b |
| `bumped` | Bumpad | #ffedd5 | #f97316 |
| `skipped` | Hoppad | #fee2e2 | #ef4444 |
| `completed` | Klar | #d1fae5 | #10b981 |

---

## UI-komponenter

### Header
Sticky, gradient-bakgrund. Visar totalt antal tasks. Knappar: Lägg till, Importera, Exportera, Team, Rensa.

### Projekt-flikar
Horisontell tabbar. Hover visar rename/delete. Nytt projekt via `+ Nytt projekt`. Aktiv flik markeras med primary-färg.

### Toolbar
Sökfält (filtrerar på alla textfält), status-filter dropdown, assignee-filter dropdown, filter-chips, expand/collapse alla-knappar.

### Bulk-action bar
Visas när tasks är markerade. Innehåller: statusändring (select), assignee-input, ta bort-knapp, avmarkera-knapp.

### Guide-grupper
Kollapsbar grupp per `guide_id`. Header visar: chevron, guide-ID, guidens namn, antal tasks, framstegsbar, procent, status-prickar. Delete-knapp syns vid hover.

### Task-tabell
Kolumner: Drag-handtag, Checkbox, Typ (Ny/Upd badge), Namn (klickbar länk till guide), ID (SV/EN/NO, redigerbar), Status (pill-knapp → dropdown), Beskrivning (klickbar inline-edit), Tilldelad (avatar + namn → dropdown), Tid.

### Inline-redigering
Klicka på `.gp-editable` → ersätts med input/textarea. Enter sparar, Escape avbryter, focusout sparar. ID-redigering stöder Tab mellan SV/EN/NO-fält.

---

## Filstruktur (enstaka HTML-fil)

Koden är organiserad i sektioner markerade med `// ===== RUBRIK =====`:

1. **CONSTANTS** — API_BASE, STATUS_CFG, ALL_STATUSES, COLUMN_MAP
2. **API LAYER** — `api()` hjälpfunktion
3. **UTILITIES** — uid, datum, deriveGuideId, esc, debounce
4. **STATE** — state, editing, editingIds, openDD, etc.
5. **STATUS SIDE EFFECTS** — `applyStatusFx()` lokal tidsstämpelhantering
6. **REDUCER** — `dispatch()` med alla actions
7. **API SYNC** — `apiSync()` asynkron API-kommunikation per action
8. **STORAGE** — `loadState()`, `mapTaskFromApi()`
9. **IMPORT/EXPORT** — parseTSV, parseCSVText, exportCSV, downloadCSV
10. **FILTERING & GROUPING** — getFilteredTasks, getGroupedTasks
11. **TOAST** — showToast, renderToasts
12. **RENDERING** — render(), renderHeader, renderTabs, renderToolbar, renderBulkBar, renderGroup, renderTaskTable, renderTaskRow, renderAddForm, renderEmptyState, modaler, dropdowns
13. **EVENT HANDLING** — setupEvents (click, change, input, keydown, focusout, drag-and-drop)
14. **INITIALIZATION** — init(), loadState, setupEvents, render, notifyReady
