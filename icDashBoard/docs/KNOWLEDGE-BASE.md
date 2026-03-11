# Kunskapsbas som MCP-endpoints

## Översikt

Kunskapsbasen låter administratörer ladda upp Markdown-dokument i dashboarden,
gruppera dem i **samlingar** (collections) och exponera varje samling som en
MCP-endpoint. AI-agenter som kopplar in via MCP-gatewayen kan sedan söka, lista
och läsa dokument från specifika samlingar — utan att behöva nå filsystemet.

### Nyckelbegrepp

| Begrepp | Beskrivning |
|---------|-------------|
| **Dokument** | En MD-fil med titel, slug, innehåll och taggar. Lagras i databasen. |
| **Samling** | En namngiven grupp av dokument med ett unikt `tool_prefix`. Varje aktiv samling exponerar 3 MCP-tools. |
| **N:N-koppling** | Ett dokument kan tillhöra flera samlingar, en samling kan innehålla många dokument. |
| **tool_prefix** | MCP-namnrymd, t.ex. `support_docs`. Tools namnges `{prefix}__search_documents`. |

---

## Arkitektur

```
                    ┌──────────────────────────┐
                    │     AI-agent (Claude)     │
                    │  .mcp.json → Bearer token │
                    └────────────┬─────────────┘
                                 │ JSON-RPC 2.0
                                 ▼
                    ┌──────────────────────────┐
                    │   McpGatewayServlet       │
                    │   POST /api/mcp           │
                    │   (auth, rate limit)       │
                    └────────────┬─────────────┘
                                 │
                    ┌────────────▼─────────────┐
                    │   McpClientManager        │
                    │                           │
                    │  1. getAggregatedTools()  │
                    │     → upstream + KB tools │
                    │                           │
                    │  2. routeToolCall()        │
                    │     → isKbPrefix? → intern │
                    │     → else → upstream HTTP │
                    └────────────┬─────────────┘
                                 │ (intern, ingen HTTP)
                    ┌────────────▼─────────────┐
                    │   KnowledgeBaseUtil        │
                    │                           │
                    │  search_documents(query)  │
                    │  get_document(slug)        │
                    │  list_documents([tag])     │
                    │                           │
                    │  MySQL FULLTEXT-sökning    │
                    └────────────┬─────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                   ▼
        kb_documents    kb_collections    kb_collection_documents
```

KB-samlingar behandlas som **interna MCP-servrar utan nätverkslatens**.
`serverId = -1` i audit-loggen markerar KB-anrop.

---

## Databasschema

### kb_documents

| Kolumn | Typ | Beskrivning |
|--------|-----|-------------|
| id | INT PK | Auto-increment |
| slug | VARCHAR(255) UNIQUE | URL-vänlig identifierare |
| title | VARCHAR(500) | Dokumenttitel |
| content | MEDIUMTEXT | Fullständigt Markdown-innehåll |
| file_type | VARCHAR(50) | `markdown`, `text`, etc. |
| tags | VARCHAR(1000) | JSON-array, t.ex. `["api","security"]` |
| created_by | INT FK → users | Skapare |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | Auto-uppdateras |

Index: UNIQUE på `slug`, B-tree på `title(100)`.

### kb_collections

| Kolumn | Typ | Beskrivning |
|--------|-----|-------------|
| id | INT PK | Auto-increment |
| name | VARCHAR(255) | Visningsnamn |
| description | TEXT | Beskrivning (visas i MCP tool description) |
| tool_prefix | VARCHAR(100) UNIQUE | MCP-namnrymd, t.ex. `support_docs` |
| is_active | TINYINT(1) | 1 = aktiv → exponerar tools |
| created_by | INT FK → users | Skapare |

### kb_collection_documents (N:N)

| Kolumn | Typ | Beskrivning |
|--------|-----|-------------|
| id | INT PK | Auto-increment |
| collection_id | INT FK → kb_collections | CASCADE DELETE |
| document_id | INT FK → kb_documents | CASCADE DELETE |
| sort_order | INT | Sorteringsordning |
| added_at | TIMESTAMP | |

UNIQUE INDEX på `(collection_id, document_id)`.

### AppConfig-nycklar

| Nyckel | Default | Beskrivning |
|--------|---------|-------------|
| `kb.enabled` | `true` | Master-switch för KB MCP-tools |
| `kb.maxDocumentSize` | `1048576` | Max storlek per dokument (bytes) |

---

## MCP Tools

Varje aktiv samling exponerar 3 tools. Exempel med prefix `support_docs`:

### support_docs__search_documents

Fulltext-sökning i samlingens dokument (MySQL `MATCH...AGAINST` i `BOOLEAN MODE`).

```json
{
  "name": "support_docs__search_documents",
  "arguments": {
    "query": "API autentisering",
    "tags": ["security"]
  }
}
```

Returnerar: Rankat sökresultat med titel, slug och taggar (max 20 träffar).

### support_docs__get_document

Hämtar ett specifikt dokuments fullständiga innehåll.

```json
{
  "name": "support_docs__get_document",
  "arguments": {
    "slug": "api-auth-guide"
  }
}
```

Returnerar: Titel, taggar, uppdateringsdatum och hela Markdown-innehållet.

### support_docs__list_documents

Listar alla dokument i samlingen.

```json
{
  "name": "support_docs__list_documents",
  "arguments": {
    "tag": "api"
  }
}
```

Returnerar: Markdown-tabell med titel, slug, taggar och datum.

---

## Admin API (KbAdminServlet)

Alla endpoints kräver admin-session (via `AdminUtil.requireAdmin`).

### Dokument

| Metod | URL | Funktion |
|-------|-----|----------|
| GET | `/api/admin/kb/documents` | Lista alla (utan content) |
| GET | `/api/admin/kb/documents?id=N` | Hämta ett med content |
| POST | `/api/admin/kb/documents` | Skapa (JSON: title, slug, content, tags) |
| PUT | `/api/admin/kb/documents` | Uppdatera (JSON: id + valfria fält) |
| DELETE | `/api/admin/kb/documents?id=N` | Radera |

### Samlingar

| Metod | URL | Funktion |
|-------|-----|----------|
| GET | `/api/admin/kb/collections` | Lista alla med dokumentantal |
| POST | `/api/admin/kb/collections` | Skapa (JSON: name, toolPrefix, description) |
| PUT | `/api/admin/kb/collections` | Uppdatera (JSON: id + valfria fält) |
| DELETE | `/api/admin/kb/collections?id=N` | Radera (CASCADE) |

### Kopplingar

| Metod | URL | Funktion |
|-------|-----|----------|
| GET | `/api/admin/kb/collections/documents?id=N` | Lista dokument i samling |
| PUT | `/api/admin/kb/collections/documents` | Sätt dokument för samling (JSON: collectionId, documentIds[]) |

---

## Admin-gränssnitt

Fliken **Kunskapsbas** i Admin-panelen (`admin.jsp`):

```
┌─────────────────────────────────────────────────┐
│ [+ Nytt dokument]  [+ Ny samling]  [🔍 Sök...] │
│                                                 │
│ ┌─ Dokument ──────────────────────────────────┐ │
│ │ Titel          Slug         Taggar   Datum  │ │
│ │ API Guide      api-guide    [api]    3/11   │ │
│ │ Onboarding     onboarding   [hr]     3/10   │ │
│ └─────────────────────────────────────────────┘ │
│                                                 │
│ ┌─ Samlingar (MCP-endpoints) ────────────────┐  │
│ │ ┌───────────────┐  ┌───────────────┐       │  │
│ │ │ Support       │  │ Intern        │       │  │
│ │ │ support_docs  │  │ internal      │       │  │
│ │ │ 5 dok ✅ Aktiv │  │ 3 dok ✅ Aktiv │       │  │
│ │ │ [✏️][🔗][🗑️]  │  │ [✏️][🔗][🗑️]  │       │  │
│ │ └───────────────┘  └───────────────┘       │  │
│ └────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

**Modaler:**
1. **Dokumentmodal** — Titel, slug (auto-genereras), filtyp, taggar (kommaseparerat), innehåll (monospace textarea)
2. **Samlingsmodal** — Namn, tool_prefix (auto-genereras), beskrivning, aktiv-toggle
3. **Kopplingsmodal** — Klicka 🔗 på en samling → checkboxlista med alla dokument

---

## Konfiguration för AI-agent

### Claude Code (projektspecifik)

Skapa `.mcp.json` i projektroten:

```json
{
  "mcpServers": {
    "icdashboard-kb": {
      "type": "url",
      "url": "https://icdashboard.infocaption.com:8443/icDashBoard/api/mcp",
      "headers": {
        "Authorization": "Bearer DIN_API_TOKEN"
      }
    }
  }
}
```

> **OBS:** Lägg till `.mcp.json` i `.gitignore` — filen innehåller API-token.

### API-token

Skapa via dashboard: **Admin → Inställningar → API-tokens**.
Token-formatet: `icd_XXXXX...` (visas bara en gång vid skapande).

### Verifiering

```bash
# Handshake
curl -sk -X POST "$URL" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# Lista tools
curl -sk -X POST "$URL" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# Anropa tool
curl -sk -X POST "$URL" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
    "name":"PREFIX__list_documents","arguments":{}}}'
```

---

## Filer

| Fil | Syfte |
|-----|-------|
| `sql/032_knowledge_base.sql` | Migration: 3 tabeller + AppConfig |
| `util/KnowledgeBaseUtil.java` | MCP tool-handler (search/get/list) |
| `servlet/KbAdminServlet.java` | Admin CRUD REST API |
| `util/McpClientManager.java` | Integration: aggregerar KB-tools + routing |
| `webapp/admin.jsp` | Admin-flik med dokument/samlings-UI |
| `WEB-INF/web.xml` | Servlet-registrering (`/api/admin/kb/*`) |

---

## Målbild

### Fas 1 — Grundfunktionalitet ✅ (Implementerad)

- [x] Admin skapar dokument (MD-filer) i dashboarden
- [x] Admin skapar samlingar med tool_prefix
- [x] N:N-koppling dokument ↔ samlingar
- [x] MCP Gateway exponerar 3 tools per aktiv samling
- [x] Fulltext-sökning via MySQL FULLTEXT
- [x] AI-agenter kan söka, lista och läsa dokument via MCP
- [x] Audit-loggning av alla tool-anrop

### Fas 2 — Förbättringar (Planerat)

- [ ] **Filuppladdning** — Dra-och-släpp MD-filer direkt i admin-UI
- [ ] **Versionshistorik** — Spåra ändringar per dokument (diff-vy)
- [ ] **Markdown-preview** — Live-förhandsgranskning i dokumentmodalen
- [ ] **Import/export** — Bulk-import av MD-filer från mapp, export som ZIP
- [ ] **Tagg-autocomplete** — Föreslå befintliga taggar vid redigering
- [ ] **Dokumentsökning i admin** — Server-side fulltext-sökning i admin-UI

### Fas 3 — Avancerat (Framtida)

- [ ] **Embedding-baserad sökning** — Semantisk sökning med vektorindex
- [ ] **Automatisk indexering** — Webhook vid dokumentändring → cache-invalidering
- [ ] **Åtkomstkontroll per samling** — Koppla samlingar till grupper/roller
- [ ] **Användningsstatistik** — Dashboard-widget med mest sökta/lästa dokument
- [ ] **Webhook-notifieringar** — Notifiera vid dokumentuppdatering
