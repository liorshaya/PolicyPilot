# PolicyPilot: תדריך לסוכן ליום הראשון

2026-09-19 · Lior Shaya

> **עדכון 18.9:** הראיון ב-5.10, ולכן התוכנית רצה ב"גרסת השבועיים" של מסמך תוכנית העבודה. יום 1 כבר בוצע, וגם פרומפט 2. את פרומפט 3 לא מדביקים. את מספר היום ואת התאריך לפרומפטים 4 ו-5 לוקחים מהטבלה בסעיף 6 ב-RUNBOOK או ברשימת ההתקדמות. התאריכים וההיקף שבהמשך המסמך הזה הם של התוכנית המקורית.

מחר בבוקר הסוכן מתחיל מריפו שכבר מכיל את `docs/` ו-`fixtures/`. המסמך הזה אומר לו מה לקרוא ובאיזה סדר, מה מותר ומה אסור, ומה בדיוק לבנות ביום 1, עם פרומפטים מוכנים להדבקה. המסמך בעברית, הפרומפטים באנגלית: המסמכים, הקוד ו-`CLAUDE.md` באנגלית, אז הסוכן מקבל שפה אחת לכל מה שטכני.

## הפרויקט בחמש שורות

PolicyPilot הוא קופיילוט AI מעל מנוע חוקים דטרמיניסטי, שנבנה לראיון טכני ב-ESI Labs (המוצר שלהם, LOGIST, הוא BRMS). המודל כותב חוקים מטקסט מדיניות, סוקר אותם מול הטקסט, עונה על שאלות עם ציטוטים ומציע שינויים; מנוע Java דטרמיניסטי מקבל כל החלטה ומתעד trace מלא; אדם מאשר כל שינוי מדיניות. **אף החלטה לא עוברת דרך מודל.** זה המשפט הראשון של הפיץ' וגם הכלל הארכיטקטוני הראשון.

- **הדמו** (3 דקות, 4 צעדים): הדבקת מדיניות הלוואות בעברית, ואז טבלת החלטה של כ-15 חוקים עם פסקת מקור לכל חוק ושתי אזהרות מהסוקר; הרצת 200 מקרים בפחות משנייה ופתיחת מקרה 17; שאלות בצ'אט עם ציטוטים, סימולציית "עם ערב" ותשובת "לא מכוסה במסמכים"; בקשת שינוי "העלה את ההכנסה המינימלית ל-9,000" שמייצרת diff, 12 היפוכי החלטה, אישור, גרסה 2 ורשומת audit.
- **סטאק**: Java 21 + Spring Boot 4 + Spring AI 2.x, PostgreSQL + pgvector, React 19 + TypeScript (Vite), OpenAI כברירת מחדל ו-Ollama מקומי מאחורי אותו ממשק; פריסה ב-Railway (API ומסד נתונים) וב-Vercel (web).
- **שפות**: הקוד, ה-DSL, ה-API וכל המסמכים באנגלית; טקסט המדיניות, השאלות והפלט החופשי של המודל בעברית, עם fixture אנגלי כגיבוי.
- **לוח זמנים**: 19 ימי עבודה, מיום שלישי 22.9 עד יום ראשון 18.10, בחמישה שלבים (0 עד 4) שכל אחד נסגר בשער (G0 עד G4); 40% מכל משימה הם בדיקות; ימים 17 עד 19 הם חזרות ותיקונים בלבד.

## מה כבר קיים ומה הסוכן קורא קודם

הריפו מתחיל עם שתי תיקיות שכבר מוכנות ומאומתות, מתוך שני קבצי zip שנשלחו בשיחה: `policypilot-docs.zip` (תיקיית `docs/`) ו-`policypilot-fixtures.zip` (תיקיית `fixtures/`). שתיהן נכנסות לשורש הריפו כמו שהן, לפני שהסוכן כותב שורת קוד אחת.

| מה | איפה | מה יש שם |
| --- | --- | --- |
| המסמכים | `docs/` | `README.md` (אינדקס וסדר קריאה), `01-project-brief.md` עד `07-work-plan.md`, `progress-checklist.md` (תיבה לכל משימה, מסומנת רק אחרי שהבדיקות ירוקות) |
| ה-fixtures | `fixtures/` | ה-JSON Schema של ה-DSL, מדיניות ההלוואות עם סט החוקים המפורסם, 200 המקרים והתוצאות הצפויות, בקשת השינוי המתוסרטת, סווייט התאימות C-01 עד C-31, 32 קבצי `invalid-*.json`, סט ההערכה (18 מדיניות, 30 שאלות, 6 בקשות שינוי), מימוש הרפרנס בפייתון והגנרטור |
| הוכחה שהכל תקין | `python3 fixtures/reference/reference_check.py` | מסתיים ב-`ALL OK` (דורש רק `pip install jsonschema`); `python3 fixtures/tools/generate_cases.py` מייצר מחדש את המקרים בלי שינוי (דטרמיניסטי) |

**סדר הקריאה ליום 1.** הסוכן לא צריך את כל 380 הקילו-בייט ביום הראשון; הוא צריך את הבריף בשלמותו ואת הסעיפים שיום 1 ממש נוגע בהם. את השאר הוא קורא ביום שבו המשימה מצביעה עליו.

1. `docs/README.md`: מה יש ומתי קוראים מה.
2. `docs/01-project-brief.md` בשלמותו: העיקרון, הדמו, 23 הדרישות הפונקציונליות, השלבים, הפריסה, ה-Definition of Done והמילון.
3. `docs/02-architecture.md`: הסעיפים Backend Module Structure (אחת עשרה החבילות וכיוון התלויות), Deployment Topology (כולל מבנה הריפו) ו-Configuration and Model Providers. את שאר המסמך (API, מודל נתונים) הוא קורא מיום 4.
4. `docs/06-test-strategy.md`: Definition of Done, Coverage Targets, CI Pipeline (שמונת השלבים עם התקציבים) ו-Working Method.
5. `docs/07-work-plan.md`: Calendar, Phase gates, Phase 0 (טבלת יום 1) ו-Daily routine and tracking.
6. `docs/05-security-specification.md`: רק Supply Chain and Build Security ו-Data Protection (סודות, gitleaks, סריקת אימג'). השאר ביום 4, כשבונים את הדלת הקדמית.
7. `fixtures/README.md`: מבנה הקבצים שהלודרים ב-Java יצטרכו לקרוא מיום 2.

את `03-rules-dsl-specification.md` ו-`04-ai-pipeline-and-prompts.md` הסוכן פותח רק בימים 2 ו-7 בהתאמה; ביום 1 אין בהם שורת קוד אחת לכתוב.

## כללי עבודה לסוכן

עשרת הכללים האלה נכנסים ל-`CLAUDE.md` בשורש הריפו (באנגלית, הסוכן כותב אותו בפרומפט 2), כך שכל סשן חדש מקבל אותם אוטומטית. כל אחד מהם לקוח מהמסמכים, לא המצאה של התדריך הזה.

1. **המודל מציע ומסביר, המנוע מחליט, אדם מאשר.** שום קריאה למודל במסלול של החלטה; `engine` ו-`rules` לא מייבאים כלום מ-`ai`, מ-`rag` או מ-Spring AI, ורק `ai.adapter` מייבא `org.springframework.ai`. בדיקות ArchUnit אוכפות את זה מיום 1.
2. **המסמכים קובעים, הקוד עוקב.** שמות חבילות, נתיבי API, קודי ולידציה, ספים, מזהי חוקים, שמות משתני סביבה ומספרי ימים לקוחים מהמסמכים כמו שהם. הסוכן לא ממציא שם או ערך; אם משהו חסר או סותר, הוא עוצר ושואל, והתיקון נכנס קודם למסמך החי ואז מיוצא מחדש ל-`docs/`.
3. **רשימת הבדיקות לפני הקוד.** כל משימה מתחילה מהפיכת שורות המפרט (טבלת היום בתוכנית העבודה, ה-API, בקרות האבטחה) לבדיקות נקובות בשם, ריקות ונכשלות; אז אדום, ירוק, ריפקטור. כ-40% מזמן המשימה הם בדיקות ו-fixtures.
4. **ערכים צפויים מגיעים מבחוץ לקוד.** מהרפרנס בפייתון ומה-fixtures למנוע ולולידטור, ממסמך ה-OpenAPI ל-API, מההקלטות ומסט ההערכה ל-AI. בדיקה שמעתיקה את הערך הצפוי מפלט הקוד נפסלת.
5. **כל בדיקה שהסוכן מייצר עוברת שלוש שאלות** לפני שהיא נשארת: היא בודקת ערך ספציפי מהמפרט, היא הייתה נכשלת אם ההתנהגות הייתה שגויה, והיא לא עושה mock לדבר שהיא בודקת.
6. **שפות.** קוד, הערות, בדיקות, הודעות commit, `CLAUDE.md` וה-worklog באנגלית; רק טקסט המדיניות, השאלות לדמו והפלט החופשי של המודל בעברית.
7. **ה-fixtures הם אמת נתונה.** הסוכן קורא מ-`fixtures/` ולא משנה בה קובץ אלא אם המפרט שונה, ואז התיקון נוחת בקוד, ברפרנס וב-fixture יחד. `reference_check.py` חייב להסתיים ב-`ALL OK` אחרי כל שינוי שם.
8. **סודות ורשת.** אף מפתח בריפו (רק `.env` מקומי שב-`.gitignore`, ו-`.env.example` עם שמות בלבד); אף בדיקה לא תלויה ברשת, בשעון, ב-locale או בסדר הרצה; קריאות מודל בבדיקות עוברות דרך הקלטות (`RecordedGateway`), לא דרך API חי.
9. **מרג'ים רק ירוק.** PR נכנס ל-`main` עם שלבי CI 1 עד 6 ירוקים (7 כשנגעו ב-`frontend/` או בחוזה של ה-API); `main` ירוק נפרס לבד ל-Railway ול-Vercel. pipeline אדום מתוקן לפני שכותבים בדיקה חדשה.
10. **היום נגמר ב-worklog.** שורה אחת ליום ב-`docs/worklog.md`: תאריך, מספר יום, מה נעשה, מה חלק, החלטות, והמספרים מסיכום ה-CI (כיסוי לכל חבילה, ציון PIT, זמן batch, first token). מה שחלק נפתח ראשון מחר בבוקר; אין השלמות בערב.

## יום 0 ויום 1: המשימות ושער G0

יום 0 הוא שעה ידנית שלך, בלי סוכן, כי הכל בו הוא חשבונות ומפתחות; יום 1 (שלישי 22.9) מסתיים במערכת ריקה אבל מחווטת עד הסוף: אף קוד דומיין, אף endpoint מעבר ל-health check, אף פרומפט, וכל pipeline שהימים הבאים נשענים עליו כבר ירוק. שום דבר מיום 1 לא גולש מעבר לבוקר של יום 2.

**יום 0 (ידני, ראשון 20.9 או מחר בבוקר, כשעה)**

- [ ] ריפו GitHub עם `main` מוגן (שלבי CI 1 עד 6 נדרשים ברגע שה-CI קיים); `docs/` ו-`fixtures/` נכנסות ב-commit הראשון
- [ ] חשבון ופרויקט ב-Railway עם מסד הנתונים מתבנית pgvector
- [ ] חשבון ופרויקט ב-Vercel; DNS ל-`policypilot.liorshaya.com`
- [ ] מפתח OpenAI עם תקרת הוצאה חודשית בגובה תקציב הדמו
- [ ] `qwen3:14b` ו-`bge-m3` מורדים ל-Ollama המקומי
- [ ] תאריך הראיון ידוע; אם הוא לפני יום 20, הלוח נמפה מחדש לפי ה-Scope ladder

**יום 1, שלב 0 (עם הסוכן)**. חמש משימות, בסדר הזה, כל אחת עם הבדיקות שלה באותו PR. הטבלה המלאה, עם כל שם וערך, היא "Phase 0, Foundations" בתוכנית העבודה; הסוכן עובד ממנה, לא מהתקציר כאן.

| # | משימה | מה נבנה | מה מוכיח שהיא גמורה |
| --- | --- | --- | --- |
| 1 | ריפו ושלדים | מבנה המונו-ריפו (`backend/`, `frontend/`, `fixtures/`, `docs/`, `.github/workflows/`); `backend` על Java 21, Spring Boot 4.0.x ו-Spring AI 2.0.x BOM עם Flyway, Actuator ו-springdoc; אחת עשרה החבילות ריקות תחת `com.liorshaya.policypilot`; JaCoCo ו-PIT עם הספים של מסמך 6; `frontend` על React 19, TypeScript strict, Vite, TanStack Query, Vitest, Playwright, MSW, ESLint; `make up` כפקודה האחת אחרי `docker compose up` | ArchUnit: כל חץ בטבלת המודולים, שום דבר לא תלוי ב-`web`, Spring AI רק ב-`ai.adapter`, איסור על `ProcessBuilder`, `Runtime.exec`, `javax.script` ו-`@JsonTypeInfo`; context-loads לכל פרופיל (`openai`, `ollama`, עם `cloud`); Vitest smoke; Playwright שדף השער נטען |
| 2 | Docker Compose | PostgreSQL 16 עם pgvector על volume, Ollama עם סקריפט משיכה ל-`qwen3:14b` ו-`bge-m3`, אימג' ה-API מ-`backend/Dockerfile`, שרת הפיתוח של ה-frontend; Flyway V1 עם `CREATE EXTENSION IF NOT EXISTS vector` | מחלקת Testcontainers בסיסית לבדיקות `*IT` על אימג' pgvector; בדיקת אינטגרציה ש-Flyway רץ וההרחבה קיימת; הרצת compose מתוזמנת מול סף 5 הדקות |
| 3 | CI | שמונת השלבים עם התקציבים (סך 15 דקות): 1 היגיינה (gitleaks, פרטיות fixtures, עותקי סכמות, קליינט מחולל), 2 בדיקות מהירות (JaCoCo, Vitest, הרפרנס בפייתון), 3 PIT, 4 סטטי ושרשרת אספקה (Semgrep, ESLint, Dependency-Check, `npm audit`, ArchUnit), 5 אינטגרציה (Testcontainers), 6 בניית אימג' עם Trivy ו-digest, 7 Playwright ב-PR, 8 דוחות ו-SBOM בתגים; `@Tag("quarantine")` מחווט; הגנת branch על 1 עד 6 | ה-workflow ירוק על השלד; מפתח מזויף שנשתל ב-branch זמני מפיל את שלב 1 (ואז נמחק) |
| 4 | Fixtures | העץ המסופק נכנס כמו שהוא | ה-self-test של הרפרנס ושתי הטענות של הגנרטור רצים בשלב 2; CI מריץ את הגנרטור מחדש ועושה diff מול הקבצים המחויבים |
| 5 | פריסה ראשונה | Railway: שירות API מ-`backend/` עם מסד pgvector, משתני `OPENAI_API_KEY`, `POLICYPILOT_ACCESS_CODE`, `POLICYPILOT_COOKIE_SECRET`, `POLICYPILOT_ADMIN_CODE`, `SPRING_PROFILES_ACTIVE=openai,cloud`, `DATABASE_URL`, health check על `/actuator/health`, פריסה רק מ-`main` ירוק; Vercel: פרויקט מ-`frontend/` עם `VITE_API_BASE_URL` והדומיין; שער הגישה כדף סטטי (החלפת הקוד עצמה מגיעה ביום 4) | health check נענה מבחוץ; דף השער נפתח מהטלפון; הפריסה משתמשת ב-digest ששלב 6 ייצר |

**שער G0**, נבדק בשעה האחרונה של היום, על האתר החי ולא על הלפטופ: `docker compose up` ועוד פקודה אחת מרימות את המערכת על מכונה נקייה בפחות מ-5 דקות; שלבי CI 1, 2, 4 ו-6 ירוקים; `/actuator/health` עונה ב-Railway; דף השער מוצג ב-Vercel; `fixtures/` מחויב עם ה-self-test של הרפרנס ירוק בשלב 2. ההוכחה (קישור להרצת CI, ה-`curl` של ה-health check, צילום דף השער מהטלפון) נכנסת ל-`docs/worklog.md`. שער שנכשל מקבל את הבוקר של מחרת, לכל היותר חצי יום, ולא יותר.

## פרומפטים מוכנים להדבקה

חמישה פרומפטים, בסדר השימוש: היכרות (קריאה בלבד), כתיבת `CLAUDE.md`, פתיחת יום 1, תבנית פתיחת יום לכל יום אחר, וסגירת יום. כולם באנגלית, כי המסמכים והקוד באנגלית; את ההמשך אתה יכול לכתוב בעברית, הסוכן יבין. מחליפים `{N}`, `{date}` ו-`{G}` לפני ההדבקה.

**פרומפט 1, היכרות.** מדביקים ראשון, בסשן חדש, אחרי ש-`docs/` ו-`fixtures/` בריפו. הסוכן קורא, מריץ את הרפרנס, ומחזיר סיכום; אם הסיכום שגוי, מתקנים כאן לפני שכותבים שורת קוד.

```text
You are joining PolicyPilot, an AI copilot over a deterministic rules engine, built for a technical interview. The repository already contains docs/ (Documents 1 to 7 plus a progress checklist) and fixtures/ (the Rules DSL schema, the demo policy, the conformance suite, the evaluation set, the Python reference implementation).

Read, in this order, and do not write any code or file yet:
1. docs/README.md
2. docs/01-project-brief.md, all of it
3. docs/02-architecture.md: the sections "Backend Module Structure", "Deployment Topology" and "Configuration and Model Providers"
4. docs/06-test-strategy.md: "Definition of Done", "Coverage Targets and Enforcement", "CI Pipeline" and "Working Method"
5. docs/07-work-plan.md: "Calendar and capacity", "Phase gates", "Phase 0, Foundations (day 1)" and "Daily routine and tracking"
6. fixtures/README.md

Then run `python3 fixtures/reference/reference_check.py` (pip install jsonschema if it is missing) and confirm it ends with ALL OK.

Reply with the following, in this order and nothing else:
- The design principle in one sentence, in your own words.
- The eleven backend packages and the direction in which dependencies are allowed.
- The five tasks of day 1, each with the tests it ships with.
- The five conditions of gate G0.
- Every number, name or path you would need for day 1 that you could NOT find in the documents. List them; do not guess.

Rules from here on: the documents are the source of truth. Never invent a route, a package name, a threshold, a version or a day number. When the documents are silent or contradict each other, stop and ask. Code, comments, tests, commit messages and documentation are in English; only policy text, demo questions and the model's free-text output are in Hebrew.
```

**פרומפט 2, `CLAUDE.md`.** מיד אחרי פרומפט 1, באותו סשן. הקובץ הזה הוא מה שכל סשן עתידי מקבל אוטומטית (ב-Cursor: אותו תוכן בקובץ הכללים של הפרויקט).

```text
Write CLAUDE.md at the repository root, in English, under 150 lines, so that every future session starts with the same context. Take every fact from docs/; add nothing the documents do not say. Sections:

1. What PolicyPilot is (three sentences) and the design principle: the model proposes and explains, the rules engine decides, a person approves every policy change.
2. Repository layout (backend/, frontend/, fixtures/, docs/, docker-compose.yml, .github/workflows/), one line each, from docs/02-architecture.md "Deployment Topology".
3. The eleven backend packages under com.liorshaya.policypilot and the dependency rule: inward toward rules and engine, only ai.adapter imports Spring AI, nothing depends on web (from "Backend Module Structure").
4. Commands: docker compose up; make up; ./mvnw verify; npm test; python3 fixtures/reference/reference_check.py (must end with ALL OK); python3 fixtures/tools/generate_cases.py (must produce no diff).
5. Working rules, taken from docs/06-test-strategy.md "Working Method" and "Definition of Done": tests are listed before the code; expected values come from the reference implementation, the fixtures, the OpenAPI document, the recordings or the labeled set, never from running the code; no test depends on the network, the clock, the locale or test order; model calls in tests go through recordings; a pull request merges only with CI stages 1 to 6 green.
6. What never changes without changing the document first: package names, routes, validator codes, thresholds, rule ids, fixture paths, environment variable names, day numbers. When a document is silent or two documents disagree, stop and ask instead of guessing.
7. Languages: everything in English except policy text, demo questions and the model's free-text output, which are in Hebrew.
8. The daily routine in five lines, from docs/07-work-plan.md "Daily routine and tracking", including the format of a docs/worklog.md line.
9. Where to look: a table mapping each kind of question (DSL semantics, prompt contracts, security controls, test levels, what to build today) to the document and section that answers it.

Show me the file before committing it.
```

**פרומפט 3, פתיחת יום 1.** מחר, אחרי שני הראשונים. הסוכן מתחיל מרשימת הבדיקות של חמש המשימות ועוצר לאישור אחרי כל משימה.

```text
Today is day 1 of the PolicyPilot work plan (Tuesday, September 22, 2026). Work through the five tasks of "Phase 0, Foundations (day 1)" in docs/07-work-plan.md, in order: 1 Repository and skeletons, 2 Docker Compose, 3 CI pipeline, 4 Fixtures, 5 First deployment. One branch and one pull request per task; each pull request contains the task's code and every test the table lists for it, and CI must be green before the next task starts.

Rules for today:
- api: Java 21, Spring Boot 4.0.x, the Spring AI 2.0.x BOM, Flyway, Actuator, springdoc; the eleven packages of docs/02-architecture.md created empty, each with a package-info.java that states its allowed dependencies; JaCoCo and PIT configured with the thresholds of docs/06-test-strategy.md "Coverage Targets and Enforcement".
- web: React 19, TypeScript strict, Vite, TanStack Query, Vitest with coverage thresholds, Playwright, MSW, ESLint; a static access gate page and nothing else.
- Check Maven Central and npm for the versions that exist today before pinning, and pin every version.
- fixtures/ is committed exactly as delivered; do not modify any file inside it. CI stage 2 runs python3 fixtures/reference/reference_check.py and python3 fixtures/tools/generate_cases.py and fails on any diff.
- No secrets anywhere in the repository; .env.example lists variable names only.
- Follow CLAUDE.md. When a value you need is not in the documents, stop and ask.

Before task 1, show me the test list for all five tasks: empty, failing tests, each with the specification row it comes from. After each task, stop and show me what changed, the test results, and what is still missing from the task's row.
```

**פרומפט 4, פתיחת יום (תבנית לימים 2 עד 16).** אותו רעיון, מפורמט על מספר היום.

```text
Today is day {N} of the PolicyPilot work plan ({date}). Before writing any code:

1. Read docs/worklog.md; anything marked slipped yesterday comes first.
2. Read the day {N} row block in docs/07-work-plan.md and the day {N} block in docs/progress-checklist.md, then the sections of docs/03, docs/04 and docs/05 that the table names.
3. For each task row, list the tests it ships with as test names (class and method, or Vitest describe and it), each with the specification row it comes from and the source of its expected value (reference implementation, fixture, OpenAPI document, recording, labeled set). Create them as empty, failing tests.
4. Show me the test list and a short plan: one pull request per task row, in the order of the table, each with its tests and fixtures, CLAUDE.md applied throughout.

Stop there and wait for my go. Do not implement anything before I confirm the list. Anything not on the list is tomorrow's.
```

**פרומפט 5, סגירת יום.** בשעה האחרונה, כל יום, גם ביום 1 (עם `{G}` = G0).

```text
Close day {N}. In this order:
1. Walk the Definition of Done in docs/06-test-strategy.md for every task of today; for each line say pass, fail or not applicable, with the evidence (test names, CI job link, command output).
2. If today ends a phase, check gate {G} in docs/07-work-plan.md "Phase gates" and collect the proof it asks for.
3. Append today's line to docs/worklog.md: date, day number, done, slipped, decisions taken, and the numbers from the CI job summary (line coverage per package, PIT score, batch time, first-token time; write n/a for numbers that do not exist yet).
4. Tick the finished items of day {N} in docs/progress-checklist.md; leave unfinished items unticked and list them as slipped in the worklog.
5. Tell me what should come first tomorrow morning.

Do not tick anything whose tests are not green. Do not start tomorrow's work.
```

## מה לא לעשות, ומה מגיע בימים 2 ו-3

ששת הדברים שסוכן נוטה לעשות מעצמו ביום הראשון, וכולם אסורים לפי המסמכים:

- להתחיל קוד דומיין (מודל ה-DSL, מנוע, endpoint) לפני ש-G0 עבר. יום 1 הוא שלד בלבד.
- לשנות קובץ ב-`fixtures/` "כדי שהבדיקה תעבור". אם הרפרנס וה-Java לא מסכימים, המפרט מכריע מי טועה.
- לבחור גרסאות מהזיכרון. Spring Boot 4 ו-Spring AI 2 חדשים; הסוכן בודק ב-Maven Central וב-npm מה קיים היום ומקבע את הגרסה בקובץ הבנייה.
- לרכך סף כיסוי או PIT כדי שה-CI יהיה ירוק. הספים במסמך 6 הם שער; CI אדום מתוקן בקוד או בבדיקה.
- להכניס מפתח לקובץ הגדרות "רק בשביל הבדיקה". gitleaks בשלב 1 מפיל את ה-build, וזו בדיוק הבדיקה של משימה 3.
- לעבוד ישר על `main`. כל משימה היא branch ו-PR, ו-`main` נפרס לבד לאתר החי.

**יום 2 (רביעי 23.9), `rules`**: מודל ה-DSL 1.0 כ-records עם Jackson קפדני וולידציית JSON Schema; הולידטור עם ארבעת הקונטקסטים וכל 32 קודי הבדיקה הסטטית; מנרמל הציטוטים; ולידטור המקרים. הבדיקות: כל `invalid-*.json` כבדיקה פרמטרית שנכשלת עם אותו קוד כמו בפייתון, נרמול עם עברית, ניקוד, U+202E ותווי zero-width, חיובי ושלילי לכל בדיקה סטטית; `rules` ב-100% שורות וסט ההלוואות עובר נקי ב-PUBLISH. מסמך 3 נקרא ביום הזה בשלמותו, והוא הארוך מכולם.

**יום 3 (חמישי 24.9), `engine`**: סט חוקים מקומפל, ביטויים ב-`BigDecimal`, אופרטורים עם RE2J ל-`matches`, סדר עדיפות, עצירה טרמינלית, שדות נגזרים, שגיאות הרצה עם trace חלקי, סימולציה. הבדיקות: C-01 עד C-31 פרמטריות, שוויון מול `sample-decision.json` ו-`cases-expected.json`, מאפייני jqwik, 200 המקרים פעמיים זהים בייט לבייט, אף `Clock` ב-`engine`, מיקרו-בנצ'מרק; PIT על `engine` ו-`rules` לפחות 90%, שער קשיח מהיום הזה והלאה.

בשני הימים האלה פרומפט 4 עושה את העבודה: הסוכן קורא את שורת היום בתוכנית, את הבלוק ב-checklist ואת מסמך 3, ומחזיר רשימת בדיקות לפני שורת קוד אחת. הרפרנס בפייתון הוא הדעה השנייה לכל מחלוקת: כשה-Java מחזיר משהו אחר על אותו קובץ, קוראים את המפרט לפני שמתקנים משהו.
